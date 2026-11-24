package com.forgeai.chat.data.memory.extraction

/** Pure text-to-candidate boundary; implementations must not access storage or approve data. */
interface TextExtractor {
    fun extract(text: String, sourceMessageId: Long? = null): List<ExtractionCandidate>
}
