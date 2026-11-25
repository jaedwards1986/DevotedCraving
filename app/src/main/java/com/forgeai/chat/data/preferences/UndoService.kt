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

enum class PersonalityChangeSource { USER_COMMAND, SYSTEM_ACTION, DISCOVERY_RECOMMENDATION }
enum class AuditEventType { CHANGE_APPLIED, CHANGE_UNDONE }

data class PersonalitySnapshot(val values: Map<String, Double>, val version: Long) {
    fun value(dimension: NatashaSettingDimension): Double =
        values[dimension.key] ?: dimension.defaultValue

    companion object {
        fun defaults() = PersonalitySnapshot(
            NatashaSettingDimension.entries.associate { it.key to it.defaultValue }, 0L
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
    val changeHistory: List<PersonalityChange> = emptyList(),
    val auditLog: List<AuditEvent> = emptyList()
)

sealed interface UndoResult {
    data class Restored(val snapshot: PersonalitySnapshot, val undoneChange: PersonalityChange) : UndoResult
    data object NothingToUndo : UndoResult
    data class Failed(val reason: String, val cause: Throwable? = null) : UndoResult
}

/**
 * Raised when persisted personality state cannot safely be read or written.
 * The original DataStore value is deliberately left untouched for recovery.
 */
class PersonalityStatePersistenceException(
    message: String,
    cause: Throwable? = null
) : IllegalStateException(message, cause)


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
            changeId, dimension.key, previous, newValue, originalRequest, source, timestamp
        )
        val snapshot = PersonalitySnapshot(
            state.snapshot.values + (dimension.key to newValue),
            state.snapshot.version + 1
        )
        val audit = AuditEvent(
            UUID.randomUUID().toString(), changeId, AuditEventType.CHANGE_APPLIED,
            source, originalRequest, previous, newValue, timestamp
        )
        return state.copy(
            snapshot = snapshot,
            undoStack = (state.undoStack + change).takeLast(MAX_UNDO_DEPTH),
            changeHistory = state.changeHistory + change,
            auditLog = state.auditLog + audit
        )
    }

    fun undo(
        state: PersonalityStoreState,
        timestamp: Long,
        undoEventId: String = UUID.randomUUID().toString()
    ): Pair<PersonalityStoreState, PersonalityChange?> {
        val change = state.undoStack.lastOrNull() ?: return state to null
        val dimension = NatashaSettingDimension.entries.firstOrNull { it.key == change.dimension }
            ?: throw IllegalStateException(
                "Undo history references an unknown personality setting: ${change.dimension}"
            )
        val current = state.snapshot.value(dimension)
        val snapshot = PersonalitySnapshot(
            state.snapshot.values + (dimension.key to change.previousValue),
            state.snapshot.version + 1
        )
        val audit = AuditEvent(
            undoEventId, change.changeId, AuditEventType.CHANGE_UNDONE,
            PersonalityChangeSource.USER_COMMAND, "undo that", current, change.previousValue,
            timestamp, details = "Undid the original change: ${change.originalRequest}"
        )
        return state.copy(
            snapshot = snapshot,
            undoStack = state.undoStack.dropLast(1),
            auditLog = state.auditLog + audit
        ) to change
    }
}

/**
 * Snapshot, undo stack, change history, and audit log share one serialized
 * DataStore value. Each apply/undo is committed by one atomic DataStore edit.
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
            val updated = PersonalityStateMachine.apply(
                decode(preferences[STATE_KEY]), dimension, newValue, originalRequest,
                source, clock(), UUID.randomUUID().toString()
            )
            preferences[STATE_KEY] = gson.toJson(updated)
            result = updated.snapshot
        }
        return result
    }

    suspend fun undoLastChange(): UndoResult {
        return try {
            var result: UndoResult = UndoResult.NothingToUndo
            context.personalityStateDataStore.edit { preferences ->
                val (updated, change) = PersonalityStateMachine.undo(
                    decode(preferences[STATE_KEY]), clock()
                )
                if (change != null) {
                    preferences[STATE_KEY] = gson.toJson(updated)
                    result = UndoResult.Restored(updated.snapshot, change)
                }
            }
            result
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            UndoResult.Failed(
                reason = error.message ?: "Personality state could not be restored.",
                cause = error
            )
        }
    }

    suspend fun getCurrentSnapshot(): PersonalitySnapshot =
        decode(context.personalityStateDataStore.data.first()[STATE_KEY]).snapshot

    suspend fun getRecentHistory(limit: Int = 10): List<PersonalityChange> {
        require(limit >= 0)
        return decode(context.personalityStateDataStore.data.first()[STATE_KEY])
            .changeHistory.takeLast(limit).asReversed()
    }

    suspend fun getAuditLog(): List<AuditEvent> =
        decode(context.personalityStateDataStore.data.first()[STATE_KEY]).auditLog

    private fun decode(json: String?): PersonalityStoreState {
        if (json == null) return PersonalityStoreState()
        if (json.isBlank()) {
            throw PersonalityStatePersistenceException("Saved personality state is empty.")
        }

        val decoded = try {
            gson.fromJson(json, PersonalityStoreState::class.java)
        } catch (error: Exception) {
            throw PersonalityStatePersistenceException(
                "Saved personality state could not be decoded. The stored data was preserved.",
                error
            )
        } ?: throw PersonalityStatePersistenceException(
            "Saved personality state is missing. The stored data was preserved."
        )

        val valid = runCatching {
            requireNotNull(decoded.snapshot)
            requireNotNull(decoded.snapshot.values)
            require(decoded.snapshot.version >= 0L)
            require(decoded.snapshot.values.values.all { it.isFinite() })
            requireNotNull(decoded.undoStack)
            requireNotNull(decoded.changeHistory)
            requireNotNull(decoded.auditLog)
            require(decoded.undoStack.size <= PersonalityStateMachine.MAX_UNDO_DEPTH)
            (decoded.undoStack + decoded.changeHistory).forEach { change ->
                require(change.changeId.isNotBlank())
                require(change.dimension.isNotBlank())
                require(change.previousValue.isFinite() && change.newValue.isFinite())
                requireNotNull(change.source)
            }
            decoded.auditLog.forEach { event ->
                require(event.eventId.isNotBlank() && event.changeId.isNotBlank())
                requireNotNull(event.eventType)
                requireNotNull(event.source)
                require(event.previousValue.isFinite() && event.newValue.isFinite())
            }
        }.isSuccess

        if (!valid) {
            throw PersonalityStatePersistenceException(
                "Saved personality state failed integrity checks. The stored data was preserved."
            )
        }
        return decoded
    }
}
