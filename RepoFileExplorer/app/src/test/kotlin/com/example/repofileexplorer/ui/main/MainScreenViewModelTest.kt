package com.example.repofileexplorer.ui.main

import com.example.repofileexplorer.data.DataRepository
import com.example.repofileexplorer.data.RepoFileItem
import com.example.repofileexplorer.data.RepoParser
import com.example.repofileexplorer.data.TreeItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MainScreenViewModelTest {

    @Test
    fun testRepoParser_sshFormat() {
        val target = RepoParser.parse("git@github.com:DHRUV7044/labfiles.git")
        assertNotNull(target)
        assertEquals("DHRUV7044", target?.owner)
        assertEquals("labfiles", target?.repo)
    }

    @Test
    fun testRepoParser_httpsFormat() {
        val target = RepoParser.parse("https://github.com/DHRUV7044/labfiles")
        assertNotNull(target)
        assertEquals("DHRUV7044", target?.owner)
        assertEquals("labfiles", target?.repo)
    }

    @Test
    fun testRepoParser_treePathFormat() {
        val target = RepoParser.parse("https://github.com/DHRUV7044/labfiles/tree/main/sit_sem5")
        assertNotNull(target)
        assertEquals("DHRUV7044", target?.owner)
        assertEquals("labfiles", target?.repo)
        assertEquals("main", target?.branch)
        assertEquals("sit_sem5", target?.initialPath)
    }

    @Test
    fun testViewModel_initialStateHasDefaultFixedRepo() = runTest {
        val fakeRepo = FakeDataRepository()
        val viewModel = MainScreenViewModel(fakeRepo)

        val state = viewModel.uiState.first()
        assertEquals("git@github.com:DHRUV7044/labfiles.git", state.repoInput)
        assertEquals("DHRUV7044", state.currentTarget.owner)
        assertEquals("labfiles", state.currentTarget.repo)
    }

    @Test
    fun testViewModel_filterFiles() = runTest {
        val fakeRepo = FakeDataRepository()
        val viewModel = MainScreenViewModel(fakeRepo)

        viewModel.filterFiles("assignment")
        val state = viewModel.uiState.first()
        assertTrue(state.filteredItems.all { it.name.contains("assignment", ignoreCase = true) })
    }
}

private class FakeDataRepository : DataRepository {
    override suspend fun getRepoDefaultBranch(owner: String, repo: String, token: String?): Result<String> {
        return Result.success("main")
    }

    override suspend fun getBranches(owner: String, repo: String, token: String?): Result<List<String>> {
        return Result.success(listOf("main", "develop"))
    }

    override suspend fun getDirectoryContents(
        owner: String,
        repo: String,
        branch: String,
        path: String,
        token: String?
    ): Result<List<RepoFileItem>> {
        return Result.success(
            listOf(
                RepoFileItem(
                    name = "sit_sem5",
                    path = "sit_sem5",
                    sha = "123",
                    size = 0L,
                    type = "dir",
                    downloadUrl = null,
                    htmlUrl = "https://github.com/DHRUV7044/labfiles/tree/main/sit_sem5"
                ),
                RepoFileItem(
                    name = "sit_assignment6.pdf",
                    path = "sit_sem5/Dhruv_U24EV057/sit_assignment6.pdf",
                    sha = "456",
                    size = 2085571L,
                    type = "file",
                    downloadUrl = "https://raw.githubusercontent.com/DHRUV7044/labfiles/main/sit_sem5/Dhruv_U24EV057/sit_assignment6.pdf",
                    htmlUrl = "https://github.com/DHRUV7044/labfiles/blob/main/sit_sem5/Dhruv_U24EV057/sit_assignment6.pdf"
                )
            )
        )
    }

    override suspend fun getRecursiveTree(owner: String, repo: String, branch: String, token: String?): Result<List<TreeItem>> {
        return Result.success(emptyList())
    }

    override suspend fun fetchRawContent(rawUrl: String): Result<String> {
        return Result.success("// Fake raw content")
    }
}
