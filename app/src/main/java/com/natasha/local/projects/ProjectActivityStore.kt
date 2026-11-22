package com.natasha.local.projects

import android.content.Context
import java.io.File
import java.util.Base64

data class ProjectActivity(
    val timestamp: Long,
    val action: String,
    val path: String,
    val detail: String
)

/** Small append-only project journal stored beside the private workspace. */
class ProjectActivityStore(context: Context, projectId: Long) {
    private val file = File(context.filesDir, "projects/$projectId/activity.log")

    @Synchronized
    fun record(action: String, path: String = "", detail: String = "") {
        file.parentFile?.mkdirs()
        val row = listOf(
            System.currentTimeMillis().toString(),
            encode(action),
            encode(path),
            encode(detail.take(1000))
        ).joinToString("\t") + "\n"
        file.appendText(row, Charsets.UTF_8)
        if (file.length() > MAX_JOURNAL_BYTES) {
            val retained = recent(MAX_RETAINED_EVENTS).asReversed().map { event ->
                listOf(
                    event.timestamp.toString(),
                    encode(event.action),
                    encode(event.path),
                    encode(event.detail)
                ).joinToString("\t")
            }
            file.writeText(retained.joinToString("\n", postfix = if (retained.isEmpty()) "" else "\n"), Charsets.UTF_8)
        }
    }

    @Synchronized
    fun recent(limit: Int = 100): List<ProjectActivity> {
        require(limit in 1..500)
        if (!file.exists()) return emptyList()
        return file.useLines { lines ->
            lines.mapNotNull { line ->
                runCatching {
                    val fields = line.split("\t")
                    require(fields.size == 4)
                    ProjectActivity(fields[0].toLong(), decode(fields[1]), decode(fields[2]), decode(fields[3]))
                }.getOrNull()
            }.toList().takeLast(limit).reversed()
        }
    }

    companion object {
        private const val MAX_JOURNAL_BYTES = 1024L * 1024L
        private const val MAX_RETAINED_EVENTS = 400
    }

    private fun encode(value: String): String =
        Base64.getEncoder().encodeToString(value.toByteArray(Charsets.UTF_8))

    private fun decode(value: String): String =
        String(Base64.getDecoder().decode(value), Charsets.UTF_8)
}
