package dev.setsuna.impakviewer.decoder

val IMPAK_MAGIC = byteArrayOf(0x49, 0x4D, 0x50, 0x41, 0x4B, 0x00) // "IMPAK\0"

const val FRAME_KEYFRAME = 0
const val FRAME_DELTA = 1

const val CODEC_PNG = 0
const val CODEC_WEBP = 1

val CODEC_NAMES = mapOf(CODEC_PNG to "png", CODEC_WEBP to "webp")

val MODE_NAMES = mapOf(
    0 to "vs_first",
    1 to "vs_prior",
    2 to "keyframe",
    3 to "lto",
    4 to "manual",
)

const val FILE_HEADER_SIZE = 30
const val FRAME_INDEX_ENTRY_SIZE = 24
const val PATCH_HEADER_SIZE = 14

data class FileHeader(
    val magic: ByteArray,
    val version: Int,
    val diffMode: Int,
    val frameCount: Int,
    val indexOffset: Long,
    val width: Int,
    val height: Int,
    val codec: Int,
    val quality: Int,
)

data class FrameIndexEntry(
    val dataOffset: Long,
    val patchCount: Int,
    val refFrameId: Int,
    val metadataLen: Int,
    val frameType: Int,
)

data class PatchHeader(val x: Int, val y: Int, val w: Int, val h: Int, val dataLen: Int)
