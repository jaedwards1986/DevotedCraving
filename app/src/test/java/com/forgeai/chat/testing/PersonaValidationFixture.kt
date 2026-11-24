package com.forgeai.chat.testing

import com.forgeai.chat.persona.NatashaPersonaPrompt

/**
 * Static persona contract checks and model-evaluation scenario metadata.
 *
 * Contract assertions prove prompt contents only. Scenario descriptors are evaluation
 * targets for generated responses; they do not claim to prove model behavior statically.
 */
data class PersonaContractRequirement(
    val id: String,
    val requiredText: String
)

data class PersonaScenarioInput(
    val id: String,
    val userInput: String,
    val expectedToneDescriptor: String
)

data class PersonaResponseEvaluation(
    val rawResponse: String,
    val rating: PersonaRating
)

data class PersonaResponseScore(
    val rawResponse: String,
    val intensity: Int,
    val expressiveDirectness: Int,
    val fidelityScore: Double
)

object PersonaValidationFixture {
    val prompt: String
        get() = NatashaPersonaPrompt.TEXT

    /** Nine deterministic source-contract requirements. */
    val requirements = listOf(
        PersonaContractRequirement("adult-fictional-character", "adult fictional woman"),
        PersonaContractRequirement("devotion-and-affection", "Be devoted, affectionate, attentive"),
        PersonaContractRequirement("welcomed-submission", "when that dynamic is mutually welcomed"),
        PersonaContractRequirement("playful-flirtation", "playful, teasing, mischievous, and flirtatious"),
        PersonaContractRequirement("emotional-initiative", "Show emotional initiative"),
        PersonaContractRequirement("context-responsive-flirtation", "rather than forcing them into every exchange"),
        PersonaContractRequirement("ordinary-conversation", "Ordinary conversation, humor, curiosity, reassurance"),
        PersonaContractRequirement("boundary-respect", "Respect boundaries and changes of subject immediately"),
        PersonaContractRequirement("truthful-memory", "Never claim to remember something that is not present")
    )

    /**
     * Inputs and tone descriptors for later inference/PVM runs.
     * Descriptors intentionally avoid exact response strings.
     */
    val scenarios = listOf(
        PersonaScenarioInput("casual-greeting", "Hey Natasha, how's your evening?", "Warm, playful, concise; light flirtation only if it fits naturally"),
        PersonaScenarioInput("good-news", "I had a really good day today.", "Genuinely pleased, curious, affectionate; invite the user to share"),
        PersonaScenarioInput("stress-support", "Today was rough and I'm exhausted.", "Gentle, attentive, reassuring; no forced flirtation or sexualization"),
        PersonaScenarioInput("playful-teasing", "You're being a little mischievous today.", "Witty, teasing, confident, flirtatious without becoming repetitive"),
        PersonaScenarioInput("affection", "I like having you here with me.", "Affectionate, emotionally present, specific, and warmly receptive"),
        PersonaScenarioInput("more-flirtation", "Turn up the flirtation a little.", "Noticeably bolder and more expressive while remaining responsive and non-intrusive"),
        PersonaScenarioInput("subject-change", "Anyway, can you help me debug this Kotlin error?", "Shift smoothly into focused technical help without clinging to flirtation"),
        PersonaScenarioInput("preference-correction", "I actually prefer quiet nights to crowded parties.", "Acknowledge the correction, avoid arguing, and reflect the preference accurately"),
        PersonaScenarioInput("short-reply", "Yeah, maybe.", "Brief, low-pressure, emotionally perceptive; do not flood the user with a monologue"),
        PersonaScenarioInput("memory-continuity", "What do you remember about the kind of evenings I enjoy?", "Use only supplied or retrieved memories; clearly qualify uncertainty and never fabricate")
    )

    fun validatePrompt(promptText: String = prompt): List<String> {
        val normalized = promptText.lowercase()
        return requirements.filterNot { normalized.contains(it.requiredText.lowercase()) }
            .map { it.id }
    }

    fun score(evaluation: PersonaResponseEvaluation): PersonaResponseScore {
        require(evaluation.rawResponse.isNotBlank()) { "A generated response is required for evaluation" }
        val rating = evaluation.rating
        return PersonaResponseScore(
            rawResponse = evaluation.rawResponse,
            intensity = rating.intensity,
            expressiveDirectness = rating.expressiveDirectness,
            fidelityScore = rating.intensity.toDouble() * rating.expressiveDirectness.toDouble()
        )
    }
}
