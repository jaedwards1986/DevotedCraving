package com.forgeai.chat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.forgeai.chat.data.local.dao.ChatDao
import com.forgeai.chat.data.local.dao.MemoryDao
import com.forgeai.chat.data.local.entity.ConversationEntity
import com.forgeai.chat.data.local.entity.MemoryEntity
import com.forgeai.chat.data.local.entity.UserPreferenceEntity
import com.forgeai.chat.data.local.entity.MemoryEventEntity
import com.forgeai.chat.data.local.entity.CorrectionRecordEntity
import com.forgeai.chat.data.local.entity.MessageEntity

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntity::class,
        UserPreferenceEntity::class,
        MemoryEventEntity::class,
        CorrectionRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun chatDao(): ChatDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        private const val DATABASE_NAME = "forgeai_chat.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instance = it }
            }
        }
    }
}
