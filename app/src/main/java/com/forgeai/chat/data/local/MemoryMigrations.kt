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
