package com.natasha.local.projects

import android.content.Context
import java.io.File

data class WorkspaceFile(
    val path: String,
    val sizeBytes: Long
)

data class WorkspaceResult<T>(
    val ok: Boolean,
    val value: T? = null,
    val error: String? = null
)

/**
 * Safe project-file operations for Natasha's build/design tools.
 *
 * The service never executes shell commands and never writes outside the
 * app-private project workspace. Callers must explicitly request deletes.
 */
class ProjectToolService(private val context: Context) {

    fun listFiles(projectId: Long): WorkspaceResult<List<WorkspaceFile>> = runCatching {
        val workspace = ProjectWorkspace(context, projectId)
        WorkspaceResult(
            ok = true,
            value = workspace.list().map { path ->
                WorkspaceFile(path, File(context.filesDir, "projects/$projectId/files/$path").length())
            }
        )
    }.getOrElse { WorkspaceResult(false, error = it.message ?: "Unable to list project files") }

    fun readFile(projectId: Long, relativePath: String): WorkspaceResult<String> = runCatching {
        WorkspaceResult(ok = true, value = ProjectWorkspace(context, projectId).read(relativePath))
    }.getOrElse { WorkspaceResult(false, error = it.message ?: "Unable to read project file") }

    fun writeFile(projectId: Long, relativePath: String, content: String): WorkspaceResult<Unit> = runCatching {
        val workspace = ProjectWorkspace(context, projectId)
        val action = if (workspace.exists(relativePath)) "UPDATE" else "CREATE"
        workspace.write(relativePath, content)
        runCatching { ProjectActivityStore(context, projectId).record(action, relativePath) }
        WorkspaceResult(ok = true, value = Unit)
    }.getOrElse { WorkspaceResult(false, error = it.message ?: "Unable to write project file") }

    fun deleteFile(projectId: Long, relativePath: String): WorkspaceResult<Unit> = runCatching {
        ProjectWorkspace(context, projectId).delete(relativePath)
        runCatching { ProjectActivityStore(context, projectId).record("DELETE", relativePath) }
        WorkspaceResult(ok = true, value = Unit)
    }.getOrElse { WorkspaceResult(false, error = it.message ?: "Unable to delete project file") }

    fun renameFile(projectId: Long, sourcePath: String, destinationPath: String): WorkspaceResult<Unit> = runCatching {
        ProjectWorkspace(context, projectId).rename(sourcePath, destinationPath)
        runCatching {
            ProjectActivityStore(context, projectId).record("RENAME", sourcePath, "Moved to $destinationPath")
        }
        WorkspaceResult(ok = true, value = Unit)
    }.getOrElse { WorkspaceResult(false, error = it.message ?: "Unable to rename project file") }

    fun exists(projectId: Long, relativePath: String): WorkspaceResult<Boolean> = runCatching {
        WorkspaceResult(ok = true, value = ProjectWorkspace(context, projectId).exists(relativePath))
    }.getOrElse { WorkspaceResult(false, error = it.message ?: "Unable to check project file") }

    fun applyBuildPlan(projectId: Long, plan: ProjectBuildPlan): WorkspaceResult<List<String>> {
        return ProjectBuildService(context).apply(projectId, plan)
    }

    fun previewBuildPlan(projectId: Long, plan: ProjectBuildPlan): WorkspaceResult<List<String>> {
        return ProjectBuildService(context).preview(projectId, plan)
    }

    fun projectRoot(projectId: Long): File =
        File(context.filesDir, "projects/$projectId/files").canonicalFile

    fun exportProject(projectId: Long, destination: File): WorkspaceResult<File> = runCatching {
        val root = ProjectWorkspace(context, projectId)
        require(destination.exists() || destination.mkdirs()) { "Unable to create export directory" }
        root.list().forEach { relative ->
            val source = File(rootRoot(projectId), relative).canonicalFile
            val target = File(destination, relative).canonicalFile
            require(source.path.startsWith(rootRoot(projectId).path + File.separator)) { "Invalid project file" }
            require(target.path.startsWith(destination.canonicalFile.path + File.separator)) { "Invalid export path" }
            target.parentFile?.mkdirs()
            source.copyTo(target, overwrite = true)
        }
        WorkspaceResult(ok = true, value = destination)
    }.getOrElse { WorkspaceResult(false, error = it.message ?: "Unable to export project") }

    private fun rootRoot(projectId: Long): File =
        File(context.filesDir, "projects/$projectId/files").canonicalFile
}
