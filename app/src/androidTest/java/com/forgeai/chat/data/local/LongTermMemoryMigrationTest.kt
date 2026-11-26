package com.forgeai.chat.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forgeai.chat.data.local.entity.CorrectionRecordEntity
import com.forgeai.chat.data.local.entity.MemoryEventEntity
import com.forgeai.chat.data.local.entity.MemoryEntity
import com.forgeai.chat.data.local.entity.UserPreferenceEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LongTermMemoryMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private var roomDatabase: AppDatabase? = null
    private var legacyDatabase: SQLiteOpenHelper? = null

    @After
    fun tearDown() {
        roomDatabase?.close()
        legacyDatabase?.close()
        context.deleteDatabase(LEGACY_DATABASE_NAME)
    }

    @Test
    fun roomSchemaMapsAllLongTermMemoryEntities() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        roomDatabase = db
        val dao = db.longTermMemoryDao()

        val preference = UserPreferenceEntity(
            settingName = "reply_tone",
            settingValue = "warm and direct",
            status = "ACTIVE"
        )
        dao.upsertPreference(preference)
        assertEquals(preference, dao.getPreference("reply_tone"))

        val event = MemoryEventEntity(
            eventId = "event-1",
            narrativeSnippet = "The user prefers concise project updates",
            relatedEntityId = "project-forgeai",
            status = "ACTIVE"
        )
        dao.upsertEvent(event)
        assertEquals(event, dao.getEvent("event-1"))

        val relationshipEvent = MemoryEventEntity(
            eventId = "relationship-1",
            narrativeSnippet = "A confirmed fictional relationship milestone",
            relatedEntityId = "primary-persona",
            eventType = "RELATIONSHIP_MILESTONE",
            status = "ACTIVE"
        )
        val pendingRelationshipEvent = relationshipEvent.copy(
            eventId = "relationship-pending",
            status = "PENDING_CONSENT"
        )
        dao.upsertEvent(relationshipEvent)
        dao.upsertEvent(pendingRelationshipEvent)
        assertEquals(
            listOf(relationshipEvent),
            dao.getActiveRelationshipEvents(limit = 10)
        )

        val correction = CorrectionRecordEntity(
            correctionId = "correction-1",
            memoryId = "memory-1",
            previousValue = "prefers long reports",
            correctedValue = "prefers concise reports",
            reason = "User corrected the preference"
        )
        dao.insertCorrection(correction)
        assertEquals(listOf(correction), dao.getCorrectionsForMemory("memory-1"))
    }

    @Test
    fun additiveMigrationsPreserveMoreThanOneHundredLegacyMessages() = runBlocking {
        val helper = LegacyV1Database(context, LEGACY_DATABASE_NAME)
        legacyDatabase = helper
        helper.writableDatabase
        helper.close()

        val db = Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            LEGACY_DATABASE_NAME
        )
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
            .build()
        roomDatabase = db

        val conversation = db.chatDao().getConversation(1L)
        assertNotNull(conversation)

        val messages = db.chatDao().getMessagesOnce(1L)
        assertEquals(120, messages.size)
        assertEquals("historical message 1", messages.first().content)
        assertEquals("historical message 120", messages.last().content)

        val preference = UserPreferenceEntity(
            settingName = "format",
            settingValue = "bullet points",
            status = "ACTIVE"
        )
        db.longTermMemoryDao().upsertPreference(preference)
        assertEquals(preference, db.longTermMemoryDao().getPreference("format"))

        // Exercise the pre-existing canonical memories table as well.
        val pineTreeMemory = MemoryEntity(
            memoryId = "pine-tree-preference",
            category = "PREFERENCE",
            subject = "Pine trees",
            content = "The user likes pine trees",
            normalizedKey = "preference:likes-pine-trees",
            status = "ACTIVE"
        )
        db.memoryDao().upsert(pineTreeMemory)
        assertEquals(pineTreeMemory, db.memoryDao().getById("pine-tree-preference"))

        // Deleting one historical message must not cascade into either memory store.
        val firstMessageId = messages.first().id
        db.chatDao().deleteMessage(firstMessageId)
        assertEquals(119, db.chatDao().getMessagesOnce(1L).size)
        assertEquals(pineTreeMemory, db.memoryDao().getById("pine-tree-preference"))
        assertEquals(preference, db.longTermMemoryDao().getPreference("format"))
        assertNull(db.chatDao().getConversation(999L))

        // Confirm the expected additive indices exist in the upgraded SQLite schema.
        val indexedTables = db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type = 'index'"
        ).use { cursor ->
            buildSet {
                val nameColumn = cursor.getColumnIndexOrThrow("name")
                while (cursor.moveToNext()) add(cursor.getString(nameColumn))
            }
        }
        assertTrue(indexedTables.contains("index_user_preferences_status_updated_at"))
        assertTrue(indexedTables.contains("index_memory_events_related_entity_id"))
        assertTrue(indexedTables.contains("index_memory_events_status_event_type_created_at"))
        assertTrue(indexedTables.contains("index_correction_records_memory_id_corrected_at"))
    }

    private class LegacyV1Database(
        context: Context,
        name: String
    ) : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE conversations (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    title TEXT NOT NULL,
                    model_name TEXT NOT NULL,
                    created_at INTEGER NOT NULL,
                    updated_at INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX index_conversations_updated_at ON conversations(updated_at)"
            )
            db.execSQL(
                """
                CREATE TABLE messages (
                    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    conversation_id INTEGER NOT NULL,
                    role TEXT NOT NULL,
                    content TEXT NOT NULL,
                    model_name TEXT NOT NULL,
                    is_error INTEGER NOT NULL,
                    created_at INTEGER NOT NULL,
                    FOREIGN KEY(conversation_id) REFERENCES conversations(id) ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX index_messages_conversation_id_created_at ON messages(conversation_id, created_at)"
            )

            val conversation = ContentValues().apply {
                put("id", 1L)
                put("title", "Historical conversation")
                put("model_name", "legacy-model")
                put("created_at", 1L)
                put("updated_at", 120L)
            }
            db.insertOrThrow("conversations", null, conversation)

            repeat(120) { index ->
                val message = ContentValues().apply {
                    put("conversation_id", 1L)
                    put("role", if (index % 2 == 0) "user" else "assistant")
                    put("content", "historical message ${index + 1}")
                    put("model_name", "legacy-model")
                    put("is_error", 0)
                    put("created_at", (index + 1).toLong())
                }
                db.insertOrThrow("messages", null, message)
            }
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val LEGACY_DATABASE_NAME = "forgeai-ltm-migration-test.db"
    }
}
