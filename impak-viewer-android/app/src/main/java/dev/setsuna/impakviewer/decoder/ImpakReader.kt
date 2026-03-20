package dev.setsuna.impakviewer.decoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream

class ImpakReader(_file: java.io.File) : AutoCloseable {
    private val _raf = java.io.RandomAccessFile(_file, "r")

    val header: FileHeader
    private val _index: List<FrameIndexEntry>
    private val _baselineCount: Int

    private val _decodeCache = LinkedHashMap<Int, Bitmap>(16, 0.75f, true)

    companion object {
        private const val MAX_CACHE_FRAMES = 10
    }

    private fun cachePut(absId: Int, bmp: Bitmap) {
        _decodeCache[absId] = bmp
        while (_decodeCache.size > MAX_CACHE_FRAMES) {
            val eldest = _decodeCache.entries.first()
            _decodeCache.remove(eldest.key)
            if (!eldest.value.isRecycled) eldest.value.recycle()
        }
    }

    private val _metaCache = mutableMapOf<Int, Map<String, Any>>()

    init {
        header = readFileHeader()
        _index = readIndex()
        _baselineCount = detectBaselineCount()
    }

    val frameCount: Int get() = header.frameCount - _baselineCount

    val baselineCount: Int get() = _baselineCount

    val canvasWidth: Int get() = header.width
    val canvasHeight: Int get() = header.height

    val codec: String get() = CODEC_NAMES[header.codec] ?: "png"

    val modeName: String get() = MODE_NAMES[header.diffMode] ?: "unknown"

    fun getFrame(index: Int): Bitmap {
        require(index in 0 until frameCount) { "Frame index $index out of range" }
        return decodeFrame(contentToAbs(index))
    }

    fun getMetadata(index: Int): Map<String, Any> = readMetadata(contentToAbs(index))

    fun getName(index: Int): String = getMetadata(index)["name"] as? String ?: "#$index"

    fun isKeyframe(index: Int): Boolean = _index[contentToAbs(index)].frameType == FRAME_KEYFRAME

    fun diffMap(index: Int): List<PatchRect> {
        val absId = contentToAbs(index)
        val entry = _index[absId]
        if (entry.frameType == FRAME_KEYFRAME) return listOf(
            PatchRect(
                0,
                0,
                header.width,
                header.height
            )
        )
        return readPatchHeaders(absId)
    }

    fun getIndexEntry(index: Int): FrameIndexEntry = _index[contentToAbs(index)]

    override fun close() {
        _decodeCache.values.forEach { it.recycle() }
        _decodeCache.clear()
        _raf.close()
    }

    private fun decodeFrame(absId: Int): Bitmap {
        _decodeCache[absId]?.let { cached ->
            if (!cached.isRecycled) return cached.copy(Bitmap.Config.ARGB_8888, false)
        }

        val entry = _index[absId]
        val bmp: Bitmap

        if (entry.frameType == FRAME_KEYFRAME) {
            bmp = decodePatches(absId)
        } else {
            val refBmp = decodeFrame(entry.refFrameId)
            val patches = readPatches(absId)
            bmp = reconstruct(refBmp, patches)
            refBmp.recycle()
        }

        val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp
        else bmp.copy(Bitmap.Config.ARGB_8888, false).also { bmp.recycle() }

        cachePut(absId, argb)
        return argb.copy(Bitmap.Config.ARGB_8888, false)
    }

    private fun decodePatches(absId: Int): Bitmap {
        val entry = _index[absId]
        val headerBuf = bufAt(entry.dataOffset.toInt(), PATCH_HEADER_SIZE)
        val x = headerBuf.short.toInt() and 0xFFFF
        val y = headerBuf.short.toInt() and 0xFFFF
        headerBuf.position(headerBuf.position() + 4)
        val dataLen = headerBuf.int
        val dataOffset = entry.dataOffset.toInt() + PATCH_HEADER_SIZE
        val dataBuf = bufAt(dataOffset, dataLen)
        val data = ByteArray(dataLen).also { dataBuf.get(it) }
        return decompressPatchToBitmap(data)
    }

    private fun reconstruct(base: Bitmap, patches: List<RawPatch>): Bitmap {
        val result = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)
        for (patch in patches) {
            val patchBmp = decompressPatchToBitmap(patch.data)
            canvas.drawBitmap(patchBmp, patch.x.toFloat(), patch.y.toFloat(), null)
            patchBmp.recycle()
        }
        return result
    }

    private fun decompressPatchToBitmap(data: ByteArray): Bitmap {
        val imageBytes = if (codec == "webp") {
            data
        } else {
            zlibDecompress(data)
        }
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            ?: error("BitmapFactory failed to decode patch (codec=$codec, ${imageBytes.size} bytes)")
    }

    private fun readFileHeader(): FileHeader {
        val buf = bufAt(0, FILE_HEADER_SIZE)
        val magic = ByteArray(6).also { buf.get(it) }
        if (!magic.contentEquals(IMPAK_MAGIC)) error("Not a valid .impak file (bad magic)")
        val version = buf.get().toInt() and 0xFF
        val diffMode = buf.get().toInt() and 0xFF
        val frameCount = buf.int
        val indexOffset = buf.long
        val width = buf.int
        val height = buf.int
        val codec = buf.get().toInt() and 0xFF
        val quality = buf.get().toInt() and 0xFF
        return FileHeader(
            magic, version, diffMode, frameCount, indexOffset, width, height, codec, quality
        )
    }

    private fun readPatches(absId: Int): List<RawPatch> {
        val entry = _index[absId]
        val buf = bufForFrame(absId)
        val fileSize = _raf.length()
        return List(entry.patchCount) {
            val x = buf.short.toInt() and 0xFFFF
            val y = buf.short.toInt() and 0xFFFF
            val w = buf.short.toInt() and 0xFFFF
            val h = buf.short.toInt() and 0xFFFF
            val dataLen = buf.int
            buf.position(buf.position() + 2)
            val remaining = fileSize - buf.position()
            require(dataLen in 1..remaining) {
                "Corrupt patch at abs=$absId: dataLen=$dataLen, remaining=$remaining"
            }
            val data = ByteArray(dataLen).also { buf.get(it) }
            RawPatch(x, y, w, h, data)
        }
    }

    private fun readPatchHeaders(absId: Int): List<PatchRect> {
        val entry = _index[absId]
        val buf = bufForFrame(absId)
        val fileSize = _raf.length()
        return List(entry.patchCount) {
            val x = buf.short.toInt() and 0xFFFF
            val y = buf.short.toInt() and 0xFFFF
            val w = buf.short.toInt() and 0xFFFF
            val h = buf.short.toInt() and 0xFFFF
            val dataLen = buf.int
            buf.position(buf.position() + 2)
            val remaining = fileSize - buf.position()
            require(dataLen in 1..remaining) {
                "Corrupt patch header at abs=$absId: dataLen=$dataLen"
            }
            buf.position(buf.position() + dataLen)
            PatchRect(x, y, w, h)
        }
    }

    private fun readMetadata(absId: Int): Map<String, Any> {
        _metaCache[absId]?.let { return it }
        val entry = _index[absId]
        if (entry.metadataLen == 0) {
            _metaCache[absId] = emptyMap()
            return emptyMap()
        }
        val buf = bufForFrame(absId)
        repeat(entry.patchCount) {
            buf.position(buf.position() + 8)
            val dataLen = buf.int
            buf.position(buf.position() + 2 + dataLen)
        }
        val raw = ByteArray(entry.metadataLen).also { buf.get(it) }
        val meta = mutableMapOf<String, Any>()
        try {
            val json = JSONObject(raw.decodeToString())
            json.keys().forEach { key -> meta[key] = json.get(key) }
        } catch (_: Exception) {
        }
        _metaCache[absId] = meta
        return meta
    }

    private fun readIndex(): List<FrameIndexEntry> {
        val length = header.frameCount * FRAME_INDEX_ENTRY_SIZE
        val buf = bufAt(header.indexOffset.toInt(), length)
        return List(header.frameCount) {
            val dataOffset = buf.long
            val patchCount = buf.int
            val refFrameId = buf.int
            val metadataLen = buf.int
            val frameType = buf.int
            FrameIndexEntry(dataOffset, patchCount, refFrameId, metadataLen, frameType)
        }
    }

    private fun detectBaselineCount(): Int {
        var count = 0
        for (absId in 0 until header.frameCount) {
            val meta = readMetadata(absId)
            if (meta["_baseline"] == true || meta["_baseline"] == 1) count++
            else break
        }
        return count
    }

    private fun contentToAbs(contentId: Int): Int = contentId + _baselineCount

    private fun zlibDecompress(data: ByteArray): ByteArray =
        InflaterInputStream(data.inputStream()).use { it.readBytes() }

    private fun bufAt(position: Int, length: Int): ByteBuffer {
        val bytes = ByteArray(length)
        _raf.seek(position.toLong())
        _raf.readFully(bytes)
        return ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    }

    private fun bufForFrame(absId: Int): ByteBuffer {
        val entry = _index[absId]
        val start = entry.dataOffset.toInt()
        val end = if (absId + 1 < _index.size) {
            _index[absId + 1].dataOffset.toInt()
        } else {
            _raf.length().toInt()
        }
        return bufAt(start, end - start)
    }
}


data class RawPatch(val x: Int, val y: Int, val w: Int, val h: Int, val data: ByteArray)
data class PatchRect(val x: Int, val y: Int, val w: Int, val h: Int)