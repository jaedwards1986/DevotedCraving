package com.natasha.local.projects

data class ProjectContextFile(val path: String, val content: String)

/**
 * Builds a bounded, text-only project snapshot for the local model.
 * Credential-like files and generated/dependency folders are never added to prompts.
 */
object ProjectContextBuilder {
    private const val DEFAULT_MAX_CHARS = 32_000
    private const val MAX_FILES = 12
    private val textExtensions = setOf(
        "kt", "kts", "java", "xml", "json", "md", "txt", "gradle", "properties",
        "toml", "yaml", "yml", "html", "css", "js", "jsx", "ts", "tsx", "py",
        "sh", "sql", "swift", "dart", "c", "h", "cpp", "hpp", "cmake"
    )
    private val blockedNames = setOf(
        ".env", "local.properties", "credentials.json", "secrets.json"
    )
    private val blockedExtensions = setOf("pem", "p12", "pfx", "keystore", "jks", "key")
    private val assignmentSecret = Regex(
        """(?im)^(\s*["']?(?:password|passwd|token|api[_-]?key|secret|client[_-]?secret|storePassword|keyPassword|access[_-]?key)["']?\s*[:=]\s*)[^\r\n]+"""
    )
    private val jsonSecret = Regex(
        """(?i)(["']?(?:password|passwd|token|api[_-]?key|secret|client[_-]?secret|access[_-]?key)["']?\s*:\s*["']?)[^"',\s}]+"""
    )
    private val recognizableToken = Regex(
        """\b(?:gh[pousr]_[A-Za-z0-9_]{20,}|github_pat_[A-Za-z0-9_]{20,}|sk-[A-Za-z0-9_-]{20,}|AIza[0-9A-Za-z_-]{30,})\b"""
    )
    private val privateKeyBlock = Regex(
        """-----BEGIN [A-Z ]*PRIVATE KEY-----[\s\S]*?-----END [A-Z ]*PRIVATE KEY-----"""
    )

    fun isEligiblePath(rawPath: String): Boolean {
        val path = rawPath.replace('\\', '/')
        if (path.isBlank() || path.startsWith("/") || path.contains('\u0000')) return false
        val parts = path.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." || it.startsWith(".") }) return false
        val name = parts.last().lowercase()
        if (name in blockedNames) return false
        if (listOf("secret", "credential", "password", "token", "private").any { name.contains(it) }) return false
        if (name.substringAfterLast('.', "").lowercase() in blockedExtensions) return false
        if (parts.any { it.lowercase() in setOf("build", "node_modules", ".gradle", ".git", "vendor") }) return false
        return name.substringAfterLast('.', "").lowercase() in textExtensions
    }

    fun redactSecrets(content: String): String {
        return content
            .replace(assignmentSecret) { it.groupValues[1] + "[REDACTED]" }
            .replace(jsonSecret) { it.groupValues[1] + "[REDACTED]" }
            .replace(recognizableToken, "[REDACTED TOKEN]")
            .replace(privateKeyBlock, "[PRIVATE KEY REDACTED]")
    }

    fun build(
        projectName: String,
        description: String,
        files: List<ProjectContextFile>,
        maxChars: Int = DEFAULT_MAX_CHARS
    ): String {
        require(maxChars >= 256) { "Project context limit must be at least 256 characters" }
        val output = StringBuilder()
        output.append("ACTIVE PROJECT: ").append(projectName.take(120)).append('\n')
        if (description.isNotBlank()) {
            output.append("PROJECT BRIEF: ").append(redactSecrets(description).take(2_000)).append('\n')
        }
        output.append("PROJECT FILES (read-only context; do not claim changes were saved unless a file tool confirms it):\n")
        var included = 0
        files.asSequence()
            .filter { isEligiblePath(it.path) }
            .distinctBy { it.path }
            .take(MAX_FILES)
            .forEach { file ->
                if (output.length >= maxChars) return@forEach
                val heading = "\n--- ${file.path} ---\n"
                val remaining = maxChars - output.length - heading.length
                if (remaining <= 0) return@forEach
                output.append(heading)
                output.append(redactSecrets(file.content).take(remaining))
                included++
            }
        if (included == 0) output.append("\nNo eligible source or documentation files are available yet.")
        if (output.length > maxChars) return output.substring(0, maxChars)
        return output.toString()
    }
}
