package com.forgeai.chat.nativebridge

import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GgufMetadataReaderTest {
    @Test
    fun readsRequiredMetadata() {
        val file = writeFixture(
            version = 3,
            entries = listOf(
                stringEntry("general.architecture", "llama"),
                uint32Entry("llama.embedding_length", 4096),
                uint32Entry("llama.context_length", 8192)
            )
        )

        try {
            val metadata = GgufMetadataReader.read(file)
            assertEquals("llama", metadata.architecture)
            assertEquals(4096, metadata.embeddingDimension)
            assertEquals(8192, metadata.declaredContextLength)
            assertEquals(file.length(), metadata.fileSize)
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsUnsupportedVersion() {
        val file = writeFixture(
            version = 1,
            entries = listOf(
                stringEntry("general.architecture", "llama"),
                uint32Entry("llama.embedding_length", 4096),
                uint32Entry("llama.context_length", 8192)
            )
        )

        try {
            assertFailsWith<IllegalArgumentException> { GgufMetadataReader.read(file) }
        } finally {
            file.delete()
        }
    }

    @Test
    fun rejectsMissingArchitecture() {
        val file = writeFixture(
            version = 3,
            entries = listOf(
                uint32Entry("llama.embedding_length", 4096),
                uint32Entry("llama.context_length", 8192)
            )
        )

        try {
            assertFailsWith<IllegalStateException> { GgufMetadataReader.read(file) }
        } finally {
            file.delete()
        }
    }

    private fun writeFixture(version: Int, entries: List<ByteArray>): File {
        val out = ByteArrayOutputStream()
        out.write(byteArrayOf(0x47, 0x47, 0x55, 0x46))
        writeU32(out, version.toLong())
        writeU64(out, 0)
        writeU64(out, entries.size.toLong())
        entries.forEach(out::write)
        return File.createTempFile("gguf-mdrh-", ".gguf").also {
            it.writeBytes(out.toByteArray())
        }
    }

    private fun stringEntry(key: String, value: String): ByteArray {
        val out = ByteArrayOutputStream()
        writeString(out, key)
        writeU32(out, 8)
        writeString(out, value)
        return out.toByteArray()
    }

    private fun uint32Entry(key: String, value: Int): ByteArray {
        val out = ByteArrayOutputStream()
        writeString(out, key)
        writeU32(out, 4)
        writeU32(out, value.toLong())
        return out.toByteArray()
    }

    private fun writeString(out: ByteArrayOutputStream, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeU64(out, bytes.size.toLong())
        out.write(bytes)
    }

    private fun writeU32(out: ByteArrayOutputStream, value: Long) {
        repeat(4) { i -> out.write(((value ushr (i * 8)) and 0xff).toInt()) }
    }

    private fun writeU64(out: ByteArrayOutputStream, value: Long) {
        repeat(8) { i -> out.write(((value ushr (i * 8)) and 0xff).toInt()) }
    }
}
