package com.forgeai.chat.data.preferences

import com.forgeai.chat.domain.settings.NatashaSettingDimension
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
    fun applyingSameValueDoesNotCreateUndoOrAuditEntry() {
        val initial = PersonalityStoreState()
        val unchanged = apply(initial, dimension.defaultValue, "no-op")

        assertEquals(initial, unchanged)
        assertFalse(unchanged.auditLog.any { it.eventType == AuditEventType.CHANGE_APPLIED })
    }
}
