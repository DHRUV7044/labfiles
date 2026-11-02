package com.example.repofileexplorer.data

interface DataRepository {
    suspend fun getRepoDefaultBranch(owner: String, repo: String, token: String?): Result<String>
    suspend fun getBranches(owner: String, repo: String, token: String?): Result<List<String>>
    suspend fun getDirectoryContents(owner: String, repo: String, branch: String, path: String, token: String?): Result<List<RepoFileItem>>
    suspend fun getRecursiveTree(owner: String, repo: String, branch: String, token: String?): Result<List<TreeItem>>
    suspend fun fetchRawContent(rawUrl: String): Result<String>
}

class DefaultDataRepository(
    private val apiClient: GitHubApiClient = GitHubApiClient()
) : DataRepository {

    override suspend fun getRepoDefaultBranch(owner: String, repo: String, token: String?): Result<String> {
        return apiClient.getRepoDefaultBranch(owner, repo, token)
    }

    override suspend fun getBranches(owner: String, repo: String, token: String?): Result<List<String>> {
        return apiClient.getBranches(owner, repo, token)
    }

    override suspend fun getDirectoryContents(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        token: String?
    ): Result<List<RepoFileItem>> {
        return apiClient.getDirectoryContents(owner, repo, branch, path, token)
    }

    override suspend fun getRecursiveTree(
        owner: String,
        repo: String,
        branch: String,
        token: String?
    ): Result<List<TreeItem>> {
        return apiClient.getRecursiveTree(owner, repo, branch, token)
    }

    override suspend fun fetchRawContent(rawUrl: String): Result<String> {
        return apiClient.fetchRawContent(rawUrl)
    }
}
