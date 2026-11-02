package com.example.repofileexplorer.data

data class RepoTarget(
    val owner: String,
    val repo: String,
    val branch: String = "main",
    val initialPath: String = ""
)

data class RepoFileItem(
    val name: String,
    val path: String,
    val sha: String,
    val size: Long,
    val type: String, // "dir" or "file"
    val downloadUrl: String?,
    val htmlUrl: String
) {
    val isDirectory: Boolean get() = type == "dir"
    
    val extension: String
        get() {
            val dotIdx = name.lastIndexOf('.')
            return if (dotIdx != -1) name.substring(dotIdx + 1).lowercase() else ""
        }

    val formattedSize: String
        get() {
            if (isDirectory) return "Folder"
            if (size <= 0) return "0 B"
            val units = arrayOf("B", "KB", "MB", "GB")
            var s = size.toDouble()
            var idx = 0
            while (s >= 1024.0 && idx < units.size - 1) {
                s /= 1024.0
                idx++
            }
            return String.format("%.2f %s", s, units[idx])
        }
}

data class TreeItem(
    val path: String,
    val type: String, // "blob" or "tree"
    val sha: String,
    val size: Long
)

data class ZipProgress(
    val current: Int,
    val total: Int,
    val currentFile: String,
    val isZipping: Boolean = false
)

object RepoParser {
    const val DEFAULT_FIXED_REPO = "git@github.com:DHRUV7044/labfiles.git"

    /**
     * Parses SSH (git@github.com:owner/repo.git), HTTPS (https://github.com/owner/repo),
     * tree/branch/subpath links, and shorthand (owner/repo).
     */
    fun parse(input: String): RepoTarget? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // SSH format: git@github.com:owner/repo(.git)?
        val sshRegex = Regex("""^git@github\.com:([^/\s]+)/([^/\s\.]+)(?:\.git)?$""", RegexOption.IGNORE_CASE)
        sshRegex.find(trimmed)?.let { match ->
            return RepoTarget(
                owner = match.groupValues[1],
                repo = match.groupValues[2]
            )
        }

        // HTTPS format: https://github.com/owner/repo(/tree/branch(/subpath)?)?
        val httpsRegex = Regex(
            """^https?://github\.com/([^/\s]+)/([^/\s\.]+)(?:\.git)?(?:/tree/([^/\s]+)(?:/(.*))?)?$""",
            RegexOption.IGNORE_CASE
        )
        httpsRegex.find(trimmed)?.let { match ->
            val branch = match.groupValues.getOrNull(3)?.takeIf { it.isNotBlank() } ?: "main"
            val subpath = match.groupValues.getOrNull(4)?.trim('/') ?: ""
            return RepoTarget(
                owner = match.groupValues[1],
                repo = match.groupValues[2],
                branch = branch,
                initialPath = subpath
            )
        }

        // Short format: owner/repo
        val shortRegex = Regex("""^([^/\s]+)/([^/\s\.]+)$""")
        shortRegex.find(trimmed)?.let { match ->
            return RepoTarget(
                owner = match.groupValues[1],
                repo = match.groupValues[2]
            )
        }

        return null
    }
}
