package com.nexora.app.model

data class LocalModel(
    val fileName: String,
    val sizeBytes: Long,
    val sha256: String,
    val importedAtMillis: Long
)
