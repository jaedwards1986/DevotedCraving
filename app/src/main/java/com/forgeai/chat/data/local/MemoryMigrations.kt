package com.forgeai.chat.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Additive migration: existing conversations and messages remain untouched.
 * No destructive fallback should be registered for this database.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memories (
                memory_id TEXT NOT NULL,
                category TEXT NOT NULL,
                subject TEXT NOT NULL,
                content TEXT NOT NULL,
                normalized_key TEXT NOT NULL,
                sensitivity TEXT NOT NULL,
                status TEXT NOT NULL,
                confidence REAL NOT NULL,
                source_conversation_id INTEGER,
                source_message_id INTEGER,
                embedding BLOB,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_accessed_at INTEGER,
                is_pinned INTEGER NOT NULL,
                PRIMARY KEY(memory_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS index_memories_normalized_key ON memories(normalized_key)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_memories_status_category_updated_at ON memories(status, category, updated_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_memories_source_conversation_id ON memories(source_conversation_id)"
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS user_preferences (
                setting_name TEXT NOT NULL,
                setting_value TEXT NOT NULL,
                metadata TEXT,
                sensitivity TEXT NOT NULL,
                status TEXT NOT NULL,
                confidence REAL NOT NULL,
                source_conversation_id INTEGER,
                source_message_id INTEGER,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(setting_name)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_user_preferences_status_updated_at ON user_preferences(status, updated_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_user_preferences_sensitivity_status ON user_preferences(sensitivity, status)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS memory_events (
                event_id TEXT NOT NULL,
                narrative_snippet TEXT NOT NULL,
                related_entity_id TEXT,
                confidence_score REAL NOT NULL,
                sensitivity TEXT NOT NULL,
                status TEXT NOT NULL,
                source_conversation_id INTEGER,
                source_message_id INTEGER,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY(event_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_memory_events_related_entity_id ON memory_events(related_entity_id)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_memory_events_status_created_at ON memory_events(status, created_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_memory_events_source_conversation_id ON memory_events(source_conversation_id)"
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS correction_records (
                correction_id TEXT NOT NULL,
                memory_id TEXT NOT NULL,
                previous_value TEXT NOT NULL,
                corrected_value TEXT NOT NULL,
                reason TEXT,
                sensitivity TEXT NOT NULL,
                status TEXT NOT NULL,
                source_conversation_id INTEGER,
                source_message_id INTEGER,
                corrected_at INTEGER NOT NULL,
                PRIMARY KEY(correction_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_correction_records_memory_id_corrected_at ON correction_records(memory_id, corrected_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_correction_records_status_corrected_at ON correction_records(status, corrected_at)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS index_correction_records_source_conversation_id ON correction_records(source_conversation_id)"
        )
    }
}

/**
 * Adds the monotonic optimistic-concurrency token without rebuilding the memories table.
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE memories ADD COLUMN revision INTEGER NOT NULL DEFAULT 0"
        )
    }
}
