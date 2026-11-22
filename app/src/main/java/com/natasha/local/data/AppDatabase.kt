package com.natasha.local.data
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
@Database(entities=[MessageEntity::class,MemoryEntity::class,ProjectEntity::class,AssetEntity::class,CharacterProfileEntity::class,ModelEntity::class],version=3,exportSchema=false)
abstract class AppDatabase:RoomDatabase(){ abstract fun messages():MessageDao; abstract fun memories():MemoryDao; abstract fun projects():ProjectDao; abstract fun assets():AssetDao; abstract fun character():CharacterDao; abstract fun models():ModelDao
companion object {
    @Volatile private var instance:AppDatabase?=null
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE INDEX IF NOT EXISTS index_messages_conversationId_createdAt ON messages(conversationId, createdAt)")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_memories_updatedAt ON memories(updatedAt)")
        }
    }
    fun get(c:Context)=instance?:synchronized(this){
        instance?:Room.databaseBuilder(c,AppDatabase::class.java,"natasha.db")
            .addMigrations(MIGRATION_2_3)
            .build().also{instance=it}
    }
} }