package dev.setsuna.impakviewer.decoder

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import org.json.JSONObject
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.InflaterInputStream

class ImpakReader(inputStream: InputStream) : AutoCloseable {
    private val _data: ByteArray = inputStream.use { it.readBytes() }
    private val _buf: ByteBuffer = ByteBuffer.wrap(_data).order(ByteOrder.LITTLE_ENDIAN)

    val header: FileHeader
    private val _index: List<FrameIndexEntry>
    private val _baselineCount: Int

    private val _decodeCache = object : LinkedHashMap<Int, Bitmap>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Bitmap>): Boolean {
            if (size > MAX_CACHE_FRAMES) {
                eldest.value.recycle()
                return true
            }
            return false
        }
    }

    companion object {
        private const val MAX_CACHE_FRAMES = 10
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
        _decodeCache.clear()
    }

    private fun decodeFrame(absId: Int): Bitmap {
        _decodeCache[absId]?.let { return it.copy(it.config ?: Bitmap.Config.ARGB_8888, false) }

        val entry = _index[absId]
        val bmp: Bitmap

        if (entry.frameType == FRAME_KEYFRAME) {
            val patches = readPatches(absId)
            check(patches.size == 1) { "Keyframe must have exactly 1 patch" }
            bmp = decompressPatchToBitmap(patches[0].data)
        } else {
            val refBmp = decodeFrame(entry.refFrameId)
            val patches = readPatches(absId)
            bmp = reconstruct(refBmp, patches)
        }

        val argb = bmp.copy(Bitmap.Config.ARGB_8888, false)
        _decodeCache[absId] = argb
        return argb.copy(Bitmap.Config.ARGB_8888, false)
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
        _buf.position(0)
        val magic = ByteArray(6).also { _buf.get(it) }
        if (!magic.contentEquals(IMPAK_MAGIC)) error("Not a valid .impak file (bad magic)")
        val version = _buf.get().toInt() and 0xFF
        val diffMode = _buf.get().toInt() and 0xFF
        val frameCount = _buf.int
        val indexOffset = _buf.long
        val width = _buf.int
        val height = _buf.int
        val codec = _buf.get().toInt() and 0xFF
        val quality = _buf.get().toInt() and 0xFF
        // position == 30  ✓
        return FileHeader(
            magic, version, diffMode, frameCount, indexOffset, width, height, codec, quality
        )
    }

    private fun readPatches(absId: Int): List<RawPatch> {
        val entry = _index[absId]
        val buf = bufAt(entry.dataOffset.toInt())
        val fileSize = _data.size
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
        val buf = bufAt(entry.dataOffset.toInt())
        val fileSize = _data.size
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
        val buf = bufAt(entry.dataOffset.toInt())
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
        val buf = bufAt(header.indexOffset.toInt())
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

    private fun bufAt(position: Int): ByteBuffer =
        _buf.duplicate().order(ByteOrder.LITTLE_ENDIAN).also { it.position(position) }
}


data class RawPatch(val x: Int, val y: Int, val w: Int, val h: Int, val data: ByteArray)
data class PatchRect(val x: Int, val y: Int, val w: Int, val h: Int)
