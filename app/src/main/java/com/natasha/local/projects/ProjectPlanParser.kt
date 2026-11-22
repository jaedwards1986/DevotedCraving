package com.natasha.local.projects

import org.json.JSONArray
import org.json.JSONObject

/**
 * Parses an explicit, bounded file-change proposal from the local model.
 * The parser never executes commands; callers must show a preview and obtain
 * a deliberate user approval before applying the resulting plan.
 */
object ProjectPlanParser {
    private const val OPEN = "<forgeai_plan>"
    private const val CLOSE = "</forgeai_plan>"
    private const val MAX_JSON_CHARS = 10 * 1024 * 1024

    fun parse(response: String): ProjectBuildPlan? = runCatching {
        val start = response.indexOf(OPEN)
        if (start < 0) return null
        val bodyStart = start + OPEN.length
        val end = response.indexOf(CLOSE, bodyStart)
        require(end >= bodyStart) { "Project plan block is incomplete" }
        val json = response.substring(bodyStart, end).trim()
        require(json.length <= MAX_JSON_CHARS) { "Project plan is too large" }

        val root = JSONObject(json)
        val summary = root.optString("summary").trim()
        val rawChanges = root.optJSONArray("changes") ?: JSONArray()
        require(rawChanges.length() in 1..50) { "Project plan must contain 1 to 50 file changes" }

        val changes = (0 until rawChanges.length()).map { index ->
            val item = rawChanges.optJSONObject(index)
                ?: error("Project plan change $index is not an object")
            require(item.has("path") && item.has("content")) { "Each project change needs a path and content" }
            ProjectFileChange(item.getString("path"), item.getString("content"))
        }
        val plan = ProjectBuildPlan(summary, changes)
        val validationError = ProjectBuildPlanValidator.validate(plan)
        require(validationError == null) { validationError ?: "Invalid project plan" }
        require(changes.all { it.content.toByteArray(Charsets.UTF_8).size <= 2 * 1024 * 1024 }) {
            "A project file exceeds the 2 MB limit"
        }
        require(changes.sumOf { it.content.toByteArray(Charsets.UTF_8).size.toLong() } <= 10L * 1024 * 1024) {
            "Project plan exceeds the 10 MB total limit"
        }
        plan
    }.getOrNull()
}
