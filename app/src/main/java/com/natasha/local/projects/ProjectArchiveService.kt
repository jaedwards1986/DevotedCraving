package com.natasha.local.projects

import android.content.Context
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ProjectArchivePathValidator {
    fun validate(raw: String): String? {
        val path = raw.replace('\\', '/')
        if (path.isBlank()) return "Archive contains an empty path"
        if (path.startsWith("/") || Regex("^[A-Za-z]:").containsMatchIn(path)) return "Archive contains an absolute path"
        val parts = path.trimEnd('/').split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." }) return "Archive contains an unsafe path"
        if (parts.any { it.contains(':') || it.contains('\u0000') }) return "Archive contains an invalid path"
        return null
    }
}

class ProjectArchiveService(private val context: Context) {
    fun export(projectId: Long, output: OutputStream): WorkspaceResult<Int> = runCatching {
        val workspace = ProjectWorkspace(context, projectId)
        val root = workspaceRoot(projectId)
        val files = workspace.list()
        require(files.size <= MAX_FILES) { "Project has more than $MAX_FILES files; split it into smaller archives" }
        var count = 0
        var totalBytes = 0L
        val tempArchive = java.io.File.createTempFile("project-export-$projectId-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(tempArchive.outputStream().buffered()).use { zip ->
                files.forEach { path ->
                    val source = java.io.File(root, path).canonicalFile
                    require(source.path.startsWith(root.canonicalPath + java.io.File.separator))
                    require(source.isFile) { "Project file disappeared during export: $path" }
                    require(source.length() <= MAX_FILE_BYTES) { "Project file exceeds 2 MB: $path" }
                    zip.putNextEntry(ZipEntry(path))
                    source.inputStream().use { input ->
                        val buffer = ByteArray(32 * 1024)
                        var fileBytes = 0L
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            fileBytes += read
                            totalBytes += read
                            require(fileBytes <= MAX_FILE_BYTES) { "Project file exceeds 2 MB: $path" }
                            require(totalBytes <= MAX_TOTAL_BYTES) { "Project exceeds 50 MB; split it into smaller archives" }
                            zip.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                    count++
                }
                zip.finish()
            }
            tempArchive.inputStream().use { it.copyTo(output) }
            output.flush()
        } finally {
            tempArchive.delete()
        }
        runCatching { ProjectActivityStore(context, projectId).record("EXPORT_ARCHIVE", "", "$count files exported") }
        count
    }.fold(
        onSuccess = { WorkspaceResult(true, it) },
        onFailure = { WorkspaceResult(false, error = it.message ?: "Project export failed") }
    )

    fun import(projectId: Long, input: InputStream): WorkspaceResult<Int> = runCatching {
        val staging = java.io.File(context.cacheDir, "project-import-$projectId-${System.currentTimeMillis()}")
        require(staging.mkdirs()) { "Unable to create import staging area" }
        val payload = java.io.File(staging, "payload").apply { mkdirs() }
        var count = 0
        var totalBytes = 0L
        try {
            ZipInputStream(input.buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (entry.isDirectory) {
                        zip.closeEntry()
                        continue
                    }
                    val normalizedPath = entry.name.replace('\\', '/')
                    val invalid = ProjectArchivePathValidator.validate(normalizedPath)
                    require(invalid == null) { invalid ?: "Unsafe archive path" }
                    count++
                    require(count <= MAX_FILES) { "Archive contains too many files" }
                    val target = java.io.File(payload, normalizedPath).canonicalFile
                    require(target.path.startsWith(payload.canonicalPath + java.io.File.separator)) { "Archive path escapes staging area" }
                    require(!target.exists()) { "Archive contains duplicate file paths" }
                    target.parentFile?.mkdirs()
                    var fileBytes = 0L
                    target.outputStream().buffered().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            fileBytes += read
                            totalBytes += read
                            require(fileBytes <= MAX_FILE_BYTES) { "Archive file exceeds 2 MB" }
                            require(totalBytes <= MAX_TOTAL_BYTES) { "Archive exceeds 50 MB" }
                            output.write(buffer, 0, read)
                        }
                    }
                    zip.closeEntry()
                }
            }

            val root = workspaceRoot(projectId).apply { mkdirs() }
            val backupRoot = java.io.File(staging, "backup").apply { mkdirs() }
            val applied = mutableListOf<Pair<java.io.File, java.io.File?>>()
            try {
                payload.walkTopDown().filter { it.isFile }.forEach { stagedFile ->
                    val relative = stagedFile.relativeTo(payload).invariantSeparatorsPath
                    val destination = java.io.File(root, relative).canonicalFile
                    require(destination.path.startsWith(root.canonicalPath + java.io.File.separator)) {
                        "Archive destination escapes project workspace"
                    }
                    destination.parentFile?.mkdirs()
                    val backup = if (destination.exists()) {
                        val backupFile = java.io.File(backupRoot, relative)
                        backupFile.parentFile?.mkdirs()
                        destination.copyTo(backupFile, overwrite = true)
                        backupFile
                    } else null
                    applied += destination to backup
                    stagedFile.copyTo(destination, overwrite = true)
                }
            } catch (failure: Throwable) {
                applied.asReversed().forEach { (destination, backup) ->
                    runCatching {
                        if (backup == null) destination.delete()
                        else backup.copyTo(destination, overwrite = true)
                    }
                }
                throw failure
            }
            runCatching { ProjectActivityStore(context, projectId).record("IMPORT_ARCHIVE", "", "$count files restored") }
            count
        } finally {
            staging.deleteRecursively()
        }
    }.fold(
        onSuccess = { WorkspaceResult(true, it) },
        onFailure = { WorkspaceResult(false, error = it.message ?: "Project import failed") }
    )

    private fun workspaceRoot(projectId: Long) =
        java.io.File(context.filesDir, "projects/$projectId/files").canonicalFile

    companion object {
        private const val MAX_FILES = 1000
        private const val MAX_FILE_BYTES = 2L * 1024 * 1024
        private const val MAX_TOTAL_BYTES = 50L * 1024 * 1024
    }
}
