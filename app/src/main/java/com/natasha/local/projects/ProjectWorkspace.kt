package com.natasha.local.projects

import android.content.Context
import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class ProjectWorkspace private constructor(
    private val root: File,
    val projectId: Long
) {
    constructor(context: Context, projectId: Long) :
        this(File(context.filesDir, "projects/$projectId/files"), projectId)

    internal constructor(rootDirectory: File) : this(rootDirectory, 0L)

    init {
        root.mkdirs()
    }

    fun list(relativeDir: String = ""): List<String> {
        val dir = resolveDirectory(relativeDir)
        if (!dir.exists()) return emptyList()
        require(dir.isDirectory) { "Not a project directory: $relativeDir" }
        return dir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root.canonicalFile).invariantSeparatorsPath }
            .sorted()
            .toList()
    }

    fun read(relativePath: String, maxBytes: Int = 2 * 1024 * 1024): String {
        require(maxBytes > 0) { "maxBytes must be positive" }
        val file = resolveFile(relativePath)
        require(file.exists() && file.isFile) { "File not found: $relativePath" }
        require(file.length() <= maxBytes) { "File is larger than the read limit" }
        return file.readText(StandardCharsets.UTF_8)
    }

    /** Writes through a same-directory temporary file so interrupted writes do not truncate a good file. */
    fun write(relativePath: String, content: String) {
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_WRITE_BYTES) { "File is larger than the 2 MB write limit" }
        val file = resolveFile(relativePath)
        file.parentFile?.mkdirs()
        val checkedFile = resolveFile(relativePath)
        val temp = File(checkedFile.parentFile, ".${checkedFile.name}.${System.nanoTime()}.tmp")
        try {
            temp.outputStream().buffered().use { it.write(bytes) }
            try {
                Files.move(temp.toPath(), checkedFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temp.toPath(), checkedFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        } finally {
            if (temp.exists()) temp.delete()
        }
    }

    fun exists(relativePath: String): Boolean = resolveFile(relativePath).exists()

    /** Renames or moves a file within the workspace. Existing destinations are never overwritten. */
    fun rename(sourcePath: String, destinationPath: String) {
        val source = resolveFile(sourcePath)
        require(source.exists() && source.isFile) { "File not found: $sourcePath" }
        val destination = resolveFile(destinationPath)
        require(!destination.exists()) { "A file already exists at: $destinationPath" }
        destination.parentFile?.mkdirs()
        val checkedDestination = resolveFile(destinationPath)
        require(!checkedDestination.exists()) { "A file already exists at: $destinationPath" }
        // Omit REPLACE_EXISTING and ATOMIC_MOVE here: the provider must fail if
        // another file appears at the destination after the preflight check.
        Files.move(source.toPath(), checkedDestination.toPath())
    }

    fun delete(relativePath: String) {
        val file = resolveFile(relativePath)
        require(file.exists()) { "File not found: $relativePath" }
        require(file.isFile) { "Only files can be deleted by this operation" }
        require(file.delete()) { "Unable to delete: $relativePath" }
    }

    companion object {
        private const val MAX_WRITE_BYTES = 2 * 1024 * 1024
    }

    private fun resolveDirectory(relativePath: String): File {
        if (relativePath.isBlank()) return root.canonicalFile
        return resolve(relativePath)
    }

    private fun resolveFile(relativePath: String): File = resolve(relativePath)

    private fun resolve(relativePath: String): File {
        require(relativePath.isNotBlank()) { "Path is required" }
        require(!relativePath.contains('\u0000')) { "Invalid path" }
        val normalized = relativePath.replace('\\', '/')
        require(!normalized.startsWith("/") && !Regex("^[A-Za-z]:").containsMatchIn(normalized)) {
            "Absolute paths are not allowed"
        }
        require(normalized.split('/').none { it.isBlank() || it == "." || it == ".." }) {
            "Invalid or escaping project path"
        }

        val rootCanonical = root.canonicalFile
        val candidate = File(rootCanonical, normalized).canonicalFile
        require(
            candidate != rootCanonical &&
                candidate.path.startsWith(rootCanonical.path + File.separator)
        ) { "Path escapes project workspace" }

        return candidate
    }
}
