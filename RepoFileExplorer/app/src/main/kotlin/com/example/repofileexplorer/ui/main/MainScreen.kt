package com.example.repofileexplorer.ui.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.NavKey
import com.example.repofileexplorer.data.RepoFileItem
import com.example.repofileexplorer.data.RepoParser

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    onItemClick: (NavKey) -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: MainScreenViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "RepoFile Explorer",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${uiState.currentTarget.owner}/${uiState.currentTarget.repo}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.toggleRepoSwitcher() }) {
                        Icon(
                            imageVector = if (uiState.isRepoSwitcherOpen) Icons.Default.Close else Icons.Default.Edit,
                            contentDescription = "Switch Repository"
                        )
                    }
                    IconButton(onClick = { viewModel.refreshCurrentDirectory() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Collapsible Repository Switcher Card
            AnimatedVisibility(
                visible = uiState.isRepoSwitcherOpen,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                RepoSwitcherCard(
                    repoInput = uiState.repoInput,
                    token = uiState.token,
                    onRepoInputChange = { viewModel.onRepoInputChange(it) },
                    onTokenChange = { viewModel.onTokenChange(it) },
                    onExplore = { viewModel.loadRepository(uiState.repoInput) },
                    onLoadFixed = {
                        viewModel.onRepoInputChange(RepoParser.DEFAULT_FIXED_REPO)
                        viewModel.loadRepository(RepoParser.DEFAULT_FIXED_REPO)
                    }
                )
            }

            // Breadcrumbs Navigation Bar
            BreadcrumbBar(
                repoName = uiState.currentTarget.repo,
                currentPath = uiState.currentPath,
                onCrumbClick = { path -> viewModel.navigateToFolder(path) }
            )

            // Toolbar: Search Filter, Branch Selector, ZIP Download
            ToolbarRow(
                searchQuery = uiState.searchQuery,
                onSearchChange = { viewModel.filterFiles(it) },
                currentBranch = uiState.currentBranch,
                availableBranches = uiState.availableBranches,
                onBranchSelected = { viewModel.changeBranch(it) },
                onZipFolder = { viewModel.downloadFolderAsZip(context) }
            )

            // Meta Info Bar
            MetaBar(
                items = uiState.filteredItems,
                currentPath = uiState.currentPath
            )

            // Content Area
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
            ) {
                when {
                    uiState.isLoading -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    text = "Fetching directory contents...",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }

                    uiState.errorMessage != null -> {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .align(Alignment.Center),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    text = "Error Loading Repository",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = uiState.errorMessage ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Button(onClick = { viewModel.refreshCurrentDirectory() }) {
                                    Text("Retry")
                                }
                            }
                        }
                    }

                    uiState.filteredItems.isEmpty() && uiState.currentPath.isEmpty() -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Directory is empty.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    else -> {
                        FileList(
                            items = uiState.filteredItems,
                            currentPath = uiState.currentPath,
                            onFolderClick = { path -> viewModel.navigateToFolder(path) },
                            onGoUpClick = { viewModel.navigateUp() },
                            onPreviewClick = { item -> viewModel.openFilePreview(item) },
                            onDownloadClick = { item -> viewModel.downloadSingleFile(context, item) },
                            onCopyLinkClick = { url ->
                                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                cm.setPrimaryClip(ClipData.newPlainText("Download Link", url))
                                viewModel.loadRepository(uiState.repoInput) // refresh
                            }
                        )
                    }
                }
            }
        }
    }

    // In-App File Preview Dialog
    uiState.previewItem?.let { item ->
        FilePreviewDialog(
            item = item,
            content = uiState.previewContent,
            isLoading = uiState.isPreviewLoading,
            onDismiss = { viewModel.closePreview() },
            onDownload = { viewModel.downloadSingleFile(context, item) },
            onOpenExternal = {
                item.downloadUrl?.let { url ->
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(intent)
                }
            }
        )
    }

    // Folder ZIP Download Progress Dialog
    uiState.zipProgress?.let { progress ->
        ZipProgressDialog(
            progress = progress,
            onCancel = { viewModel.cancelZipDownload() }
        )
    }
}

@Composable
fun RepoSwitcherCard(
    repoInput: String,
    token: String,
    onRepoInputChange: (String) -> Unit,
    onTokenChange: (String) -> Unit,
    onExplore: () -> Unit,
    onLoadFixed: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Target Repository",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))

            OutlinedTextField(
                value = repoInput,
                onValueChange = onRepoInputChange,
                label = { Text("Repository Link (SSH, HTTPS, or owner/repo)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Fixed preset chip
                SuggestionChip(
                    onClick = onLoadFixed,
                    label = { Text("DHRUV7044/labfiles", fontSize = 12.sp) },
                    icon = { Icon(Icons.Default.Bookmark, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )

                Button(
                    onClick = onExplore,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Explore")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Token input for higher rate limits or private repos
            OutlinedTextField(
                value = token,
                onValueChange = onTokenChange,
                label = { Text("GitHub Token (Optional - for 5,000 reqs/hr)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun BreadcrumbBar(
    repoName: String,
    currentPath: String,
    onCrumbClick: (String) -> Unit
) {
    val scrollState = rememberScrollState()
    val parts = currentPath.split('/').filter { it.isNotBlank() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .horizontalScroll(scrollState)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Root Crumb
        AssistChip(
            onClick = { onCrumbClick("") },
            label = { Text(repoName, fontWeight = if (parts.isEmpty()) FontWeight.Bold else FontWeight.Normal) },
            leadingIcon = { Icon(Icons.Default.Home, contentDescription = null, modifier = Modifier.size(16.dp)) }
        )

        var accumulatedPath = ""
        parts.forEachIndexed { index, part ->
            accumulatedPath += (if (index > 0) "/" else "") + part
            val targetPath = accumulatedPath
            val isLast = index == parts.size - 1

            Text(
                text = " / ",
                color = MaterialTheme.colorScheme.outline,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 2.dp)
            )

            AssistChip(
                onClick = { onCrumbClick(targetPath) },
                label = { Text(part, fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal) }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolbarRow(
    searchQuery: String,
    onSearchChange: (String) -> Unit,
    currentBranch: String,
    availableBranches: List<String>,
    onBranchSelected: (String) -> Unit,
    onZipFolder: () -> Unit
) {
    var branchMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search Filter
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            placeholder = { Text("Filter files...", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchChange("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            modifier = Modifier.weight(1f)
        )

        // Branch Selector Dropdown
        Box {
            OutlinedButton(
                onClick = { branchMenuExpanded = true },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
            ) {
                Icon(Icons.AutoMirrored.Filled.CallSplit, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(currentBranch, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 12.sp)
            }
            DropdownMenu(
                expanded = branchMenuExpanded,
                onDismissRequest = { branchMenuExpanded = false }
            ) {
                availableBranches.forEach { branch ->
                    DropdownMenuItem(
                        text = { Text(branch) },
                        onClick = {
                            onBranchSelected(branch)
                            branchMenuExpanded = false
                        }
                    )
                }
            }
        }

        // Folder ZIP Download Button
        FilledTonalButton(
            onClick = onZipFolder,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("ZIP", fontSize = 12.sp)
        }
    }
}

@Composable
fun MetaBar(items: List<RepoFileItem>, currentPath: String) {
    val folders = items.count { it.isDirectory }
    val files = items.count { !it.isDirectory }
    val totalBytes = items.filter { !it.isDirectory }.sumOf { it.size }

    val formattedSize = if (totalBytes <= 0) "0 B" else {
        val units = arrayOf("B", "KB", "MB", "GB")
        var s = totalBytes.toDouble()
        var idx = 0
        while (s >= 1024.0 && idx < units.size - 1) {
            s /= 1024.0
            idx++
        }
        String.format("%.2f %s", s, units[idx])
    }

    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = if (currentPath.isEmpty()) "Path: / (root)" else "Path: /$currentPath",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "$folders folders • $files files ($formattedSize)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun FileList(
    items: List<RepoFileItem>,
    currentPath: String,
    onFolderClick: (String) -> Unit,
    onGoUpClick: () -> Unit,
    onPreviewClick: (RepoFileItem) -> Unit,
    onDownloadClick: (RepoFileItem) -> Unit,
    onCopyLinkClick: (String) -> Unit
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // ".." Go Up One Level item
        if (currentPath.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .clickable { onGoUpClick() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Go up",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = ".. (Go Up One Level)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        items(items, key = { it.path }) { item ->
            if (item.isDirectory) {
                FolderItemRow(item = item, onClick = { onFolderClick(item.path) })
            } else {
                FileItemRow(
                    item = item,
                    onPreview = { onPreviewClick(item) },
                    onDownload = { onDownloadClick(item) },
                    onCopyLink = { item.downloadUrl?.let { onCopyLinkClick(it) } }
                )
            }
        }
    }
}

@Composable
fun FolderItemRow(item: RepoFileItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Folder,
                    contentDescription = "Folder",
                    tint = Color(0xFFD97706),
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "Folder",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(onClick = onClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = "Open Folder",
                    tint = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun FileItemRow(
    item: RepoFileItem,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
    onCopyLink: () -> Unit
) {
    val iconInfo = getFileIconInfo(item.extension)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(iconInfo.backgroundColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = iconInfo.icon,
                    contentDescription = item.extension,
                    tint = iconInfo.tintColor,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onPreview() }
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.extension.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = item.extension.uppercase(),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                    }
                    Text(
                        text = item.formattedSize,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Quick Actions
            IconButton(onClick = onPreview) {
                Icon(Icons.Default.Visibility, contentDescription = "Preview", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDownload) {
                Icon(Icons.Default.Download, contentDescription = "Download", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onCopyLink) {
                Icon(Icons.Default.ContentCopy, contentDescription = "Copy Link", tint = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
fun FilePreviewDialog(
    item: RepoFileItem,
    content: String?,
    isLoading: Boolean,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
    onOpenExternal: () -> Unit
) {
    val isPdf = item.extension.equals("pdf", ignoreCase = true)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.formattedSize} • ${item.extension.uppercase()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                // Content View
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp)
                ) {
                    when {
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator()
                            }
                        }

                        isPdf -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color(0xFFEF4444),
                                    modifier = Modifier.size(64.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "PDF Document Preview",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "Open directly in your device's PDF viewer or download to storage.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(20.dp))
                                Button(onClick = onOpenExternal) {
                                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open in PDF Reader")
                                }
                            }
                        }

                        content != null -> {
                            SelectionContainer {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .background(Color(0xFF1E1E1E), shape = RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = content,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = Color(0xFFD4D4D4)
                                    )
                                }
                            }
                        }

                        else -> {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(48.dp))
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Ready to Download", style = MaterialTheme.typography.titleMedium)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(item.formattedSize, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                // Footer
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onDownload) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Download")
                    }
                }
            }
        }
    }
}

@Composable
fun ZipProgressDialog(
    progress: com.example.repofileexplorer.data.ZipProgress,
    onCancel: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Archive,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Creating Folder ZIP",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                val percent = if (progress.total > 0) (progress.current.toFloat() / progress.total) else 0f
                LinearProgressIndicator(
                    progress = { percent },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (progress.total > 0) "${progress.current} / ${progress.total} files" else "Scanning...",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = progress.currentFile,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(onClick = onCancel) {
                    Text("Cancel")
                }
            }
        }
    }
}

data class FileIconInfo(
    val icon: ImageVector,
    val tintColor: Color,
    val backgroundColor: Color
)

fun getFileIconInfo(ext: String): FileIconInfo {
    return when (ext.lowercase()) {
        "pdf" -> FileIconInfo(
            icon = Icons.Default.PictureAsPdf,
            tintColor = Color(0xFFDC2626),
            backgroundColor = Color(0xFFFEE2E2)
        )
        "v", "vhd", "vhdl", "sv", "kt", "java", "py", "c", "cpp", "h", "js", "ts", "json", "xml", "html", "css" -> FileIconInfo(
            icon = Icons.Default.Code,
            tintColor = Color(0xFF059669),
            backgroundColor = Color(0xFFD1FAE5)
        )
        "png", "jpg", "jpeg", "gif", "svg", "webp" -> FileIconInfo(
            icon = Icons.Default.Image,
            tintColor = Color(0xFF7C3AED),
            backgroundColor = Color(0xFFEDE9FE)
        )
        "zip", "tar", "gz", "7z", "rar" -> FileIconInfo(
            icon = Icons.Default.Archive,
            tintColor = Color(0xFF0284C7),
            backgroundColor = Color(0xFFE0F2FE)
        )
        else -> FileIconInfo(
            icon = Icons.AutoMirrored.Filled.InsertDriveFile,
            tintColor = Color(0xFF64748B),
            backgroundColor = Color(0xFFF1F5F9)
        )
    }
}
