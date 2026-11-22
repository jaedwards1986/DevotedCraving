package com.natasha.local.data

import androidx.room.*

@Dao interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId=:id ORDER BY createdAt")
    suspend fun forConversation(id:Long):List<MessageEntity>
    @Query("SELECT conversationId FROM messages GROUP BY conversationId ORDER BY MAX(createdAt) DESC")
    suspend fun conversationIds():List<Long>
    @Query("SELECT * FROM messages WHERE conversationId=:id ORDER BY createdAt DESC LIMIT 1")
    suspend fun latest(id:Long):MessageEntity?
    @Query("SELECT * FROM messages WHERE content LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    suspend fun searchAll(q:String):List<MessageEntity>
    @Insert suspend fun insert(m:MessageEntity):Long
    @Query("DELETE FROM messages WHERE conversationId=:id")
    suspend fun deleteConversation(id:Long):Int
}
@Dao interface MemoryDao {
    @Query("SELECT * FROM memories ORDER BY updatedAt DESC") suspend fun all():List<MemoryEntity>
    @Query("SELECT * FROM memories WHERE text LIKE '%' || :q || '%' ORDER BY updatedAt DESC") suspend fun search(q:String):List<MemoryEntity>
    @Query("SELECT * FROM memories WHERE id=:id LIMIT 1") suspend fun get(id:Long):MemoryEntity?
    @Insert suspend fun insert(m:MemoryEntity):Long
    @Update suspend fun update(m:MemoryEntity)
    @Delete suspend fun delete(m:MemoryEntity)
    @Query("DELETE FROM memories") suspend fun clear():Int
}
@Dao interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY updatedAt DESC") suspend fun all():List<ProjectEntity>
    @Query("SELECT * FROM projects WHERE id=:id LIMIT 1") suspend fun get(id:Long):ProjectEntity?
    @Insert suspend fun insert(p:ProjectEntity):Long
    @Update suspend fun update(p:ProjectEntity)
    @Delete suspend fun delete(p:ProjectEntity)
}
@Dao interface AssetDao {
    @Query("SELECT * FROM assets WHERE projectId=:id ORDER BY createdAt DESC") suspend fun forProject(id:Long):List<AssetEntity>
    @Insert suspend fun insert(a:AssetEntity):Long
}
@Dao interface CharacterDao {
    @Query("SELECT * FROM character_profile WHERE id=1") suspend fun get():CharacterProfileEntity?
    @Upsert suspend fun save(p:CharacterProfileEntity)
}
@Dao interface ModelDao {
    @Query("SELECT * FROM models ORDER BY name") suspend fun all():List<ModelEntity>
    @Upsert suspend fun save(m:ModelEntity)
    @Delete suspend fun delete(m:ModelEntity)
    @Query("UPDATE models SET selected=0") suspend fun clearSelection()
    @Query("UPDATE models SET selected=1 WHERE path=:path") suspend fun select(path:String)
    @Query("SELECT * FROM models WHERE selected=1 LIMIT 1") suspend fun selected():ModelEntity?
}
