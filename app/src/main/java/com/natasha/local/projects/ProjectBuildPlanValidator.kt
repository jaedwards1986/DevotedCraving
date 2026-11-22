package com.natasha.local.projects

object ProjectBuildPlanValidator {
    private const val MAX_CHANGES = 50
    private const val MAX_PATH_LENGTH = 240

    fun validate(plan: ProjectBuildPlan): String? {
        if (plan.summary.isBlank()) return "Build plan summary is required"
        if (plan.summary.length > 500) return "Build plan summary is too long"
        if (plan.changes.isEmpty()) return "Build plan contains no file changes"
        if (plan.changes.size > MAX_CHANGES) return "Build plan contains too many file changes"

        val paths = plan.changes.map { it.path.trim().replace('\\', '/') }
        if (paths.any { it.isBlank() }) return "File path is required"
        if (paths.any { it.length > MAX_PATH_LENGTH }) return "A file path is too long"
        if (paths.any { it.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(it) }) {
            return "Absolute file paths are not allowed"
        }
        if (paths.any { it.split('/').any { part -> part.isBlank() || part == "." || part == ".." } }) {
            return "Invalid or escaping file path"
        }
        if (paths.any { it.contains('\u0000') }) return "Invalid file path"
        if (paths.distinct().size != paths.size) return "Build plan contains duplicate file paths"
        return null
    }
}
