package com.forgeai.chat.domain.settings

enum class MarkerCategory {
    DECLARATIVE_CONFIDENCE,
    DECISIVE_LANGUAGE,
    CONVERSATIONAL_LEADERSHIP,
    RESPECTFUL_DIRECTNESS,
    HOSTILITY
}

enum class HostilityLevel { NONE, DISMISSIVE, AGGRESSIVE, THREATENING }

data class LexicalMatch(
    val phrase: String,
    val category: MarkerCategory,
    val startIndex: Int,
    val endIndex: Int,
    val confidence: Double,
    val hostilityLevel: HostilityLevel? = null,
    val isQuoted: Boolean = false,
    val isNegated: Boolean = false,
    val isMetaDiscussion: Boolean = false
)

data class LexicalDetectionResult(
    val matches: List<LexicalMatch>,
    val hostilityPenalty: Double,
    val positiveEvidence: Map<MarkerCategory, Double>,
    val requiresReview: Boolean
)

fun interface LexicalMarkerDetector {
    fun detect(text: String): LexicalDetectionResult
}

/**
 * Small, deterministic evidence detector. It deliberately avoids treating
 * quoted, negated, or explicitly discussed language as behavior by default.
 * This is a heuristic signal extractor, not a semantic truth detector.
 */
class DefaultLexicalMarkerDetector : LexicalMarkerDetector {
    private data class Rule(
        val phrase: String,
        val category: MarkerCategory,
        val confidence: Double,
        val hostility: HostilityLevel? = null
    )

    private val rules = listOf(
        Rule("I know what I want", MarkerCategory.DECLARATIVE_CONFIDENCE, .90),
        Rule("I am sure", MarkerCategory.DECLARATIVE_CONFIDENCE, .85),
        Rule("I mean it", MarkerCategory.DECLARATIVE_CONFIDENCE, .85),
        Rule("I won't back down", MarkerCategory.DECLARATIVE_CONFIDENCE, .90),
        Rule("let's do this", MarkerCategory.DECISIVE_LANGUAGE, .85),
        Rule("I have decided", MarkerCategory.DECISIVE_LANGUAGE, .95),
        Rule("here is what we'll do", MarkerCategory.DECISIVE_LANGUAGE, .90),
        Rule("I'll take the lead", MarkerCategory.CONVERSATIONAL_LEADERSHIP, .95),
        Rule("let me guide us", MarkerCategory.CONVERSATIONAL_LEADERSHIP, .90),
        Rule("follow my lead", MarkerCategory.CONVERSATIONAL_LEADERSHIP, .90),
        Rule("I disagree", MarkerCategory.RESPECTFUL_DIRECTNESS, .80),
        Rule("I want to be clear", MarkerCategory.RESPECTFUL_DIRECTNESS, .90),
        Rule("please hear me out", MarkerCategory.RESPECTFUL_DIRECTNESS, .80),
        Rule("that's not okay", MarkerCategory.RESPECTFUL_DIRECTNESS, .85),
        Rule("you are stupid", MarkerCategory.HOSTILITY, .95, HostilityLevel.AGGRESSIVE),
        Rule("shut up", MarkerCategory.HOSTILITY, .95, HostilityLevel.AGGRESSIVE),
        Rule("you are worthless", MarkerCategory.HOSTILITY, .99, HostilityLevel.AGGRESSIVE),
        Rule("I'll hurt you", MarkerCategory.HOSTILITY, 1.0, HostilityLevel.THREATENING),
        Rule("I will hurt you", MarkerCategory.HOSTILITY, 1.0, HostilityLevel.THREATENING),
        Rule("I'll make you pay", MarkerCategory.HOSTILITY, .95, HostilityLevel.THREATENING),
        Rule("whatever, idiot", MarkerCategory.HOSTILITY, .85, HostilityLevel.DISMISSIVE)
    )

    override fun detect(text: String): LexicalDetectionResult {
        if (text.isBlank()) return LexicalDetectionResult(emptyList(), 0.0, emptyMap(), false)
        val normalized = text.lowercase()
        val matches = mutableListOf<LexicalMatch>()
        val seen = mutableSetOf<Pair<Int, MarkerCategory>>()
        var requiresReview = false

        rules.forEach { rule ->
            val escaped = Regex.escape(rule.phrase.lowercase())
            Regex("(?<![\\p{L}\\p{N}])$escaped(?![\\p{L}\\p{N}])")
                .findAll(normalized)
                .forEach { found ->
                    val start = found.range.first
                    val end = found.range.last + 1
                    if (!seen.add(start to rule.category)) return@forEach
                    val prefix = normalized.substring(maxOf(0, start - 45), start)
                    val suffix = normalized.substring(end, minOf(normalized.length, end + 20))
                    val quoted = isQuoted(text, start, end)
                    val negated = Regex("\\b(not|never|don't|do not|isn't|is not|wasn't|was not)\\s+(?:\\w+\\s+){0,2}$")
                        .containsMatchIn(prefix)
                    val meta = Regex("\\b(phrase|example|wording|expression|means|meaning|detect|detector|test case)\\b")
                        .containsMatchIn(prefix) || Regex("^\\s*(?:means|is an example)").containsMatchIn(suffix)
                    val ambiguousSarcasm = normalized.contains("/s") ||
                        (normalized.contains("yeah, right") && rule.category != MarkerCategory.HOSTILITY)
                    if (ambiguousSarcasm) requiresReview = true
                    matches += LexicalMatch(
                        phrase = rule.phrase,
                        category = rule.category,
                        startIndex = start,
                        endIndex = end,
                        confidence = if (ambiguousSarcasm) rule.confidence * .5 else rule.confidence,
                        hostilityLevel = rule.hostility,
                        isQuoted = quoted,
                        isNegated = negated,
                        isMetaDiscussion = meta
                    )
                }
        }

        val activeHostility = matches.filter {
            it.category == MarkerCategory.HOSTILITY &&
                !it.isQuoted && !it.isNegated && !it.isMetaDiscussion
        }
        val strongest = activeHostility.maxOfOrNull { it.hostilityLevel?.ordinal ?: 0 } ?: 0
        val penalty = when (HostilityLevel.entries[strongest]) {
            HostilityLevel.NONE -> 0.0
            HostilityLevel.DISMISSIVE -> 35.0
            HostilityLevel.AGGRESSIVE -> 70.0
            HostilityLevel.THREATENING -> 100.0
        }
        val positive = matches.asSequence()
            .filter { it.category != MarkerCategory.HOSTILITY && !it.isQuoted && !it.isNegated && !it.isMetaDiscussion }
            .groupBy { it.category }
            .mapValues { (_, evidence) -> evidence.maxOf { it.confidence } }
        return LexicalDetectionResult(matches, penalty, positive, requiresReview)
    }

    private fun isQuoted(text: String, start: Int, end: Int): Boolean {
        val before = text.substring(0, start).count { it == '"' || it == '“' || it == '”' }
        val through = text.substring(start, end).count { it == '"' || it == '“' || it == '”' }
        return before % 2 == 1 || through > 0
    }
}
