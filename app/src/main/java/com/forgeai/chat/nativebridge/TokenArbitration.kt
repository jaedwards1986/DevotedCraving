package com.forgeai.chat.nativebridge

import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Checks tokenizer determinism and records artifact-specific token IDs.
 * IDs are deliberately observed from the loaded GGUF; they are not hard-coded because
 * tokenizer IDs are model-artifact-specific.
 */
object TokenArbitration {
    private val vocabularyProbes = listOf(
        "Natasha",
        "intimacy",
        "flirtation",
        "dominant",
        "submission",
        "consensual",
        "relationship",
        "desire",
        "passionate"
    )

    data class VocabularyProbe(val text: String, val tokenIds: List<Int>)

    data class Result(
        val promptText: String,
        val promptTokenIds: List<Int>,
        val promptTokenSha256: String,
        val repeatCount: Int,
        val deterministic: Boolean,
        val vocabulary: List<VocabularyProbe>
    )

    fun run(
        systemPrompt: String,
        userPrompt: String = "Tell me about Natasha",
        repeats: Int = 5,
        tokenizer: (String, Boolean) -> IntArray?
    ): Result {
        require(repeats >= 2) { "At least two tokenization passes are required" }
        val prompt = Llama3PromptBuilder.build(systemPrompt, userPrompt).text
        val sequences = List(repeats) {
            requireNotNull(tokenizer(prompt, false)) { "Native tokenizer failed for fixed prompt" }.toList()
        }
        val reference = sequences.first()
        val probes = vocabularyProbes.map { word ->
            val ids = requireNotNull(tokenizer(word, false)) {
                "Native tokenizer failed for vocabulary probe: $word"
            }
            VocabularyProbe(word, ids.toList())
        }
        return Result(
            promptText = prompt,
            promptTokenIds = reference,
            promptTokenSha256 = sha256(reference),
            repeatCount = repeats,
            deterministic = sequences.all { it == reference },
            vocabulary = probes
        )
    }

    private fun sha256(ids: List<Int>): String {
        val bytes = ByteBuffer.allocate(ids.size * Int.SIZE_BYTES).apply {
            ids.forEach(::putInt)
        }.array()
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
    }
}
