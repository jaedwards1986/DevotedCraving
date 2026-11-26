package com.forgeai.chat.nativebridge

import java.io.File
import java.io.RandomAccessFile

/** Reads the structural GGUF metadata needed before native model loading. */
object GgufMetadataReader {
    private const val GGUF_MAGIC = 0x46554747L
    private const val MAX_STRING_BYTES = 1_048_576L
    private const val MAX_ARRAY_ITEMS = 10_000_000L

    data class ModelMetadata(
        val architecture: String,
        val embeddingDimension: Int,
        val declaredContextLength: Int,
        val fileSize: Long
    )

    fun read(file: File): ModelMetadata {
        RandomAccessFile(file, "r").use { raf ->
            require(raf.length() >= 24L) { "GGUF metadata header is truncated" }
            require(raf.readUInt32() == GGUF_MAGIC) { "Invalid GGUF magic" }
            val version = raf.readUInt32()
            require(version in 2L..3L) { "Unsupported GGUF version: $version" }
            raf.readUInt64() // tensor count
            val metadataCount = raf.readUInt64()
            require(metadataCount in 0L..MAX_ARRAY_ITEMS) { "GGUF metadata entry count is unreasonable" }

            var architecture: String? = null
            val numeric = HashMap<String, Long>()

            repeat(metadataCount.toInt()) {
                val key = raf.readString(MAX_STRING_BYTES)
                when (raf.readUInt32().toInt()) {
                    0 -> readNumeric(raf, key, numeric, 1)
                    1 -> readNumeric(raf, key, numeric, 1)
                    2 -> readNumeric(raf, key, numeric, 2)
                    3 -> readNumeric(raf, key, numeric, 2)
                    4 -> readNumeric(raf, key, numeric, 4)
                    5 -> readNumeric(raf, key, numeric, 4)
                    6 -> raf.skipBytesExact(4)
                    7 -> raf.skipBytesExact(1)
                    8 -> if (key == "general.architecture") architecture = raf.readString(MAX_STRING_BYTES) else raf.skipString()
                    9 -> skipArray(raf)
                    10, 11, 12 -> readNumeric(raf, key, numeric, 8)
                    else -> error("Unsupported GGUF metadata type")
                }
            }

            val arch = architecture?.takeIf { it.isNotBlank() }
                ?: error("GGUF metadata is missing general.architecture")
            val embedding = numeric["$arch.embedding_length"]
                ?: error("GGUF metadata is missing $arch.embedding_length")
            val context = numeric["$arch.context_length"]
                ?: error("GGUF metadata is missing $arch.context_length")
            require(embedding in 1L..Int.MAX_VALUE) { "Invalid embedding dimension: $embedding" }
            require(context in 1L..Int.MAX_VALUE) { "Invalid declared context length: $context" }

            return ModelMetadata(arch, embedding.toInt(), context.toInt(), file.length())
        }
    }

    private fun readNumeric(raf: RandomAccessFile, key: String, out: MutableMap<String, Long>, bytes: Int) {
        val value = when (bytes) {
            1 -> raf.readUnsignedByte().toLong()
            2 -> raf.readUInt16()
            4 -> raf.readUInt32()
            8 -> raf.readUInt64()
            else -> error("Unsupported numeric width")
        }
        if (key.endsWith(".embedding_length") || key.endsWith(".context_length")) out[key] = value
    }

    private fun skipArray(raf: RandomAccessFile) {
        val elementType = raf.readUInt32().toInt()
        val count = raf.readUInt64()
        require(count in 0L..MAX_ARRAY_ITEMS) { "GGUF metadata array is unreasonable" }
        repeat(count.toInt()) { skipValue(raf, elementType) }
    }

    private fun skipValue(raf: RandomAccessFile, type: Int) {
        when (type) {
            0, 1, 7 -> raf.skipBytesExact(1)
            2, 3 -> raf.skipBytesExact(2)
            4, 5, 6 -> raf.skipBytesExact(4)
            8 -> raf.skipString()
            9 -> skipArray(raf)
            10, 11, 12 -> raf.skipBytesExact(8)
            else -> error("Unsupported GGUF array element type: $type")
        }
    }

    private fun RandomAccessFile.readString(maxBytes: Long): String {
        val size = readUInt64()
        require(size in 0L..maxBytes && size <= length() - filePointer) { "GGUF string exceeds bounds" }
        val bytes = ByteArray(size.toInt())
        readFully(bytes)
        return bytes.toString(Charsets.UTF_8)
    }

    private fun RandomAccessFile.skipString() {
        val size = readUInt64()
        require(size >= 0L && size <= length() - filePointer) { "GGUF string exceeds bounds" }
        seek(filePointer + size)
    }

    private fun RandomAccessFile.skipBytesExact(count: Int) {
        require(count <= length() - filePointer) { "GGUF metadata exceeds bounds" }
        seek(filePointer + count)
    }

    private fun RandomAccessFile.readUInt16(): Long =
        readUnsignedByte().toLong() or (readUnsignedByte().toLong() shl 8)

    private fun RandomAccessFile.readUInt32(): Long =
        readUnsignedByte().toLong() or
            (readUnsignedByte().toLong() shl 8) or
            (readUnsignedByte().toLong() shl 16) or
            (readUnsignedByte().toLong() shl 24)

    private fun RandomAccessFile.readUInt64(): Long {
        var value = 0L
        repeat(8) { i -> value = value or (readUnsignedByte().toLong() shl (i * 8)) }
        return value
    }
}
