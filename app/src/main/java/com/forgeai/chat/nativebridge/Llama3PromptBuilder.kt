package com.forgeai.chat.nativebridge

/**
 * Llama 3 Instruct message framing. The model's tokenizer, not this class, assigns token IDs.
 * The final assistant header is intentionally open so generation continues in that turn.
 */
object Llama3PromptBuilder {
    data class BuiltPrompt(
        val text: String,
        val estimatedUtf8Bytes: Int
    )

    fun build(systemPrompt: String, userMessage: String): BuiltPrompt {
        require(systemPrompt.isNotBlank()) { "System prompt must not be blank" }
        require(userMessage.isNotBlank()) { "User message must not be blank" }
        val prompt = buildString {
            append("<|begin_of_text|>")
            append("<|start_header_id|>system<|end_header_id|>\n\n")
            append(systemPrompt.trim())
            append("<|eot_id|>")
            append("<|start_header_id|>user<|end_header_id|>\n\n")
            append(userMessage.trim())
            append("<|eot_id|>")
            append("<|start_header_id|>assistant<|end_header_id|>\n\n")
        }
        return BuiltPrompt(prompt, prompt.toByteArray(Charsets.UTF_8).size)
    }

    /** Tokenizes a fully framed prompt using the currently loaded model vocabulary. */
    fun tokenize(
        systemPrompt: String,
        userMessage: String,
        tokenizer: (String, Boolean) -> IntArray?
    ): IntArray {
        val built = build(systemPrompt, userMessage)
        return requireNotNull(tokenizer(built.text, false)) {
            "Native tokenizer failed; confirm a GGUF model is loaded"
        }.also { require(it.isNotEmpty()) { "Tokenizer returned an empty prompt" } }
    }
}
