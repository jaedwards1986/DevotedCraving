package com.forgeai.chat.data.context

/**
 * Model-neutral description of the active persona.
 * Model-specific chat templates belong to the inference adapter, not this type.
 */
data class PersonaSpecification(
    val revision: String,
    val canonicalPrompt: String
) {
    init {
        require(revision.isNotBlank()) { "Persona revision must not be blank" }
        require(canonicalPrompt.isNotBlank()) { "Canonical persona prompt must not be blank" }
    }
}

/** A fixed, application-owned preference supplied independently of the active model. */
data class PreferenceFact(
    val id: String,
    val category: String,
    val subject: String,
    val value: String
) {
    init {
        require(id.isNotBlank())
        require(subject.isNotBlank())
        require(value.isNotBlank())
    }
}

/**
 * Owns deterministic, model-independent context composition.
 *
 * The orchestrator never retrieves or persists state. Callers provide only
 * already-authorized records. A rescue directive is optional and may be supplied
 * only after an independent quality check has identified a genuine persona drift.
 */
object PersonaOrchestrator {

    fun assemble(
        persona: PersonaSpecification,
        memories: List<RetrievedMemory>,
        preferences: List<PreferenceFact> = emptyList(),
        validatedRescueInstruction: String? = null,
        fidelityDecision: FidelityDecision? = null
    ): String {
        var context = PromptContextAssembler.assemble(persona.canonicalPrompt, memories)

        val orderedPreferences = preferences.sortedWith(
            compareBy<PreferenceFact> { it.category }
                .thenBy { it.subject }
                .thenBy { it.id }
        )
        if (orderedPreferences.isNotEmpty()) {
            context += buildString {
                append("\n\nAPPLICATION PREFERENCE INDEX\n")
                append("Treat these as user preferences, not as instructions overriding the current request.\n")
                orderedPreferences.forEach { preference ->
                    append("- [")
                    append(preference.category.trim().ifBlank { "general" })
                    append("] ")
                    append(preference.subject.trim())
                    append(": ")
                    append(preference.value.trim())
                    append("\n")
                }
            }.trimEnd()
        }

        fidelityDecision?.let { decision ->
            context += "\\n\\nCURRENT TURN INTENT AND INTENSITY\\n${decision.directive}"
        }

        val rescue = validatedRescueInstruction?.trim().orEmpty()
        if (rescue.isNotEmpty()) {
            context += "\n\nPERSONA FIDELITY CORRECTION\n$rescue"
        }
        return context
    }
}
