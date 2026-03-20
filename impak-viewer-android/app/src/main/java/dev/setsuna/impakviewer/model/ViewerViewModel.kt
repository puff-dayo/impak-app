package dev.setsuna.impakviewer.model

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Debug
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.setsuna.impakviewer.decoder.FrameIndexEntry
import dev.setsuna.impakviewer.decoder.ImpakReader
import dev.setsuna.impakviewer.decoder.PatchRect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.graphics.scale

data class FrameThumb(
    val index: Int,
    val thumb: Bitmap,
    val name: String,
    val isKeyframe: Boolean,
)

data class FileInfo(
    val fileName: String,
    val width: Int,
    val height: Int,
    val totalFrames: Int,
    val baselineCount: Int,
    val modeName: String,
    val codec: String,
    val fileSizeBytes: Long,
)

data class FrameInfo(
    val index: Int,
    val name: String,
    val isKeyframe: Boolean,
    val refId: String,
    val patchCount: Int,
    val patches: List<PatchRect>,
    val entry: FrameIndexEntry,
    val baselineCount: Int,
)

sealed class ViewerState {
    data object Welcome : ViewerState()
    data object Loading : ViewerState()
    data class Error(val message: String) : ViewerState()
    data class Ready(val bitmap: Bitmap) : ViewerState()
}

class ViewerViewModel : ViewModel() {

    var frames = mutableStateListOf<Bitmap>()
        private set

    private val _thumbnails = MutableStateFlow<List<FrameThumb>>(emptyList())
    val thumbnails: StateFlow<List<FrameThumb>> = _thumbnails

    var viewerState by mutableStateOf<ViewerState>(ViewerState.Welcome)
        private set

    var fileInfo by mutableStateOf<FileInfo?>(null)
        private set

    var frameInfo by mutableStateOf<FrameInfo?>(null)
        private set

    var currentIndex by mutableIntStateOf(0)
        private set

    var frameCount by mutableIntStateOf(0)
        private set

    var statusText by mutableStateOf("")
        private set

    var statusError by mutableStateOf(false)
        private set

    fun getMemoryStatus(): String {
        val usedMb = Debug.getNativeHeapAllocatedSize() / 1_048_576L
        return "Mem used: ${usedMb}MB"
    }

    var isDark by mutableStateOf(true)

    private var _reader: ImpakReader? = null
    private var _fileName: String = ""
    private var _fileSize: Long = 0L

    fun openFile(uri: Uri, context: Context) {
        viewModelScope.launch {
            viewerState = ViewerState.Loading
            _thumbnails.value = emptyList()
            _reader?.close()
            _reader = null

            try {
                val (reader, fileName, fileSize) = withContext(Dispatchers.IO) {
                    val cr = context.contentResolver
                    val name = cr.query(uri, null, null, null, null)?.use { cursor ->
                        val col = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                        cursor.moveToFirst()
                        if (col >= 0) cursor.getString(col) else "unknown.impak"
                    } ?: uri.lastPathSegment ?: "unknown.impak"
                    val size = cr.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L

                    val tempFile = java.io.File(context.cacheDir, "current.impak")
                    cr.openInputStream(uri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    } ?: error("Cannot open stream")

                    Triple(ImpakReader(tempFile), name, size)
                }
                _reader = reader
                _fileName = fileName
                _fileSize = fileSize
                frameCount = reader.frameCount

                fileInfo = FileInfo(
                    fileName = fileName,
                    width = reader.canvasWidth,
                    height = reader.canvasHeight,
                    totalFrames = reader.frameCount,
                    baselineCount = reader.baselineCount,
                    modeName = reader.modeName,
                    codec = reader.codec,
                    fileSizeBytes = fileSize,
                )
                setStatus("$fileName  ·  ${reader.frameCount} frames")

                goTo(0)
                loadThumbnails(reader)

            } catch (e: Exception) {
                viewerState = ViewerState.Error(e.message ?: "Unknown error")
                setStatus("Failed to open file", error = true)
            }
        }
    }

    // Navigation

    private var _goToJob: kotlinx.coroutines.Job? = null
    fun goTo(index: Int) {
        val reader = _reader ?: return
        if (index !in 0..<frameCount) return
        currentIndex = index
        _goToJob?.cancel()
        _goToJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = reader.getFrame(index)
                val fi = buildFrameInfo(reader, index)
                withContext(Dispatchers.Main) {
                    viewerState = ViewerState.Ready(bmp)
                    frameInfo = fi
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) return@launch
                withContext(Dispatchers.Main) {
                    setStatus("Error loading frame $index: ${e.message}", error = true)
                }
            }
        }
    }

    fun prevFrame() {
        goTo(currentIndex - 1)
    }

    fun nextFrame() {
        goTo(currentIndex + 1)
    }

    // Thumbnail loading
    private fun loadThumbnails(reader: ImpakReader) {
        viewModelScope.launch(Dispatchers.IO) {
            val list = mutableListOf<FrameThumb>()
            for (i in 0 until reader.frameCount) {
                try {
                    val bmp = reader.getFrame(i)
                    val thumbW = 140
                    val thumbH = (bmp.height * 140f / bmp.width).toInt().coerceAtLeast(1)
                    val thumb = bmp.scale(thumbW, thumbH)
                    bmp.recycle()
                    val name = reader.getName(i)
                    val isKey = reader.isKeyframe(i)
                    list.add(FrameThumb(i, thumb, name, isKey))
                    _thumbnails.value = list.toList()
                } catch (_: Exception) { /* skip bad frame */
                }
            }
        }
    }

    // Export
    fun exportCurrentFrame(context: Context, uri: Uri) {
        val reader = _reader ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bmp = reader.getFrame(currentIndex)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
                }
                withContext(Dispatchers.Main) { setStatus("Frame exported.") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    setStatus(
                        "Export failed: ${e.message}", error = true
                    )
                }
            }
        }
    }

    // Helpers

    private fun buildFrameInfo(reader: ImpakReader, index: Int): FrameInfo {
        val entry = reader.getIndexEntry(index)
        val meta = reader.getMetadata(index)
        val name = meta["name"] as? String ?: "frame_${index.toString().padStart(4, '0')}"
        val isKey = entry.frameType == 0
        val bc = reader.baselineCount
        val refText = when {
            isKey -> "self"
            entry.refFrameId < bc -> "B${entry.refFrameId}"
            else -> "${entry.refFrameId - bc}"
        }
        val patches = reader.diffMap(index)
        return FrameInfo(index, name, isKey, refText, entry.patchCount, patches, entry, bc)
    }

    fun setStatus(msg: String, error: Boolean = false) {
        statusText = msg
        statusError = error
    }
}
