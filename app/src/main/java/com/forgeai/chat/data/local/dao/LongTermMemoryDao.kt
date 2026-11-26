package com.forgeai.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forgeai.chat.data.local.entity.CorrectionRecordEntity
import com.forgeai.chat.data.local.entity.MemoryEventEntity
import com.forgeai.chat.data.local.entity.UserPreferenceEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LongTermMemoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(preference: UserPreferenceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferences(preferences: List<UserPreferenceEntity>)

    @Query("SELECT * FROM user_preferences WHERE setting_name = :settingName LIMIT 1")
    suspend fun getPreference(settingName: String): UserPreferenceEntity?

    @Query("SELECT * FROM user_preferences WHERE status = 'ACTIVE' ORDER BY updated_at DESC")
    suspend fun getActivePreferences(): List<UserPreferenceEntity>

    @Query("SELECT * FROM user_preferences WHERE status = 'PENDING_CONSENT' ORDER BY updated_at ASC")
    fun observePendingPreferences(): Flow<List<UserPreferenceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: MemoryEventEntity)

    @Query("SELECT * FROM memory_events WHERE event_id = :eventId LIMIT 1")
    suspend fun getEvent(eventId: String): MemoryEventEntity?

    @Query("SELECT * FROM memory_events WHERE status = 'ACTIVE' ORDER BY created_at DESC")
    suspend fun getActiveEvents(): List<MemoryEventEntity>

    @Query("""
        SELECT * FROM memory_events
        WHERE status = 'ACTIVE' AND event_type = 'RELATIONSHIP_MILESTONE'
        ORDER BY created_at DESC, event_id ASC
        LIMIT :limit
    """)
    suspend fun getActiveRelationshipEvents(limit: Int): List<MemoryEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCorrection(correction: CorrectionRecordEntity)

    @Query("SELECT * FROM correction_records WHERE memory_id = :memoryId ORDER BY corrected_at DESC")
    suspend fun getCorrectionsForMemory(memoryId: String): List<CorrectionRecordEntity>
}
