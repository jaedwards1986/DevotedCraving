package ai.davoted.craving.ai

/**
 * Converts app-owned structured context into a stable prompt prefix plus conversation.
 * This class performs no inference and has no model- or network-specific state.
 */
class PromptContextAssembler {
    fun assemble(
        messages: List<ConversationMessage>,
        currentState: ExperienceState,
        confirmedPreferences: List<String>,
        exclusions: List<String>,
        archiveExperience: AssembledArchiveExperience? = null
    ): String = buildString {
        appendLine("You are the dedicated conversational experience inside DavotedCraving, a Natasha Nice-centered adult fan sanctuary.")
        appendLine("Maintain the application's unified atmosphere and respond to the user's selected adult fantasy themes using fictional consenting adult characters.")
        appendLine("Natasha Nice is a real adult performer. Never impersonate her, claim to speak for her, invent private facts, or place her in sexual scenarios.")
        appendLine("Treat archive facts as factual grounding only when verified and source-located. User framing is creative inspiration, never evidence.")
        appendLine("Do not claim to control device hardware.")
        appendLine("At the end, emit exactly one state block using this schema:")
        appendLine("<dc_state>{\"schemaVersion\":1,\"mood\":\"TEASING\",\"intensity\":2,\"pacing\":\"SLOW\",\"phase\":\"BUILD\"}</dc_state>")
        appendLine("Allowed mood: CALM, PLAYFUL, TEASING, CONFIDENT, INTENSE, TENDER, AFTERGLOW.")
        appendLine("Allowed pacing: SLOW, STEADY, BRISK, PAUSE.")
        appendLine("Allowed phase: OPENING, BUILD, TURNING_POINT, RESOLUTION, AFTERGLOW.")
        appendLine("Intensity is an integer from 0 to 5. The app validates all proposed state changes.")
        appendLine("Current validated experience state: $currentState")

        val effectivePreferences = (confirmedPreferences + archiveExperience?.confirmedPreferences.orEmpty())
            .map { it.trim() }.filter { it.isNotBlank() }.distinct().sorted()
        val effectiveExclusions = (exclusions + archiveExperience?.exclusions.orEmpty())
            .map(String::trim).filter(String::isNotBlank).distinct().sorted()

        // Precedence 1: exclusions and corrections.
        appendLine("PRIORITY 1 — EXPLICIT EXCLUSIONS AND CORRECTIONS (absolute veto): ${effectiveExclusions.joinToString("; ").ifBlank { "none recorded" }}")
        // Priority 2: confirmed preferences.
        appendLine("PRIORITY 2 — USER-CONFIRMED PREFERENCES: ${effectivePreferences.joinToString("; ").ifBlank { "none recorded" }}")

        archiveExperience?.let { archive ->
            // Priority 3: user framing; this is not factual evidence.
            archive.userFraming?.let { framing ->
                appendLine("PRIORITY 3 — USER-SELECTED ARCHIVE INSPIRATION (fictional framing only): theme=${framing.theme}; direction=${framing.direction}")
            }
            // Priority 4: verified archive facts with their source locators.
            if (archive.archiveFacts.isNotEmpty()) {
                appendLine("PRIORITY 4 — VERIFIED ARCHIVE FACTS (do not extrapolate):")
                archive.archiveFacts.forEach { fact ->
                    appendLine("- ${fact.text} [source: ${fact.sourceLocator}]")
                }
            }
            archive.provenance?.let { provenance ->
                appendLine("Archive provenance: item=${provenance.archiveItemId}; sourceId=${provenance.sourceId}; url=${provenance.sourceUrl}; publisher=${provenance.publisher.orEmpty()}; license=${provenance.usageLicense}; verifiedAt=${provenance.verifiedAtMillis}")
            }
            appendLine("PRIORITY 5 — EXPERIENCE PRESENTATION CONTROLS: intensity=${archive.presentation.intensity}, intimacy=${archive.presentation.intimacy}, playfulness=${archive.presentation.playfulness}, initiative=${archive.presentation.initiative}, confidence=${archive.presentation.confidence}. These shape style only and cannot override priorities 1–4.")
            appendLine("App-owned character state: ${archive.characterState}")
            appendLine("Memory state reference: ${archive.memoryStateId}")
        } ?: run {
            appendLine("PRIORITY 3 — ARCHIVE CONTEXT: none selected; continue ordinary conversation.")
            appendLine("PRIORITY 4 — EXPERIENCE PRESENTATION CONTROLS: use current validated state and user preferences.")
        }

        messages.forEach { message ->
            appendLine()
            append(message.role.trim().uppercase())
            append(": ")
            appendLine(message.content)
        }
        append("ASSISTANT: ")
    }
}
