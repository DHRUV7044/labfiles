package com.example.repofileexplorer.ui.main

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.repofileexplorer.data.DataRepository
import com.example.repofileexplorer.data.DefaultDataRepository
import com.example.repofileexplorer.data.RepoFileItem
import com.example.repofileexplorer.data.RepoParser
import com.example.repofileexplorer.data.RepoTarget
import com.example.repofileexplorer.data.ZipProgress
import com.example.repofileexplorer.util.DownloadHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ExplorerUiState(
    val repoInput: String = RepoParser.DEFAULT_FIXED_REPO,
    val token: String = "",
    val currentTarget: RepoTarget = RepoTarget(owner = "DHRUV7044", repo = "labfiles"),
    val currentBranch: String = "main",
    val availableBranches: List<String> = listOf("main"),
    val currentPath: String = "",
    val items: List<RepoFileItem> = emptyList(),
    val filteredItems: List<RepoFileItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val previewItem: RepoFileItem? = null,
    val previewContent: String? = null,
    val isPreviewLoading: Boolean = false,
    val zipProgress: ZipProgress? = null,
    val snackbarMessage: String? = null,
    val isRepoSwitcherOpen: Boolean = false
)

class MainScreenViewModel(
    private val repository: DataRepository = DefaultDataRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    private var zipJob: Job? = null

    init {
        // Initial load with default fixed repo: git@github.com:DHRUV7044/labfiles.git
        loadRepository(RepoParser.DEFAULT_FIXED_REPO)
    }

    fun onRepoInputChange(newInput: String) {
        _uiState.update { it.copy(repoInput = newInput) }
    }

    fun onTokenChange(newToken: String) {
        _uiState.update { it.copy(token = newToken) }
    }

    fun toggleRepoSwitcher(isOpen: Boolean? = null) {
        _uiState.update {
            it.copy(isRepoSwitcherOpen = isOpen ?: !it.isRepoSwitcherOpen)
        }
    }

    fun loadRepository(input: String) {
        val target = RepoParser.parse(input)
        if (target == null) {
            _uiState.update {
                it.copy(
                    errorMessage = "Invalid GitHub link. Supported formats:\n• git@github.com:owner/repo.git\n• https://github.com/owner/repo\n• owner/repo"
                )
            }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    repoInput = input,
                    currentTarget = target,
                    isLoading = true,
                    errorMessage = null,
                    currentPath = target.initialPath,
                    searchQuery = "",
                    isRepoSwitcherOpen = false
                )
            }

            val token = _uiState.value.token.takeIf { it.isNotBlank() }

            // 1. Fetch default branch
            val defaultBranchResult = repository.getRepoDefaultBranch(target.owner, target.repo, token)
            val branch = if (defaultBranchResult.isSuccess) {
                target.branch.ifBlank { defaultBranchResult.getOrThrow() }
            } else {
                target.branch.ifBlank { "main" }
            }

            // 2. Fetch available branches
            val branchesResult = repository.getBranches(target.owner, target.repo, token)
            val branchList = if (branchesResult.isSuccess && branchesResult.getOrThrow().isNotEmpty()) {
                branchesResult.getOrThrow()
            } else {
                listOf(branch)
            }

            _uiState.update {
                it.copy(
                    currentBranch = branch,
                    availableBranches = branchList
                )
            }

            // 3. Fetch initial directory contents
            fetchContents(target.owner, target.repo, branch, target.initialPath, token)
        }
    }

    fun changeBranch(newBranch: String) {
        if (_uiState.value.currentBranch == newBranch) return
        viewModelScope.launch {
            _uiState.update { it.copy(currentBranch = newBranch, isLoading = true, errorMessage = null) }
            val state = _uiState.value
            fetchContents(state.currentTarget.owner, state.currentTarget.repo, newBranch, state.currentPath, state.token.takeIf { it.isNotBlank() })
        }
    }

    fun navigateToFolder(folderPath: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentPath = folderPath,
                    isLoading = true,
                    errorMessage = null,
                    searchQuery = ""
                )
            }
            val state = _uiState.value
            fetchContents(state.currentTarget.owner, state.currentTarget.repo, state.currentBranch, folderPath, state.token.takeIf { it.isNotBlank() })
        }
    }

    fun navigateUp() {
        val current = _uiState.value.currentPath.trim('/')
        if (current.isEmpty()) return
        val lastSlash = current.lastIndexOf('/')
        val parentPath = if (lastSlash != -1) current.substring(0, lastSlash) else ""
        navigateToFolder(parentPath)
    }

    fun refreshCurrentDirectory() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val state = _uiState.value
            fetchContents(state.currentTarget.owner, state.currentTarget.repo, state.currentBranch, state.currentPath, state.token.takeIf { it.isNotBlank() })
        }
    }

    fun filterFiles(query: String) {
        _uiState.update { state ->
            val filtered = if (query.isBlank()) {
                state.items
            } else {
                state.items.filter { it.name.contains(query, ignoreCase = true) }
            }
            state.copy(searchQuery = query, filteredItems = filtered)
        }
    }

    fun openFilePreview(item: RepoFileItem) {
        val ext = item.extension
        val isText = ext in listOf("v", "vhd", "vhdl", "txt", "kt", "java", "py", "c", "cpp", "h", "json", "xml", "md", "csv", "sh")

        _uiState.update {
            it.copy(
                previewItem = item,
                previewContent = null,
                isPreviewLoading = isText
            )
        }

        if (isText && item.downloadUrl != null) {
            viewModelScope.launch {
                val result = repository.fetchRawContent(item.downloadUrl)
                _uiState.update {
                    it.copy(
                        previewContent = result.getOrNull() ?: "Unable to fetch content: ${result.exceptionOrNull()?.message}",
                        isPreviewLoading = false
                    )
                }
            }
        }
    }

    fun closePreview() {
        _uiState.update { it.copy(previewItem = null, previewContent = null, isPreviewLoading = false) }
    }

    fun downloadSingleFile(context: Context, item: RepoFileItem) {
        val url = item.downloadUrl ?: return
        try {
            DownloadHelper.downloadSingleFile(context, item.name, url)
            _uiState.update {
                it.copy(snackbarMessage = "Downloading ${item.name} via Download Manager")
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(snackbarMessage = "Download failed: ${e.message}")
            }
        }
    }

    fun downloadFolderAsZip(context: Context) {
        val state = _uiState.value
        zipJob?.cancel()

        _uiState.update {
            it.copy(
                zipProgress = ZipProgress(
                    current = 0,
                    total = 0,
                    currentFile = "Querying repository tree...",
                    isZipping = true
                )
            )
        }

        zipJob = viewModelScope.launch {
            val token = state.token.takeIf { it.isNotBlank() }
            val treeResult = repository.getRecursiveTree(
                state.currentTarget.owner,
                state.currentTarget.repo,
                state.currentBranch,
                token
            )

            if (treeResult.isFailure) {
                _uiState.update {
                    it.copy(
                        zipProgress = null,
                        snackbarMessage = "Failed to load repo tree: ${treeResult.exceptionOrNull()?.message}"
                    )
                }
                return@launch
            }

            val treeNodes = treeResult.getOrThrow()
            val zipResult = DownloadHelper.downloadFolderAsZip(
                context = context,
                owner = state.currentTarget.owner,
                repo = state.currentTarget.repo,
                branch = state.currentBranch,
                folderPath = state.currentPath,
                treeItems = treeNodes,
                token = token,
                onProgress = { current, total, filename ->
                    _uiState.update {
                        it.copy(
                            zipProgress = ZipProgress(
                                current = current,
                                total = total,
                                currentFile = filename,
                                isZipping = true
                            )
                        )
                    }
                }
            )

            _uiState.update {
                it.copy(
                    zipProgress = null,
                    snackbarMessage = if (zipResult.isSuccess) {
                        "ZIP archive saved to Downloads: ${zipResult.getOrThrow().name}"
                    } else {
                        "ZIP failed: ${zipResult.exceptionOrNull()?.message}"
                    }
                )
            }
        }
    }

    fun cancelZipDownload() {
        zipJob?.cancel()
        zipJob = null
        _uiState.update {
            it.copy(
                zipProgress = null,
                snackbarMessage = "ZIP archiving cancelled"
            )
        }
    }

    fun dismissSnackbar() {
        _uiState.update { it.copy(snackbarMessage = null) }
    }

    private suspend fun fetchContents(owner: String, repo: String, branch: String, path: String, token: String?) {
        val result = repository.getDirectoryContents(owner, repo, branch, path, token)
        if (result.isSuccess) {
            val items = result.getOrThrow()
            _uiState.update {
                it.copy(
                    items = items,
                    filteredItems = items,
                    isLoading = false,
                    errorMessage = null
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    items = emptyList(),
                    filteredItems = emptyList(),
                    isLoading = false,
                    errorMessage = result.exceptionOrNull()?.message ?: "Failed to fetch contents"
                )
            }
        }
    }
}
