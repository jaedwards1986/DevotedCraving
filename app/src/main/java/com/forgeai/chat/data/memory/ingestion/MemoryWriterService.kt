package com.forgeai.chat.data.memory.ingestion

import androidx.room.withTransaction
import com.forgeai.chat.data.local.AppDatabase
import com.forgeai.chat.data.local.dao.LongTermMemoryDao
import com.forgeai.chat.data.local.entity.MemoryEventEntity
import com.forgeai.chat.data.local.entity.UserPreferenceEntity
import com.forgeai.chat.data.memory.extraction.ExtractionCandidate
import com.forgeai.chat.data.memory.extraction.ExtractionCategory
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import java.util.Locale

/**
 * Minimal ingress adapter. It intentionally does not perform semantic
 * consolidation or overwrite conflicting memories.
 *
 * All writes remain PENDING_CONSENT. Corrections are rejected until candidates
 * carry a stable target-memory identity; guessing a correction's target would
 * risk attaching an audit record to the wrong memory.
 */
class MemoryWriterService(
    private val database: AppDatabase,
    private val memoryDao: LongTermMemoryDao
) : IngestionService {

    override suspend fun ingest(candidate: ExtractionCandidate): IngestionResult {
        if (candidate.category == ExtractionCategory.SENSITIVE_DATA) {
            // Never persist the original message or candidate value for this signal.
            return IngestionResult.Rejected("Sensitive-data signals are not persisted.")
        }

        if (candidate.category == ExtractionCategory.UNCERTAIN) {
            return IngestionResult.Rejected("Uncertain statements are not durable memories.")
        }

        if (candidate.category == ExtractionCategory.CORRECTION) {
            return IngestionResult.Rejected(
                "Correction target identity is missing; no memory was changed."
            )
        }

        val value = candidate.value?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return IngestionResult.Rejected("Candidate has no persistable value.")

        return try {
            database.withTransaction {
                when (candidate.category) {
                    ExtractionCategory.PREFERENCE,
                    ExtractionCategory.DISLIKE -> persistPreference(candidate, value)

                    ExtractionCategory.EVENT -> persistEvent(candidate, value)

                    ExtractionCategory.CORRECTION,
                    ExtractionCategory.UNCERTAIN,
                    ExtractionCategory.SENSITIVE_DATA -> error("Category was screened before transaction.")
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            IngestionResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    private suspend fun persistPreference(
        candidate: ExtractionCandidate,
        value: String
    ): IngestionResult {
        val category = candidate.category.name
        val normalized = normalize(value)
        val settingName = "${category.lowercase(Locale.ROOT)}:${stableId(normalized)}"
        val existing = memoryDao.getPreference(settingName)
        if (existing != null) {
            return IngestionResult.Duplicate(category, settingName)
        }

        memoryDao.upsertPreference(
            UserPreferenceEntity(
                settingName = settingName,
                settingValue = value,
                metadata = "matchedRule=${candidate.matchedRule}",
                sensitivity = candidate.sensitivity.name,
                status = PENDING_CONSENT,
                confidence = candidate.confidence,
                sourceMessageId = candidate.sourceMessageId
            )
        )
        return IngestionResult.AwaitingConsent(category, settingName)
    }

    private suspend fun persistEvent(
        candidate: ExtractionCandidate,
        value: String
    ): IngestionResult {
        val eventId = "event:${stableId(normalize(value))}"
        if (memoryDao.getEvent(eventId) != null) {
            return IngestionResult.Duplicate(candidate.category.name, eventId)
        }

        memoryDao.upsertEvent(
            MemoryEventEntity(
                eventId = eventId,
                narrativeSnippet = value,
                confidenceScore = candidate.confidence,
                sensitivity = candidate.sensitivity.name,
                status = PENDING_CONSENT,
                sourceMessageId = candidate.sourceMessageId
            )
        )
        return IngestionResult.AwaitingConsent(candidate.category.name, eventId)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun stableId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val PENDING_CONSENT = "PENDING_CONSENT"
    }
}
