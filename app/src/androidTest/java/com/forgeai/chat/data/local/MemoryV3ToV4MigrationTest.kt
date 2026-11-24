package com.forgeai.chat.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forgeai.chat.data.local.entity.ConversationEntity
import com.forgeai.chat.data.local.entity.MessageEntity
import com.forgeai.chat.data.local.entity.UserPreferenceEntity
import com.forgeai.chat.data.local.entity.MemoryEventEntity
import com.forgeai.chat.data.local.entity.CorrectionRecordEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Builds a real Room v3 database, then opens it through the production v4 database
 * and migration. This exercises Room's schema validation, not just raw SQLite SQL.
 */
@RunWith(AndroidJUnit4::class)
class MemoryV3ToV4MigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var v3: RoomDatabase? = null
    private var v4: AppDatabase? = null

    @After
    fun tearDown() {
        v4?.close()
        v3?.close()
        context.deleteDatabase(DATABASE_NAME)
    }

    @Test
    fun migrationPreservesExistingMemoryAndInitializesRevisionToZero() {
        val oldDb = Room.databaseBuilder(context, AppDatabaseV3::class.java, DATABASE_NAME)
            .allowMainThreadQueries()
            .build()
        v3 = oldDb

        oldDb.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO memories (
                memory_id, category, subject, content, normalized_key, sensitivity,
                status, confidence, source_conversation_id, source_message_id,
                embedding, created_at, updated_at, last_accessed_at, is_pinned
            ) VALUES (
                'legacy-memory', 'PREFERENCE', 'model', 'Qwen', 'model',
                'LOW', 'ACTIVE', 0.96, 7, 42, NULL, 100, 100, NULL, 0
            )
            """.trimIndent()
        )
        oldDb.close()
        v3 = null

        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, DATABASE_NAME)
            .addMigrations(MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        v4 = upgraded

        val memory = upgraded.memoryDao().getById("legacy-memory")
        assertNotNull(memory)
        assertEquals("Qwen", memory?.content)
        assertEquals("PREFERENCE", memory?.category)
        assertEquals(7L, memory?.sourceConversationId)
        assertEquals(0L, memory?.revision)
    }

    private companion object {
        const val DATABASE_NAME = "forgeai-v3-v4-migration-test.db"
    }
}

/** Exact v3 entity shape: revision did not exist before MIGRATION_3_4. */
@Entity(
    tableName = "memories",
    indices = [
        Index(value = ["normalized_key"], unique = true),
        Index(value = ["status", "category", "updated_at"]),
        Index(value = ["source_conversation_id"])
    ]
)
internal data class MemoryEntityV3(
    @PrimaryKey @ColumnInfo(name = "memory_id") val memoryId: String,
    val category: String,
    val subject: String,
    val content: String,
    @ColumnInfo(name = "normalized_key") val normalizedKey: String,
    val sensitivity: String,
    val status: String,
    val confidence: Double,
    @ColumnInfo(name = "source_conversation_id") val sourceConversationId: Long?,
    @ColumnInfo(name = "source_message_id") val sourceMessageId: Long?,
    val embedding: ByteArray?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_accessed_at") val lastAccessedAt: Long?,
    @ColumnInfo(name = "is_pinned") val isPinned: Boolean
)

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        MemoryEntityV3::class,
        UserPreferenceEntity::class,
        MemoryEventEntity::class,
        CorrectionRecordEntity::class
    ],
    version = 3,
    exportSchema = false
)
internal abstract class AppDatabaseV3 : RoomDatabase()
