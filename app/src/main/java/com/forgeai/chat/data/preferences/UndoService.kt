package com.forgeai.chat.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.forgeai.chat.domain.settings.NatashaSettingDimension
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import java.util.UUID

private val Context.personalityStateDataStore: DataStore<Preferences> by
    preferencesDataStore(name = "natasha_personality_state")

enum class PersonalityChangeSource {
    USER_COMMAND,
    SYSTEM_ACTION,
    DISCOVERY_RECOMMENDATION
}

enum class AuditEventType {
    CHANGE_APPLIED,
    CHANGE_UNDONE
}

data class PersonalitySnapshot(
    val values: Map<String, Double>,
    val version: Long
) {
    fun value(dimension: NatashaSettingDimension): Double =
        values[dimension.key] ?: dimension.defaultValue

    companion object {
        fun defaults() = PersonalitySnapshot(
            values = NatashaSettingDimension.entries.associate { it.key to it.defaultValue },
            version = 0L
        )
    }
}

data class PersonalityChange(
    val changeId: String,
    val dimension: String,
    val previousValue: Double,
    val newValue: Double,
    val originalRequest: String,
    val source: PersonalityChangeSource,
    val timestamp: Long
)

data class AuditEvent(
    val eventId: String,
    val changeId: String,
    val eventType: AuditEventType,
    val source: PersonalityChangeSource,
    val originalRequest: String,
    val previousValue: Double,
    val newValue: Double,
    val timestamp: Long,
    val outcome: String = "SUCCESS",
    val details: String? = null
)

data class PersonalityStoreState(
    val snapshot: PersonalitySnapshot = PersonalitySnapshot.defaults(),
    val undoStack: List<PersonalityChange> = emptyList(),
    val auditLog: List<AuditEvent> = emptyList()
)

sealed interface UndoResult {
    data class Restored(
        val snapshot: PersonalitySnapshot,
        val undoneChange: PersonalityChange
    ) : UndoResult

    data object NothingToUndo : UndoResult
}

internal object PersonalityStateMachine {
    const val MAX_UNDO_DEPTH = 10

    fun apply(
        state: PersonalityStoreState,
        dimension: NatashaSettingDimension,
        newValue: Double,
        originalRequest: String,
        source: PersonalityChangeSource,
        timestamp: Long,
        changeId: String
    ): PersonalityStoreState {
        require(newValue.isFinite()) { "Personality value must be finite." }
        require(newValue in dimension.minimum..dimension.maximum) {
            "${dimension.key} must be between ${dimension.minimum} and ${dimension.maximum}."
        }

        val previous = state.snapshot.value(dimension)
        if (previous == newValue) return state

        val change = PersonalityChange(
            changeId = changeId,
            dimension = dimension.key,
            previousValue = previous,
            newValue = newValue,
            originalRequest = originalRequest,
            source = source,
            timestamp = timestamp
        )
        val updatedValues = state.snapshot.values + (dimension.key to newValue)
        val updatedSnapshot = PersonalitySnapshot(updatedValues, state.snapshot.version + 1)
        val audit = AuditEvent(
            eventId = UUID.randomUUID().toString(),
            changeId = changeId,
            eventType = AuditEventType.CHANGE_APPLIED,
            source = source,
            originalRequest = originalRequest,
            previousValue = previous,
            newValue = newValue,
            timestamp = timestamp
        )
        return state.copy(
            snapshot = updatedSnapshot,
            undoStack = (state.undoStack + change).takeLast(MAX_UNDO_DEPTH),
            auditLog = state.auditLog + audit
        )
    }

    fun undo(state: PersonalityStoreState, timestamp: Long): Pair<PersonalityStoreState, PersonalityChange?> {
        val change = state.undoStack.lastOrNull() ?: return state to null
        val dimension = NatashaSettingDimension.entries.firstOrNull { it.key == change.dimension }
            ?: return state to null
        val current = state.snapshot.value(dimension)
        val restoredValues = state.snapshot.values + (dimension.key to change.previousValue)
        val snapshot = PersonalitySnapshot(restoredValues, state.snapshot.version + 1)
        val audit = AuditEvent(
            eventId = UUID.randomUUID().toString(),
            changeId = change.changeId,
            eventType = AuditEventType.CHANGE_UNDONE,
            source = change.source,
            originalRequest = change.originalRequest,
            previousValue = current,
            newValue = change.previousValue,
            timestamp = timestamp,
            details = "Restored the value recorded before change ${change.changeId}."
        )
        return state.copy(
            snapshot = snapshot,
            undoStack = state.undoStack.dropLast(1),
            auditLog = state.auditLog + audit
        ) to change
    }
}

/**
 * Persists the current snapshot, undo stack, and append-only audit log in one
 * Preferences DataStore value. Each edit is a single atomic DataStore transaction.
 */
class UndoService(
    private val context: Context,
    private val gson: Gson = Gson(),
    private val clock: () -> Long = System::currentTimeMillis
) {
    private companion object {
        val STATE_KEY = stringPreferencesKey("personality_state_v1")
    }

    suspend fun applyChange(
        dimension: NatashaSettingDimension,
        newValue: Double,
        originalRequest: String,
        source: PersonalityChangeSource = PersonalityChangeSource.USER_COMMAND,
        authorizedByUser: Boolean = false
    ): PersonalitySnapshot {
        require(source == PersonalityChangeSource.USER_COMMAND || authorizedByUser) {
            "Non-user changes require explicit user authorization."
        }
        var result = PersonalitySnapshot.defaults()
        context.personalityStateDataStore.edit { preferences ->
            val current = decode(preferences[STATE_KEY])
            val updated = PersonalityStateMachine.apply(
                state = current,
                dimension = dimension,
                newValue = newValue,
                originalRequest = originalRequest,
                source = source,
                timestamp = clock(),
                changeId = UUID.randomUUID().toString()
            )
            preferences[STATE_KEY] = gson.toJson(updated)
            result = updated.snapshot
        }
        return result
    }

    suspend fun undoLastChange(): UndoResult {
        var result: UndoResult = UndoResult.NothingToUndo
        context.personalityStateDataStore.edit { preferences ->
            val current = decode(preferences[STATE_KEY])
            val (updated, change) = PersonalityStateMachine.undo(current, clock())
            if (change != null) {
                preferences[STATE_KEY] = gson.toJson(updated)
                result = UndoResult.Restored(updated.snapshot, change)
            } else {
                result = UndoResult.NothingToUndo
            }
        }
        return result
    }

    suspend fun getCurrentSnapshot(): PersonalitySnapshot =
        decode(context.personalityStateDataStore.data.first()[STATE_KEY]).snapshot

    suspend fun getRecentHistory(limit: Int = 10): List<PersonalityChange> {
        require(limit >= 0)
        return decode(context.personalityStateDataStore.data.first()[STATE_KEY])
            .undoStack.takeLast(limit).asReversed()
    }

    suspend fun getAuditLog(): List<AuditEvent> =
        decode(context.personalityStateDataStore.data.first()[STATE_KEY]).auditLog

    private fun decode(json: String?): PersonalityStoreState {
        if (json.isNullOrBlank()) return PersonalityStoreState()
        return runCatching {
            gson.fromJson(json, PersonalityStoreState::class.java) ?: PersonalityStoreState()
        }.getOrElse { PersonalityStoreState() }
    }
}
