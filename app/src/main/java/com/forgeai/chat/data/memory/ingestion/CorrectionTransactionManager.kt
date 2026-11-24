package com.forgeai.chat.data.memory.ingestion

import androidx.room.withTransaction
import com.forgeai.chat.data.local.AppDatabase
import com.forgeai.chat.data.local.dao.LongTermMemoryDao
import com.forgeai.chat.data.local.dao.MemoryDao
import com.forgeai.chat.data.local.entity.CorrectionRecordEntity
import com.forgeai.chat.data.local.entity.MemoryEntity
import com.forgeai.chat.data.memory.resolution.AnchoredCorrection
import com.forgeai.chat.data.memory.resolution.CorrectionResolution
import java.security.MessageDigest
import java.util.Locale

class CorrectionTransactionManager(
    private val database: AppDatabase,
    private val memoryDao: MemoryDao,
    private val longTermMemoryDao: LongTermMemoryDao
) {
    suspend fun write(
        input: AnchoredCorrection,
        resolution: CorrectionResolution.Resolved,
        consentGranted: Boolean
    ): CorrectionWriteResult {
        val correctedValue = input.candidate.value?.trim()
            ?.takeIf(String::isNotEmpty)
            ?: return CorrectionWriteResult.Rejected("Correction has no corrected value.")

        return try {
            database.withTransaction {
                val target = memoryDao.getById(resolution.targetMemoryId)
                    ?: return@withTransaction CorrectionWriteResult.Conflict(
                        "Target memory no longer exists."
                    )

                if (target.sourceConversationId != input.anchor.conversationId ||
                    target.status != ACTIVE
                ) {
                    return@withTransaction recordConflict(
                        input, resolution, target,
                        "Target ownership or active state changed."
                    )
                }

                if (normalize(target.content) != normalize(resolution.expectedPreviousValue)) {
                    return@withTransaction recordConflict(
                        input, resolution, target,
                        "Target value changed after validation."
                    )
                }

                if (!consentGranted) {
                    val correctionId = correctionId(input, resolution)
                    longTermMemoryDao.insertCorrection(
                        correctionRecord(
                            input, resolution, correctionId, target.content,
                            correctedValue, PENDING_CONSENT
                        )
                    )
                    return@withTransaction CorrectionWriteResult.AwaitingConsent(correctionId)
                }

                val updated = memoryDao.applyCorrectionIfUnchanged(
                    memoryId = target.memoryId,
                    conversationId = input.anchor.conversationId,
                    expectedPreviousValue = target.content,
                    expectedRevision = resolution.expectedVersion,
                    correctedValue = correctedValue
                )

                if (updated != 1) {
                    return@withTransaction recordConflict(
                        input, resolution, target,
                        "Target revision changed before the correction update."
                    )
                }

                val correctionId = correctionId(input, resolution)
                longTermMemoryDao.insertCorrection(
                    correctionRecord(
                        input, resolution, correctionId, target.content,
                        correctedValue, APPLIED
                    )
                )
                CorrectionWriteResult.Applied(correctionId, resolution.targetMemoryId)
            }
        } catch (error: Exception) {
            CorrectionWriteResult.Failed(error.message ?: error::class.java.simpleName)
        }
    }

    private suspend fun recordConflict(
        input: AnchoredCorrection,
        resolution: CorrectionResolution.Resolved,
        target: MemoryEntity,
        reason: String
    ): CorrectionWriteResult {
        val correctionId = correctionId(input, resolution)
        longTermMemoryDao.insertCorrection(
            correctionRecord(
                input, resolution, correctionId, target.content,
                input.candidate.value!!.trim(), CONFLICT, reason
            )
        )
        return CorrectionWriteResult.Conflict(reason)
    }

    private fun correctionRecord(
        input: AnchoredCorrection,
        resolution: CorrectionResolution.Resolved,
        correctionId: String,
        previousValue: String,
        correctedValue: String,
        status: String,
        reason: String? = null
    ) = CorrectionRecordEntity(
        correctionId = correctionId,
        memoryId = resolution.targetMemoryId,
        previousValue = previousValue,
        correctedValue = correctedValue,
        reason = reason ?: resolution.evidence,
        sensitivity = input.candidate.sensitivity.name,
        status = status,
        sourceConversationId = input.anchor.conversationId,
        sourceMessageId = input.candidate.sourceMessageId
    )

    private fun correctionId(
        input: AnchoredCorrection,
        resolution: CorrectionResolution.Resolved
    ): String {
        val source = buildString {
            append(resolution.targetMemoryId)
            append('|')
            append(input.anchor.sourceMessageId)
            append('|')
            append(resolution.expectedVersion)
            append('|')
            append(input.candidate.value?.trim().orEmpty())
        }
        return "correction:" + stableId(source)
    }

    private fun normalize(value: String): String =
        value.trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

    private fun stableId(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { "%02x".format(it) }
    }

    private companion object {
        const val ACTIVE = "ACTIVE"
        const val PENDING_CONSENT = "PENDING_CONSENT"
        const val APPLIED = "APPLIED"
        const val CONFLICT = "CONFLICT"
    }
}

sealed interface CorrectionWriteResult {
    data class Applied(val correctionId: String, val targetMemoryId: String) : CorrectionWriteResult
    data class AwaitingConsent(val correctionId: String) : CorrectionWriteResult
    data class Conflict(val reason: String) : CorrectionWriteResult
    data class Rejected(val reason: String) : CorrectionWriteResult
    data class Failed(val reason: String) : CorrectionWriteResult
}
