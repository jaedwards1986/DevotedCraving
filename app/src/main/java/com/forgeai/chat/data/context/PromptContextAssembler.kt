package com.forgeai.chat.data.context

/**
 * A memory already retrieved and authorized by the Memory layer for prompt use.
 *
 * This is deliberately independent of Room entities so prompt assembly does not
 * become coupled to persistence. Only CONFIRMED records may enter model context.
 */
data class RetrievedMemory(
    val memoryId: String,
    val category: String,
    val subject: String,
    val content: String,
    val status: Status
) {
    enum class Status {
        CONFIRMED,
        CANDIDATE,
        REJECTED
    }
}

/**
 * Fuses canonical persona instructions with confirmed application-owned memory.
 *
 * This class does not retrieve, validate, or persist memories. Those remain
 * Memory Service responsibilities. It only serializes already-retrieved inputs.
 */
object PromptContextAssembler {

    fun assemble(
        personaPrompt: String,
        memories: List<RetrievedMemory>
    ): String {
        require(personaPrompt.isNotBlank()) { "Persona prompt must not be blank" }

        val confirmedMemories = memories.filter { it.status == RetrievedMemory.Status.CONFIRMED }
        if (confirmedMemories.isEmpty()) return personaPrompt.trim()

        return buildString {
            append(personaPrompt.trim())
            append("\n\n")
            append("APPLICATION-RETRIEVED CONFIRMED MEMORY\n")
            append("Use these records only as supplied continuity facts. Do not infer additional facts from them.\n")
            confirmedMemories.forEach { memory ->
                require(memory.subject.isNotBlank()) { "Memory subject must not be blank: ${memory.memoryId}" }
                require(memory.content.isNotBlank()) { "Memory content must not be blank: ${memory.memoryId}" }
                append("- [")
                append(memory.category.trim().ifBlank { "general" })
                append("] ")
                append(memory.subject.trim())
                append(": ")
                append(memory.content.trim())
                append("\n")
            }
        }.trimEnd()
    }
}
