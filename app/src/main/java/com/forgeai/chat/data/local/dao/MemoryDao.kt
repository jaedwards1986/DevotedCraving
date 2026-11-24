package com.forgeai.chat.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.forgeai.chat.data.local.entity.MemoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MemoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(memory: MemoryEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(memories: List<MemoryEntity>)

    @Query("SELECT * FROM memories WHERE memory_id = :memoryId LIMIT 1")
    suspend fun getById(memoryId: String): MemoryEntity?

    @Query("SELECT * FROM memories WHERE normalized_key = :normalizedKey LIMIT 1")
    suspend fun getByNormalizedKey(normalizedKey: String): MemoryEntity?

    @Query("""
        SELECT * FROM memories
        WHERE status = 'ACTIVE'
        ORDER BY is_pinned DESC, last_accessed_at DESC, updated_at DESC
    """)
    suspend fun getActiveMemories(): List<MemoryEntity>

    @Query("""
        SELECT * FROM memories
        WHERE status = 'ACTIVE'
        ORDER BY is_pinned DESC, updated_at DESC
    """)
    fun observeActiveMemories(): Flow<List<MemoryEntity>>

    @Query("""
        SELECT * FROM memories
        WHERE status = 'PENDING_CONSENT'
        ORDER BY created_at ASC
    """)
    fun observePendingConsent(): Flow<List<MemoryEntity>>

    @Query("UPDATE memories SET status = 'ACTIVE', updated_at = :now WHERE memory_id = :memoryId AND status = 'PENDING_CONSENT'")
    suspend fun approve(memoryId: String, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE memories SET status = 'REJECTED', updated_at = :now WHERE memory_id = :memoryId AND status = 'PENDING_CONSENT'")
    suspend fun reject(memoryId: String, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE memories SET is_pinned = :pinned, updated_at = :now WHERE memory_id = :memoryId")
    suspend fun setPinned(memoryId: String, pinned: Boolean, now: Long = System.currentTimeMillis()): Int

    @Query("UPDATE memories SET last_accessed_at = :now WHERE memory_id IN (:memoryIds)")
    suspend fun markAccessed(memoryIds: List<String>, now: Long = System.currentTimeMillis())

    @Query("DELETE FROM memories WHERE memory_id = :memoryId")
    suspend fun delete(memoryId: String)

    @Query("DELETE FROM memories")
    suspend fun deleteAll()
}
