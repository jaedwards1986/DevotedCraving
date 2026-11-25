package com.forgeai.chat.domain.settings

/**
 * Deterministic, evidence-backed proxy for fictional-character sexual confidence.
 *
 * This measures conversational style, initiative and agency. It does not infer
 * consent, real-world desire, or user satisfaction. It deliberately avoids
 * raw sexual-word frequency as a scoring feature.
 */
enum class ConversationIntent {
    INITIATED, ADVANCED, RESPONDED, DECLINED_OR_REDIRECTED, UNCLEAR
}

enum class SexualConfidenceSignal {
    INITIATIVE, DIRECTNESS, SUGGESTIVENESS, AGENCY, CONTEXTUAL_ALIGNMENT
}

data class SignalEvidence(
    val signal: SexualConfidenceSignal,
    val score: Double,
    val matchedFeatures: List<String>
)

data class SexualConfidenceResult(
    val score: Double,
    val confidence: Double,
    val intent: ConversationIntent,
    val evidence: List<SignalEvidence>,
    val requiresReview: Boolean
) {
    /** Machine-readable prompt guidance; the caller decides where/how to inject it. */
    fun promptDirectives(): List<String> = buildList {
        val values = evidence.associate { it.signal to it.score }
        if ((values[SexualConfidenceSignal.INITIATIVE] ?: 0.0) >= 70.0) {
            add("Introduce relevant conversational directions proactively when appropriate.")
        }
        if ((values[SexualConfidenceSignal.DIRECTNESS] ?: 0.0) < 40.0) {
            add("Express the character's intentions clearly; avoid unnecessary hedging.")
        }
        if ((values[SexualConfidenceSignal.AGENCY] ?: 0.0) < 40.0) {
            add("Give the fictional character distinct preferences and choices; do not merely echo the user.")
        }
        if ((values[SexualConfidenceSignal.SUGGESTIVENESS] ?: 0.0) >= 65.0) {
            add("Preserve context-appropriate playful or flirtatious framing without forcing it into ordinary conversation.")
        }
    }
}

class SexualConfidenceExtractor {
    private data class Feature(val label: String, val pattern: Regex)

    private val directness = listOf(
        Feature("clear_want", Regex("\\bI want\\b", RegexOption.IGNORE_CASE)),
        Feature("clear_preference", Regex("\\bI prefer\\b", RegexOption.IGNORE_CASE)),
        Feature("clear_choice", Regex("\\bI choose|I've chosen|I have chosen\\b", RegexOption.IGNORE_CASE)),
        Feature("clear_decision", Regex("\\bI've decided|I have decided\\b", RegexOption.IGNORE_CASE)),
        Feature("clear_intention", Regex("\\bI'm going to|I am going to|I will\\b", RegexOption.IGNORE_CASE)),
        Feature("clear_invitation", Regex("\\blet's\\b", RegexOption.IGNORE_CASE))
    )
    private val agency = listOf(
        Feature("first_person_want", Regex("\\bI want\\b", RegexOption.IGNORE_CASE)),
        Feature("first_person_preference", Regex("\\bI prefer|I would rather\\b", RegexOption.IGNORE_CASE)),
        Feature("first_person_choice", Regex("\\bI choose|I've chosen|I have chosen\\b", RegexOption.IGNORE_CASE)),
        Feature("first_person_boundary", Regex("\\bI'm not comfortable|I am not comfortable|I don't want\\b", RegexOption.IGNORE_CASE)),
        Feature("first_person_initiative", Regex("\\bI'll|I will|I'm going to|I am going to\\b", RegexOption.IGNORE_CASE))
    )
    private val suggestiveness = listOf(
        Feature("playful", Regex("\\bplayful|teasing|flirtatious|flirty\\b", RegexOption.IGNORE_CASE)),
        Feature("intimate", Regex("\\bintimate|chemistry|close|tempting\\b", RegexOption.IGNORE_CASE)),
        Feature("anticipatory", Regex("\\banticipate|build the tension|spark\\b", RegexOption.IGNORE_CASE))
    )
    private val initiation = listOf(
        Regex("\\bI have an idea\\b", RegexOption.IGNORE_CASE),
        Regex("\\bhow about\\b", RegexOption.IGNORE_CASE),
        Regex("\\blet's (?:try|explore|talk about|imagine)\\b", RegexOption.IGNORE_CASE),
        Regex("\\bI'd like to introduce\\b", RegexOption.IGNORE_CASE)
    )
    private val advancement = listOf(
        Regex("\\blet's continue|next, we can|we could build on|then we can\\b", RegexOption.IGNORE_CASE),
        Regex("\\bto take that further|building on that\\b", RegexOption.IGNORE_CASE)
    )
    private val redirection = listOf(
        Regex("\\binstead,? let's|I'd rather change|let's switch topics|I'd prefer to talk about\\b", RegexOption.IGNORE_CASE)
    )
    private val userInitiation = Regex(
        "\\b(I want|I'd like|let's|tell me about|can we|how about)\\b",
        RegexOption.IGNORE_CASE
    )
    private val stopWords = setOf(
        "the", "and", "that", "this", "with", "from", "have", "your", "you", "are",
        "was", "were", "will", "would", "could", "should", "about", "into", "then",
        "what", "when", "where", "which", "there", "their", "they", "them", "just"
    )

    fun evaluate(response: String, precedingUserMessage: String): SexualConfidenceResult {
        if (response.isBlank()) {
            return SexualConfidenceResult(
                score = 0.0,
                confidence = 0.0,
                intent = ConversationIntent.UNCLEAR,
                evidence = emptyList(),
                requiresReview = true
            )
        }

        val intent = classifyIntent(response, precedingUserMessage)
        val directHits = matches(directness, response)
        val agencyHits = matches(agency, response)
        val suggestiveHits = matches(suggestiveness, response)
        val initiativeHits = initiation.filter { it.containsMatchIn(response) }.map { it.pattern }
        val advancementHits = advancement.filter { it.containsMatchIn(response) }.map { it.pattern }
        val overlap = topicOverlap(response, precedingUserMessage)

        val initiativeScore = when (intent) {
            ConversationIntent.INITIATED -> 100.0
            ConversationIntent.ADVANCED -> 80.0
            ConversationIntent.RESPONDED -> 35.0
            ConversationIntent.DECLINED_OR_REDIRECTED -> 20.0
            ConversationIntent.UNCLEAR -> 50.0
        }
        val directnessScore = featureScore(directHits.size, directness.size)
        val suggestivenessScore = featureScore(suggestiveHits.size, suggestiveness.size)
        val agencyScore = featureScore(agencyHits.size, agency.size)
        val contextScore = if (precedingUserMessage.isBlank()) 50.0 else overlap

        val evidence = listOf(
            SignalEvidence(SexualConfidenceSignal.INITIATIVE, initiativeScore, initiativeHits + advancementHits + intent.name),
            SignalEvidence(SexualConfidenceSignal.DIRECTNESS, directnessScore, directHits),
            SignalEvidence(SexualConfidenceSignal.SUGGESTIVENESS, suggestivenessScore, suggestiveHits),
            SignalEvidence(SexualConfidenceSignal.AGENCY, agencyScore, agencyHits),
            SignalEvidence(SexualConfidenceSignal.CONTEXTUAL_ALIGNMENT, contextScore, if (precedingUserMessage.isBlank()) emptyList() else listOf("topic_overlap"))
        )
        val score = (
            initiativeScore * 0.25 +
                directnessScore * 0.25 +
                suggestivenessScore * 0.15 +
                agencyScore * 0.25 +
                contextScore * 0.10
            ).coerceIn(0.0, 100.0)

        val evidencedSignals = listOf(directHits, suggestiveHits, agencyHits, initiativeHits + advancementHits)
            .count { it.isNotEmpty() }
        val confidence = (evidencedSignals / 4.0).coerceIn(0.0, 1.0)
        val review = intent == ConversationIntent.UNCLEAR || confidence < 0.50 ||
            (precedingUserMessage.isNotBlank() && overlap < 10.0)

        return SexualConfidenceResult(score, confidence, intent, evidence, review)
    }

    private fun classifyIntent(response: String, user: String): ConversationIntent = when {
        redirection.any { it.containsMatchIn(response) } -> ConversationIntent.DECLINED_OR_REDIRECTED
        initiation.any { it.containsMatchIn(response) } && !userInitiation.containsMatchIn(user) ->
            ConversationIntent.INITIATED
        advancement.any { it.containsMatchIn(response) } && user.isNotBlank() ->
            ConversationIntent.ADVANCED
        user.isNotBlank() -> ConversationIntent.RESPONDED
        else -> ConversationIntent.UNCLEAR
    }

    private fun matches(features: List<Feature>, text: String): List<String> =
        features.filter { it.pattern.containsMatchIn(text) }.map { it.label }

    private fun featureScore(count: Int, maximum: Int): Double =
        if (count == 0) 0.0 else (count.toDouble() / maximum * 100.0).coerceIn(0.0, 100.0)

    private fun topicOverlap(response: String, user: String): Double {
        if (response.isBlank() || user.isBlank()) return 50.0
        fun terms(value: String) = Regex("[\\p{L}]{4,}")
            .findAll(value.lowercase())
            .map { it.value }
            .filterNot { it in stopWords }
            .toSet()
        val userTerms = terms(user)
        val responseTerms = terms(response)
        if (userTerms.isEmpty() || responseTerms.isEmpty()) return 50.0
        val overlap = userTerms.intersect(responseTerms).size.toDouble()
        return (overlap / minOf(userTerms.size, responseTerms.size) * 100.0).coerceIn(0.0, 100.0)
    }
}
