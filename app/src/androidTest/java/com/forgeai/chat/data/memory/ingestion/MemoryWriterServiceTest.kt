package com.forgeai.chat.data.memory.ingestion

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import com.forgeai.chat.data.local.AppDatabase
import com.forgeai.chat.data.memory.extraction.ExtractionCandidate
import com.forgeai.chat.data.memory.extraction.ExtractionCategory
import com.forgeai.chat.data.memory.extraction.SensitivityLevel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MemoryWriterServiceTest {
    private lateinit var database: AppDatabase
    private lateinit var service: MemoryWriterService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()
        service = MemoryWriterService(database, database.longTermMemoryDao())
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun preferenceIsWrittenPendingConsentAndDuplicateIsIdempotent() = runBlocking {
        val candidate = candidate(ExtractionCategory.PREFERENCE, "I like dark roast coffee.")
        val first = service.ingest(candidate)
        assertTrue(first is IngestionResult.AwaitingConsent)
        val recordId = (first as IngestionResult.AwaitingConsent).recordId
        val stored = database.longTermMemoryDao().getPreference(recordId)
        assertEquals("I like dark roast coffee.", stored?.settingValue)
        assertEquals("PENDING_CONSENT", stored?.status)
        assertEquals(42L, stored?.sourceMessageId)

        val duplicate = service.ingest(candidate)
        assertTrue(duplicate is IngestionResult.Duplicate)
        assertEquals(1, database.longTermMemoryDao().getActivePreferences().size)
    }

    @Test
    fun eventIsWrittenPendingConsent() = runBlocking {
        val result = service.ingest(candidate(ExtractionCategory.EVENT, "We adopted a dog."))
        assertTrue(result is IngestionResult.AwaitingConsent)
        val recordId = (result as IngestionResult.AwaitingConsent).recordId
        val stored = database.longTermMemoryDao().getEvent(recordId)
        assertEquals("We adopted a dog.", stored?.narrativeSnippet)
        assertEquals("PENDING_CONSENT", stored?.status)
    }

    @Test
    fun sensitiveAndCorrectionCandidatesAreNotPersisted() = runBlocking {
        val sensitive = candidate(
            ExtractionCategory.SENSITIVE_DATA,
            value = null,
            sensitivity = SensitivityLevel.HIGH
        )
        val correction = candidate(
            ExtractionCategory.CORRECTION,
            value = "The model is Llama 3.",
            previousValue = "Qwen"
        )

        assertTrue(service.ingest(sensitive) is IngestionResult.Rejected)
        assertTrue(service.ingest(correction) is IngestionResult.Rejected)

        val db = database.openHelper.readableDatabase
        db.query("SELECT COUNT(*) FROM user_preferences").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM memory_events").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
        db.query("SELECT COUNT(*) FROM correction_records").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    private fun candidate(
        category: ExtractionCategory,
        value: String?,
        sensitivity: SensitivityLevel = SensitivityLevel.LOW,
        previousValue: String? = null
    ) = ExtractionCandidate(
        category = category,
        value = value,
        confidence = 0.96,
        sensitivity = sensitivity,
        sourceMessageId = 42L,
        matchedRule = "test-rule",
        previousValue = previousValue
    )
}
