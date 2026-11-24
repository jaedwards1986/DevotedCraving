package com.forgeai.chat.data.memory.ingestion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.forgeai.chat.data.local.AppDatabase
import com.forgeai.chat.data.local.entity.MemoryEntity
import com.forgeai.chat.data.memory.extraction.ExtractionCandidate
import com.forgeai.chat.data.memory.extraction.ExtractionCategory
import com.forgeai.chat.data.memory.extraction.SensitivityLevel
import com.forgeai.chat.data.memory.resolution.AnchorRelation
import com.forgeai.chat.data.memory.resolution.AnchorValidator
import com.forgeai.chat.data.memory.resolution.AnchoredCorrection
import com.forgeai.chat.data.memory.resolution.ContextualAnchor
import com.forgeai.chat.data.memory.resolution.CorrectionContext
import com.forgeai.chat.data.memory.resolution.CorrectionContextProvider
import com.forgeai.chat.data.memory.resolution.CorrectionMemoryCandidate
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CorrectionTransactionManagerTest {
    private lateinit var database: AppDatabase
    private lateinit var service: MemoryWriterService

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java
        ).allowMainThreadQueries().build()

        service = MemoryWriterService(
            database = database,
            memoryDao = database.longTermMemoryDao(),
            correctionValidator = AnchorValidator(Provider(database))
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resolvedCorrectionAtomicallyUpdatesMemoryAndAudit() = runBlocking {
        seedMemory()

        val result = service.ingestCorrection(correction(), consentGranted = true)

        assertTrue(result is CorrectionWriteResult.Applied)
        val stored = database.memoryDao().getById("memory-1")
        assertEquals("Llama 3 8B", stored?.content)
        assertEquals(1L, stored?.revision)

        val audit = database.longTermMemoryDao().getCorrectionsForMemory("memory-1")
        assertEquals(1, audit.size)
        assertEquals("APPLIED", audit.single().status)
        assertEquals("Qwen", audit.single().previousValue)
        assertEquals("Llama 3 8B", audit.single().correctedValue)
    }

    @Test
    fun missingConsentDoesNotMutateTarget() = runBlocking {
        seedMemory()

        val result = service.ingestCorrection(correction(), consentGranted = false)

        assertTrue(result is CorrectionWriteResult.AwaitingConsent)
        val stored = database.memoryDao().getById("memory-1")
        assertEquals("Qwen", stored?.content)
        assertEquals(0L, stored?.revision)
        assertEquals("PENDING_CONSENT", database.longTermMemoryDao()
            .getCorrectionsForMemory("memory-1").single().status)
    }

    @Test
    fun changedRevisionProducesConflictAndDoesNotOverwriteCurrentValue() = runBlocking {
        seedMemory()
        database.memoryDao().upsert(
            database.memoryDao().getById("memory-1")!!.copy(
                content = "Changed elsewhere",
                revision = 1L
            )
        )

        val result = service.ingestCorrection(correction(), consentGranted = true)

        assertTrue(result is CorrectionWriteResult.Conflict)
        val stored = database.memoryDao().getById("memory-1")
        assertEquals("Changed elsewhere", stored?.content)
        assertEquals(1L, stored?.revision)
        assertEquals("CONFLICT", database.longTermMemoryDao()
            .getCorrectionsForMemory("memory-1").single().status)
    }

    @Test
    fun auditFailureRollsBackMemoryUpdate() = runBlocking {
        seedMemory()
        database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_correction_insert BEFORE INSERT ON correction_records " +
                "BEGIN SELECT RAISE(ABORT, 'forced audit failure'); END"
        )

        val result = service.ingestCorrection(correction(), consentGranted = true)

        assertTrue(result is CorrectionWriteResult.Failed)
        val stored = database.memoryDao().getById("memory-1")
        assertEquals("Qwen", stored?.content)
        assertEquals(0L, stored?.revision)
        assertEquals(0, database.longTermMemoryDao()
            .getCorrectionsForMemory("memory-1").size)
    }

    private suspend fun seedMemory() {
        database.memoryDao().upsert(
            MemoryEntity(
                memoryId = "memory-1",
                category = "PREFERENCE",
                subject = "model",
                content = "Qwen",
                normalizedKey = "model",
                status = "ACTIVE",
                sourceConversationId = 7L,
                sourceMessageId = 42L,
                revision = 0L
            )
        )
    }

    private fun correction() = AnchoredCorrection(
        candidate = ExtractionCandidate(
            category = ExtractionCategory.CORRECTION,
            value = "Llama 3 8B",
            confidence = 0.96,
            sensitivity = SensitivityLevel.LOW,
            sourceMessageId = 42L,
            matchedRule = "correction-rule",
            previousValue = "Qwen"
        ),
        anchor = ContextualAnchor(
            conversationId = 7L,
            sourceMessageId = 42L,
            referencedMessageIds = listOf(42L),
            relation = AnchorRelation.DIRECT_CORRECTION,
            expectedPreviousValue = "Qwen",
            confidence = 0.90,
            evidence = "The user explicitly corrected the model choice."
        )
    )

    private class Provider(
        private val database: AppDatabase
    ) : CorrectionContextProvider {
        override suspend fun getContext(
            conversationId: Long,
            sourceMessageId: Long
        ): CorrectionContext {
            val memory = database.memoryDao().getById("memory-1")
                ?: error("fixture memory missing")
            return CorrectionContext(
                conversationId = conversationId,
                sourceMessageId = sourceMessageId,
                availableMessageIds = setOf(sourceMessageId),
                activeMemories = listOf(
                    CorrectionMemoryCandidate(
                        memoryId = memory.memoryId,
                        conversationId = memory.sourceConversationId!!,
                        normalizedKey = memory.normalizedKey,
                        currentValue = memory.content,
                        version = memory.revision,
                        active = memory.status == "ACTIVE"
                    )
                )
            )
        }
    }
}
