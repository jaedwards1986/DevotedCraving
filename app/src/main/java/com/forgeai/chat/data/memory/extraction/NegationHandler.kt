package com.forgeai.chat.data.memory.extraction

/** Result of interpreting a narrowly scoped, explicit preference statement. */
internal data class PreferenceMatch(
    val polarity: PreferencePolarity,
    val value: String,
    val matchedRule: String
)

internal enum class PreferencePolarity {
    POSITIVE,
    NEGATIVE
}

/**
 * Handles only explicit first-person preference clauses.
 *
 * Double-negated or otherwise ambiguous constructions deliberately return null:
 * "I don't dislike coffee" is not strong enough evidence to infer "I like coffee".
 * This is a precision-first rule engine, not a general natural-language parser.
 */
internal object NegationHandler {

    private val ambiguousDoubleNegation = Regex(
        """(?i)^\s*I\s+(?:really\s+)?(?:do\s+not|don't|never)\s+(?:dislike|hate|can't stand|love)\s+.+?[.!?]*$"""
    )

    private val explicitNegative = Regex(
        """(?i)^\s*I\s+(?:(?:really|particularly)\s+)?(?:(?:do\s+not|don't)\s+(?:like|enjoy)|dislike|hate|can't stand)\s+(.+?)\s*[.!?]*$"""
    )

    private val explicitPositive = Regex(
        """(?i)^\s*I\s+(?:(?:really|particularly)\s+)?(?:do\s+)?(?:like|love|prefer|enjoy)\s+(.+?)\s*[.!?]*$"""
    )

    fun classify(text: String): PreferenceMatch? {
        val input = text.trim()
        if (input.isEmpty() || ambiguousDoubleNegation.matches(input)) return null

        explicitNegative.matchEntire(input)?.let { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotEmpty()) {
                return PreferenceMatch(
                    polarity = PreferencePolarity.NEGATIVE,
                    value = value,
                    matchedRule = "explicit-negated-preference"
                )
            }
        }

        explicitPositive.matchEntire(input)?.let { match ->
            val value = match.groupValues[1].trim()
            if (value.isNotEmpty()) {
                return PreferenceMatch(
                    polarity = PreferencePolarity.POSITIVE,
                    value = value,
                    matchedRule = "explicit-positive-preference"
                )
            }
        }

        return null
    }
}
