package com.forgeai.chat.data.memory.ingestion

import com.forgeai.chat.data.memory.extraction.ExtractionCandidate

/**
 * Persists validated extraction proposals. Implementations must preserve consent
 * state and must not treat extraction confidence as consent.
 */
interface IngestionService {
    suspend fun ingest(candidate: ExtractionCandidate): IngestionResult
}

sealed interface IngestionResult {
    data class AwaitingConsent(
        val category: String,
        val recordId: String
    ) : IngestionResult

    data class Duplicate(
        val category: String,
        val recordId: String
    ) : IngestionResult

    data class Rejected(val reason: String) : IngestionResult

    data class Failed(val reason: String) : IngestionResult
}
