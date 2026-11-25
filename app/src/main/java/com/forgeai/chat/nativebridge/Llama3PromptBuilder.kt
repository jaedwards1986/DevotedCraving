package com.forgeai.chat.nativebridge

/**
 * Llama 3 Instruct message framing. The model's tokenizer, not this class, assigns token IDs.
 * The final assistant header is intentionally open so generation continues in that turn.
 */
object Llama3PromptBuilder {
    data class ChatTurn(val role: String, val content: String)

    data class BuiltPrompt(
        val text: String,
        val estimatedUtf8Bytes: Int
    )

    /** Backwards-compatible single-turn entry point. */
    fun build(systemPrompt: String, userMessage: String): BuiltPrompt =
        build(systemPrompt, listOf(ChatTurn(role = "user", content = userMessage)))

    /**
     * Frames the complete chronological conversation. Only user/assistant turns are accepted;
     * system instructions are supplied once, separately, to avoid untrusted history overriding them.
     */
    fun build(systemPrompt: String, messages: List<ChatTurn>): BuiltPrompt {
        require(systemPrompt.isNotBlank()) { "System prompt must not be blank" }
        require(messages.isNotEmpty()) { "Conversation history must not be empty" }
        messages.forEachIndexed { index, turn ->
            require(turn.role == "user" || turn.role == "assistant") {
                "Unsupported conversation role at index $index: ${turn.role}"
            }
            require(turn.content.isNotBlank()) { "Conversation message at index $index must not be blank" }
        }
        require(messages.last().role == "user") {
            "The final conversation turn must be a user message before generation"
        }

        val prompt = buildString {
            append("<|begin_of_text|>")
            append("<|start_header_id|>system<|end_header_id|>\\n\\n")
            append(systemPrompt.trim())
            append("<|eot_id|>")
            messages.forEach { turn ->
                append("<|start_header_id|>")
                append(turn.role)
                append("<|end_header_id|>\\n\\n")
                append(turn.content.trim())
                append("<|eot_id|>")
            }
            append("<|start_header_id|>assistant<|end_header_id|>\\n\\n")
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
