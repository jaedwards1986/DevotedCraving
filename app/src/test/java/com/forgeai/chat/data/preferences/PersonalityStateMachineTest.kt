package com.forgeai.chat.data.preferences

import com.forgeai.chat.domain.settings.NatashaSettingDimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PersonalityStateMachineTest {
    private val dimension = NatashaSettingDimension.AFFECTION

    private fun apply(
        state: PersonalityStoreState,
        value: Double,
        id: String
    ) = PersonalityStateMachine.apply(
        state = state,
        dimension = dimension,
        newValue = value,
        originalRequest = "make yourself more affectionate",
        source = PersonalityChangeSource.USER_COMMAND,
        timestamp = id.toLongOrNull() ?: 1L,
        changeId = id
    )

    @Test
    fun applyAndUndoRestoreSnapshotAndKeepAuditAndChangeHistory() {
        val initial = PersonalityStoreState()
        val applied = apply(initial, 0.7, "change-1")

        assertEquals(0.7, applied.snapshot.value(dimension), 0.0001)
        assertEquals(1, applied.undoStack.size)
        assertEquals(1, applied.changeHistory.size)
        assertEquals(AuditEventType.CHANGE_APPLIED, applied.auditLog.single().eventType)

        val (undone, change) = PersonalityStateMachine.undo(applied, timestamp = 2L)

        assertEquals("change-1", change?.changeId)
        assertEquals(dimension.defaultValue, undone.snapshot.value(dimension), 0.0001)
        assertTrue(undone.undoStack.isEmpty())
        assertEquals(1, undone.changeHistory.size)
        assertEquals(2, undone.auditLog.size)
        assertEquals(AuditEventType.CHANGE_UNDONE, undone.auditLog.last().eventType)
        assertEquals("change-1", undone.auditLog.last().changeId)
    }

    @Test
    fun consecutiveChangesUndoInReverseOrder() {
        val first = apply(PersonalityStoreState(), 0.6, "change-1")
        val second = apply(first, 0.8, "change-2")

        val (afterFirstUndo, undoneSecond) = PersonalityStateMachine.undo(second, 3L)
        assertEquals("change-2", undoneSecond?.changeId)
        assertEquals(0.6, afterFirstUndo.snapshot.value(dimension), 0.0001)
        assertEquals(1, afterFirstUndo.undoStack.size)

        val (afterSecondUndo, undoneFirst) = PersonalityStateMachine.undo(afterFirstUndo, 4L)
        assertEquals("change-1", undoneFirst?.changeId)
        assertEquals(dimension.defaultValue, afterSecondUndo.snapshot.value(dimension), 0.0001)
        assertTrue(afterSecondUndo.undoStack.isEmpty())
        assertEquals(2, afterSecondUndo.changeHistory.size)
        assertEquals(4, afterSecondUndo.auditLog.size)
    }

    @Test
    fun undoOnEmptyStackDoesNotChangeState() {
        val initial = PersonalityStoreState()
        val (result, change) = PersonalityStateMachine.undo(initial, 1L)

        assertEquals(initial, result)
        assertEquals(null, change)
    }

    @Test
    fun undoStackIsLimitedToTenButChangeHistoryIsRetained() {
        var state = PersonalityStoreState()
        for (index in 1..11) {
            state = apply(state, 0.5 + index * 0.03, "change-$index")
        }

        assertEquals(PersonalityStateMachine.MAX_UNDO_DEPTH, state.undoStack.size)
        assertEquals(11, state.changeHistory.size)
        assertEquals("change-11", state.undoStack.last().changeId)
        assertEquals("change-2", state.undoStack.first().changeId)
    }

    @Test(expected = IllegalArgumentException::class)
    fun outOfRangeValueIsRejectedRatherThanSilentlyClamped() {
        apply(PersonalityStoreState(), 0.95, "invalid")
    }

    @Test
    fun concurrentChangesAreLinearizedBySerializedStoreHarness() = runBlocking {
        // This models DataStore.edit's serialized transaction contract, not DataStore internals.
        val mutex = Mutex()
        var persisted = PersonalityStoreState()
        val values = (1..40).map { index -> 0.2 + index * 0.015 }

        values.mapIndexed { index, value ->
            async(Dispatchers.Default) {
                mutex.withLock {
                    persisted = PersonalityStateMachine.apply(
                        state = persisted,
                        dimension = dimension,
                        newValue = value,
                        originalRequest = "concurrent change $index",
                        source = PersonalityChangeSource.USER_COMMAND,
                        timestamp = index.toLong(),
                        changeId = "concurrent-$index"
                    )
                }
            }
        }.awaitAll()

        val history = persisted.changeHistory
        assertEquals(40, history.size)
        assertEquals(40L, persisted.snapshot.version)
        assertEquals(PersonalityStateMachine.MAX_UNDO_DEPTH, persisted.undoStack.size)
        assertEquals(history.takeLast(PersonalityStateMachine.MAX_UNDO_DEPTH), persisted.undoStack)
        assertEquals(history.last().newValue, persisted.snapshot.value(dimension), 0.000001)
        assertEquals(40, history.map { it.changeId }.toSet().size)
        assertEquals(40, persisted.auditLog.count { it.eventType == AuditEventType.CHANGE_APPLIED })
    }

    @Test
    fun decoderTreatsMissingPayloadAsNewInstallOnly() {
        assertEquals(PersonalityStoreState(), decodePersonalityStoreState(null))
    }

    @Test
    fun decoderRejectsTruncatedJsonWithoutReplacingIt() {
        val error = try {
            decodePersonalityStoreState("""{"snapshot":{"values":""")
            null
        } catch (caught: PersonalityStatePersistenceException) {
            caught
        }
        assertTrue(error != null)
        assertTrue(error!!.message!!.contains("preserved"))
    }

    @Test
    fun decoderRejectsBlankAndExplicitNullPayloads() {
        listOf("", "   ", "null").forEach { payload ->
            var rejected = false
            try {
                decodePersonalityStoreState(payload)
            } catch (_: PersonalityStatePersistenceException) {
                rejected = true
            }
            assertTrue("Expected rejection for payload: '$payload'", rejected)
        }
    }

    @Test
    fun decoderRejectsMissingMandatoryFieldsAndUnknownUndoDimension() {
        val missingSnapshot = """{"undoStack":[],"changeHistory":[],"auditLog":[]}"""
        var missingRejected = false
        try {
            decodePersonalityStoreState(missingSnapshot)
        } catch (_: PersonalityStatePersistenceException) {
            missingRejected = true
        }
        assertTrue(missingRejected)

        val corrupt = PersonalityStateMachine.apply(
            PersonalityStoreState(), dimension, 0.7, "test",
            PersonalityChangeSource.USER_COMMAND, 1L, "known-change"
        ).copy(undoStack = listOf(
            PersonalityChange("bad", "NOT_A_SETTING", 0.5, 0.7, "bad",
                PersonalityChangeSource.USER_COMMAND, 2L)
        ))
        var undoRejected = false
        try {
            PersonalityStateMachine.undo(corrupt, 3L)
        } catch (_: IllegalStateException) {
            undoRejected = true
        }
        assertTrue(undoRejected)
    }

    @Test
    fun applyingSameValueDoesNotCreateUndoOrAuditEntry() {
        val initial = PersonalityStoreState()
        val unchanged = apply(initial, dimension.defaultValue, "no-op")

        assertEquals(initial, unchanged)
        assertFalse(unchanged.auditLog.any { it.eventType == AuditEventType.CHANGE_APPLIED })
    }
}
