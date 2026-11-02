package com.example.repofileexplorer.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class GitHubApiClient {

    suspend fun getRepoDefaultBranch(owner: String, repo: String, token: String? = null): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$owner/$repo"
                val json = fetchString(url, token)
                val obj = JSONObject(json)
                val defaultBranch = obj.optString("default_branch", "main")
                Result.success(defaultBranch)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getBranches(owner: String, repo: String, token: String? = null): Result<List<String>> =
        withContext(Dispatchers.IO) {
            try {
                val url = "https://api.github.com/repos/$owner/$repo/branches?per_page=50"
                val json = fetchString(url, token)
                val arr = JSONArray(json)
                val branches = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val branchObj = arr.getJSONObject(i)
                    branches.add(branchObj.getString("name"))
                }
                Result.success(branches)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun getDirectoryContents(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        token: String? = null
    ): Result<List<RepoFileItem>> = withContext(Dispatchers.IO) {
        try {
            val cleanPath = path.trim('/')
            val endpoint = if (cleanPath.isEmpty()) {
                "https://api.github.com/repos/$owner/$repo/contents?ref=$branch"
            } else {
                "https://api.github.com/repos/$owner/$repo/contents/$cleanPath?ref=$branch"
            }

            val json = fetchString(endpoint, token)
            val items = mutableListOf<RepoFileItem>()

            if (json.trim().startsWith("[")) {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    items.add(parseFileItem(obj))
                }
            } else {
                val obj = JSONObject(json)
                items.add(parseFileItem(obj))
            }

            // Sort: Directories first (A-Z), then Files (A-Z)
            items.sortWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))

            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getRecursiveTree(
        owner: String,
        repo: String,
        branch: String,
        token: String? = null
    ): Result<List<TreeItem>> = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1"
            val json = fetchString(url, token)
            val obj = JSONObject(json)
            val treeArr = obj.getJSONArray("tree")
            val items = mutableListOf<TreeItem>()

            for (i in 0 until treeArr.length()) {
                val node = treeArr.getJSONObject(i)
                items.add(
                    TreeItem(
                        path = node.getString("path"),
                        type = node.getString("type"),
                        sha = node.optString("sha", ""),
                        size = node.optLong("size", 0L)
                    )
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun fetchRawContent(rawUrl: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val url = URL(rawUrl)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 20000
            }
            if (conn.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP error ${conn.responseCode}: ${conn.responseMessage}"))
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            Result.success(text)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseFileItem(obj: JSONObject): RepoFileItem {
        return RepoFileItem(
            name = obj.getString("name"),
            path = obj.getString("path"),
            sha = obj.optString("sha", ""),
            size = obj.optLong("size", 0L),
            type = obj.getString("type"),
            downloadUrl = obj.optString("download_url").takeIf { it.isNotBlank() && it != "null" },
            htmlUrl = obj.optString("html_url", "")
        )
    }

    private fun fetchString(urlString: String, token: String?): String {
        val url = URL(urlString)
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15000
            readTimeout = 20000
            setRequestProperty("Accept", "application/vnd.github.v3+json")
            setRequestProperty("User-Agent", "RepoFileExplorer-Android")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
        }

        val code = conn.responseCode
        if (code in 200..299) {
            return BufferedReader(InputStreamReader(conn.inputStream)).use { it.readText() }
        } else {
            val errStream = conn.errorStream ?: conn.inputStream
            val errBody = errStream?.bufferedReader()?.use { it.readText() } ?: ""
            val message = try {
                JSONObject(errBody).optString("message", conn.responseMessage)
            } catch (e: Exception) {
                conn.responseMessage
            }
            if (code == 403 && message.contains("rate limit", ignoreCase = true)) {
                throw Exception("GitHub API rate limit exceeded. Add a GitHub Personal Access Token in settings to get 5,000 requests/hr.")
            } else if (code == 404) {
                throw Exception("Repository or path not found on GitHub. Verify repo name and permissions.")
            }
            throw Exception("GitHub API error ($code): $message")
        }
    }
}
