package com.forgeai.chat.data.memory.resolution

/**
 * Test-only, hand-authored snapshots for correction target resolution.
 *
 * These fixtures deliberately avoid Room, Android, and live conversation dependencies.
 * They define the resolver's input/output contract before its matching algorithm exists.
 */
internal object CorrectionResolverFixtures {
    data class Memory(
        val id: String,
        val conversationId: Long,
        val key: String,
        val value: String,
        val active: Boolean = true,
        val version: Long = 1L
    )

    data class Anchor(
        val conversationId: Long,
        val sourceMessageId: Long,
        val referencedMessageIds: List<Long>,
        val targetMemoryId: String? = null,
        val expectedPreviousValue: String? = null,
        val confidence: Double = 0.98
    )

    enum class Expected {
        RESOLVED,
        AMBIGUOUS,
        UNRESOLVED,
        STALE,
        INVALID
    }

    data class Scenario(
        val name: String,
        val memories: List<Memory>,
        val anchor: Anchor,
        val expected: Expected,
        val expectedTargetId: String? = null
    )

    fun all(): List<Scenario> = listOf(
        Scenario(
            name = "one explicit target with matching previous value",
            memories = listOf(Memory("pref-coffee", 42L, "coffee", "espresso")),
            anchor = Anchor(42L, 318L, listOf(291L, 318L), "pref-coffee", "espresso"),
            expected = Expected.RESOLVED,
            expectedTargetId = "pref-coffee"
        ),
        Scenario(
            name = "two plausible memories with the same previous value",
            memories = listOf(
                Memory("pref-coffee-home", 42L, "coffee_at_home", "espresso"),
                Memory("pref-coffee-order", 42L, "coffee_order", "espresso")
            ),
            anchor = Anchor(42L, 318L, listOf(291L, 318L), expectedPreviousValue = "espresso"),
            expected = Expected.AMBIGUOUS
        ),
        Scenario(
            name = "no matching memory",
            memories = listOf(Memory("pref-tea", 42L, "tea", "oolong")),
            anchor = Anchor(42L, 318L, listOf(318L), expectedPreviousValue = "espresso"),
            expected = Expected.UNRESOLVED
        ),
        Scenario(
            name = "explicit target has changed since anchor creation",
            memories = listOf(Memory("pref-coffee", 42L, "coffee", "cappuccino", version = 2L)),
            anchor = Anchor(42L, 318L, listOf(291L, 318L), "pref-coffee", "espresso"),
            expected = Expected.STALE
        ),
        Scenario(
            name = "explicit target was deleted",
            memories = emptyList(),
            anchor = Anchor(42L, 318L, listOf(291L, 318L), "pref-coffee", "espresso"),
            expected = Expected.UNRESOLVED
        ),
        Scenario(
            name = "target belongs to another conversation",
            memories = listOf(Memory("pref-coffee", 99L, "coffee", "espresso")),
            anchor = Anchor(42L, 318L, listOf(291L, 318L), "pref-coffee", "espresso"),
            expected = Expected.INVALID
        ),
        Scenario(
            name = "inactive memory cannot be corrected",
            memories = listOf(Memory("pref-coffee", 42L, "coffee", "espresso", active = false)),
            anchor = Anchor(42L, 318L, listOf(291L, 318L), "pref-coffee", "espresso"),
            expected = Expected.UNRESOLVED
        ),
        Scenario(
            name = "confidence outside valid range is rejected",
            memories = listOf(Memory("pref-coffee", 42L, "coffee", "espresso")),
            anchor = Anchor(42L, 318L, listOf(291L, 318L), "pref-coffee", "espresso", confidence = 1.5),
            expected = Expected.INVALID
        )
    )
}
