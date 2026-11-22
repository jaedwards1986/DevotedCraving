package com.natasha.local.projects

import android.content.Context

data class ProjectFileChange(val path: String, val content: String)
data class ProjectBuildPlan(val summary: String, val changes: List<ProjectFileChange>)

class ProjectBuildService(private val context: Context) {
    fun preview(projectId: Long, plan: ProjectBuildPlan): WorkspaceResult<List<String>> = runCatching {
        val validationError = ProjectBuildPlanValidator.validate(plan)
        require(validationError == null) { validationError ?: "Invalid build plan" }
        val workspace = ProjectWorkspace(context, projectId)
        val normalizedChanges = plan.changes.map { it.copy(path = it.path.trim().replace('\\', '/')) }
        normalizedChanges.forEach { change ->
            workspace.exists(change.path)
            require(change.content.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
                "File exceeds the 2 MB per-file limit: ${change.path}"
            }
        }
        require(plan.changes.sumOf { it.content.toByteArray(Charsets.UTF_8).size.toLong() } <= MAX_PLAN_BYTES) {
            "Build plan exceeds the 10 MB total limit"
        }
        normalizedChanges.map { it.path }
    }.fold(
        onSuccess = { WorkspaceResult(ok = true, value = it) },
        onFailure = { WorkspaceResult(false, error = it.message ?: "Invalid build plan") }
    )

    fun apply(projectId: Long, plan: ProjectBuildPlan): WorkspaceResult<List<String>> {
        val checked = preview(projectId, plan)
        if (!checked.ok) return WorkspaceResult(false, error = checked.error)
        val normalizedPlan = plan.copy(changes = plan.changes.map {
            it.copy(path = it.path.trim().replace('\\', '/'))
        })
        val workspace = ProjectWorkspace(context, projectId)
        val originals = linkedMapOf<String, String?>()
        return try {
            normalizedPlan.changes.forEach { change ->
                originals[change.path] = if (workspace.exists(change.path)) {
                    workspace.read(change.path, MAX_FILE_BYTES)
                } else null
            }
            normalizedPlan.changes.forEach { change -> workspace.write(change.path, change.content) }
            ProjectActivityStore(context, projectId).record(
                "BUILD_PLAN",
                normalizedPlan.changes.joinToString(",") { it.path },
                normalizedPlan.summary
            )
            WorkspaceResult(ok = true, value = normalizedPlan.changes.map { it.path })
        } catch (failure: Throwable) {
            val rollbackErrors = mutableListOf<String>()
            originals.entries.toList().asReversed().forEach { (path, original) ->
                runCatching {
                    if (original == null) {
                        if (workspace.exists(path)) workspace.delete(path)
                    } else {
                        workspace.write(path, original)
                    }
                }.onFailure { rollbackErrors += path }
            }
            val suffix = if (rollbackErrors.isEmpty()) "" else "; rollback incomplete for: " + rollbackErrors.joinToString()
            WorkspaceResult(false, error = (failure.message ?: "Unable to apply build plan") + suffix)
        }
    }

    companion object {
        private const val MAX_FILE_BYTES = 2 * 1024 * 1024
        private const val MAX_PLAN_BYTES = 10L * 1024 * 1024
    }
}
