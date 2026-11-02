/**
 * RepoFile Explorer & Downloader
 * Interactive GitHub Repository Browser, File Previewer & Folder ZIP Downloader
 */

(function () {
  'use strict';

  // App State
  const state = {
    owner: 'DHRUV7044',
    repo: 'labfiles',
    branch: 'main',
    currentPath: '',
    items: [],
    filteredItems: [],
    branches: ['main'],
    token: localStorage.getItem('repofile_gh_token') || '',
    theme: localStorage.getItem('repofile_theme') || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light'),
    activeZipAbortController: null
  };

  // DOM Elements
  const elements = {
    themeToggleBtn: document.getElementById('themeToggleBtn'),
    themeIcon: document.getElementById('themeIcon'),
    externalRepoLink: document.getElementById('externalRepoLink'),
    repoForm: document.getElementById('repoForm'),
    repoInput: document.getElementById('repoInput'),
    exploreBtn: document.getElementById('exploreBtn'),
    tokenToggleBtn: document.getElementById('tokenToggleBtn'),
    tokenBox: document.getElementById('tokenBox'),
    tokenInput: document.getElementById('tokenInput'),
    tokenChevron: document.getElementById('tokenChevron'),
    breadcrumbsNav: document.getElementById('breadcrumbsNav'),
    filterInput: document.getElementById('filterInput'),
    branchSelect: document.getElementById('branchSelect'),
    downloadZipBtn: document.getElementById('downloadZipBtn'),
    currentPathText: document.getElementById('currentPathText'),
    fileCountText: document.getElementById('fileCountText'),
    fileList: document.getElementById('fileList'),
    loadingState: document.getElementById('loadingState'),
    emptyState: document.getElementById('emptyState'),
    emptyTitle: document.getElementById('emptyTitle'),
    emptyMessage: document.getElementById('emptyMessage'),
    previewModal: document.getElementById('previewModal'),
    previewTitle: document.getElementById('previewTitle'),
    previewSizeBadge: document.getElementById('previewSizeBadge'),
    previewIcon: document.getElementById('previewIcon'),
    previewDownloadBtn: document.getElementById('previewDownloadBtn'),
    previewBody: document.getElementById('previewBody'),
    zipModal: document.getElementById('zipModal'),
    zipStatusTitle: document.getElementById('zipStatusTitle'),
    zipStatusDetail: document.getElementById('zipStatusDetail'),
    zipProgressBar: document.getElementById('zipProgressBar'),
    zipProgressPercent: document.getElementById('zipProgressPercent'),
    toast: document.getElementById('toast'),
    toastIcon: document.getElementById('toastIcon'),
    toastMessage: document.getElementById('toastMessage')
  };

  // Initialize
  function init() {
    applyTheme(state.theme);
    if (state.token) {
      elements.tokenInput.value = state.token;
    }

    // Initialize Lucide icons
    if (window.lucide) {
      window.lucide.createIcons();
    }

    // Set up theme toggle listener
    elements.themeToggleBtn.addEventListener('click', toggleTheme);

    // Initial Load of default repository (git@github.com:DHRUV7044/labfiles.git)
    handleLoadRepo();
  }

  // Theme Management
  function applyTheme(theme) {
    state.theme = theme;
    document.documentElement.setAttribute('data-theme', theme);
    localStorage.setItem('repofile_theme', theme);
    if (elements.themeIcon) {
      elements.themeIcon.setAttribute('data-lucide', theme === 'dark' ? 'sun' : 'moon');
      if (window.lucide) window.lucide.createIcons();
    }
  }

  function toggleTheme() {
    const nextTheme = state.theme === 'dark' ? 'light' : 'dark';
    applyTheme(nextTheme);
  }

  // GitHub Token Box
  function toggleTokenBox() {
    const isOpen = elements.tokenBox.classList.contains('open');
    if (isOpen) {
      elements.tokenBox.classList.remove('open');
      elements.tokenChevron.setAttribute('data-lucide', 'chevron-down');
    } else {
      elements.tokenBox.classList.add('open');
      elements.tokenChevron.setAttribute('data-lucide', 'chevron-up');
    }
    if (window.lucide) window.lucide.createIcons();
  }

  function saveToken() {
    const token = elements.tokenInput.value.trim();
    state.token = token;
    if (token) {
      localStorage.setItem('repofile_gh_token', token);
      showToast('GitHub token saved securely in browser!', 'check-circle');
    } else {
      localStorage.removeItem('repofile_gh_token');
      showToast('GitHub token removed.', 'info');
    }
    // Reload current path with new authentication headers
    fetchDirectoryContents(state.currentPath);
  }

  // Parse any GitHub repo URL format (SSH, HTTPS, or short)
  function parseRepoInput(input) {
    input = input.trim();
    if (!input) return null;

    // SSH format: git@github.com:owner/repo(.git)?
    const sshMatch = input.match(/^git@github\.com:([^\/\s]+)\/([^\/\s\.]+)(?:\.git)?$/i);
    if (sshMatch) {
      return { owner: sshMatch[1], repo: sshMatch[2], branch: null, path: '' };
    }

    // HTTPS format with optional /tree/branch/path
    const httpsMatch = input.match(/^https?:\/\/github\.com\/([^\/\s]+)\/([^\/\s\.]+)(?:\.git)?(?:\/tree\/([^\/\s]+)(?:\/(.*))?)?$/i);
    if (httpsMatch) {
      return {
        owner: httpsMatch[1],
        repo: httpsMatch[2],
        branch: httpsMatch[3] || null,
        path: (httpsMatch[4] || '').replace(/\/+$/, '')
      };
    }

    // Short format: owner/repo
    const shortMatch = input.match(/^([^\/\s]+)\/([^\/\s\.]+)$/);
    if (shortMatch) {
      return { owner: shortMatch[1], repo: shortMatch[2], branch: null, path: '' };
    }

    return null;
  }

  // GitHub API Fetch Helper with Token Support
  async function apiFetch(endpoint) {
    const headers = {
      'Accept': 'application/vnd.github.v3+json'
    };
    if (state.token) {
      headers['Authorization'] = `Bearer ${state.token}`;
    }

    const response = await fetch(endpoint, { headers });
    if (!response.ok) {
      const errorData = await response.json().catch(() => ({}));
      let msg = errorData.message || response.statusText;
      if (response.status === 403 && msg.includes('API rate limit')) {
        msg = 'GitHub API rate limit exceeded. Please add your GitHub Personal Access Token in the top drawer.';
      } else if (response.status === 404) {
        msg = 'Repository or directory path was not found on GitHub.';
      }
      throw new Error(msg);
    }
    return response.json();
  }

  // Main Handler: Load Repository from Input
  async function handleLoadRepo() {
    const rawInput = elements.repoInput.value;
    const parsed = parseRepoInput(rawInput);

    if (!parsed) {
      showToast('Invalid GitHub URL or format. Use git@github.com:owner/repo.git or https://github.com/owner/repo', 'alert-triangle');
      return;
    }

    state.owner = parsed.owner;
    state.repo = parsed.repo;
    state.currentPath = parsed.path || '';

    // Update external link
    elements.externalRepoLink.href = `https://github.com/${state.owner}/${state.repo}`;

    showLoading(true);
    elements.emptyState.style.display = 'none';
    elements.fileList.innerHTML = '';

    try {
      // 1. Fetch repo metadata to get default branch
      const repoData = await apiFetch(`https://api.github.com/repos/${state.owner}/${state.repo}`);
      const defaultBranch = parsed.branch || repoData.default_branch || 'main';
      state.branch = defaultBranch;

      // 2. Fetch branches
      try {
        const branchesData = await apiFetch(`https://api.github.com/repos/${state.owner}/${state.repo}/branches?per_page=50`);
        state.branches = branchesData.map(b => b.name);
        populateBranchDropdown(state.branches, state.branch);
      } catch (e) {
        state.branches = [state.branch];
        populateBranchDropdown(state.branches, state.branch);
      }

      // 3. Fetch directory contents
      await fetchDirectoryContents(state.currentPath);

    } catch (err) {
      showLoading(false);
      showErrorState('Failed to Load Repository', err.message);
    }
  }

  // Set repo and immediately load (used by quick presets)
  function setRepoAndLoad(repoUrl) {
    elements.repoInput.value = repoUrl;
    handleLoadRepo();
  }

  // Populate branch dropdown
  function populateBranchDropdown(branches, current) {
    elements.branchSelect.innerHTML = '';
    branches.forEach(branch => {
      const opt = document.createElement('option');
      opt.value = branch;
      opt.textContent = branch;
      if (branch === current) opt.selected = true;
      elements.branchSelect.appendChild(opt);
    });
  }

  // Handle branch switch
  function handleBranchChange(newBranch) {
    state.branch = newBranch;
    fetchDirectoryContents(state.currentPath);
  }

  // Fetch Directory Contents
  async function fetchDirectoryContents(path) {
    state.currentPath = path;
    showLoading(true);
    elements.emptyState.style.display = 'none';
    elements.filterInput.value = '';

    renderBreadcrumbs();
    elements.currentPathText.textContent = `Path: /${path}`;
    elements.fileCountText.textContent = 'Fetching...';

    const cleanPath = path ? path.replace(/^\/+|\/+$/g, '') : '';
    const apiUrl = `https://api.github.com/repos/${state.owner}/${state.repo}/contents/${cleanPath}?ref=${state.branch}`;

    try {
      const data = await apiFetch(apiUrl);

      // GitHub contents API returns an array for a directory, or an object if it's a single file
      let items = Array.isArray(data) ? data : [data];

      // Sort: Directories first (A-Z), then Files (A-Z)
      items.sort((a, b) => {
        if (a.type === b.type) {
          return a.name.localeCompare(b.name, undefined, { sensitivity: 'base', numeric: true });
        }
        return a.type === 'dir' ? -1 : 1;
      });

      state.items = items;
      state.filteredItems = items;

      showLoading(false);
      renderFileList(state.filteredItems);
      updateStatsBar(state.items);

    } catch (err) {
      showLoading(false);
      showErrorState('Failed to Fetch Contents', err.message);
    }
  }

  // Update Stats Bar
  function updateStatsBar(items) {
    const folders = items.filter(i => i.type === 'dir').length;
    const files = items.filter(i => i.type === 'file').length;
    const totalBytes = items.reduce((sum, i) => sum + (i.size || 0), 0);
    elements.fileCountText.textContent = `${folders} folders, ${files} files (${formatFileSize(totalBytes)})`;
  }

  // Filter Files
  function handleFilterFiles(query) {
    const q = query.trim().toLowerCase();
    if (!q) {
      state.filteredItems = state.items;
    } else {
      state.filteredItems = state.items.filter(item => item.name.toLowerCase().includes(q));
    }
    renderFileList(state.filteredItems);
  }

  // Render Breadcrumbs
  function renderBreadcrumbs() {
    elements.breadcrumbsNav.innerHTML = '';

    // Root crumb
    const rootCrumb = document.createElement('span');
    rootCrumb.className = `crumb-item ${!state.currentPath ? 'active' : ''}`;
    rootCrumb.innerHTML = `<i data-lucide="home" style="width: 14px; height: 14px;"></i> <span>${state.repo}</span>`;
    if (state.currentPath) {
      rootCrumb.addEventListener('click', () => fetchDirectoryContents(''));
    }
    elements.breadcrumbsNav.appendChild(rootCrumb);

    if (state.currentPath) {
      const parts = state.currentPath.split('/').filter(Boolean);
      let accumulated = '';

      parts.forEach((part, index) => {
        accumulated += (index > 0 ? '/' : '') + part;
        const currentAccumulated = accumulated;
        const isLast = index === parts.length - 1;

        const divider = document.createElement('span');
        divider.className = 'crumb-divider';
        divider.textContent = '/';
        elements.breadcrumbsNav.appendChild(divider);

        const crumb = document.createElement('span');
        crumb.className = `crumb-item ${isLast ? 'active' : ''}`;
        crumb.textContent = part;
        if (!isLast) {
          crumb.addEventListener('click', () => fetchDirectoryContents(currentAccumulated));
        }
        elements.breadcrumbsNav.appendChild(crumb);
      });
    }

    if (window.lucide) window.lucide.createIcons();
  }

  // Render File List
  function renderFileList(items) {
    elements.fileList.innerHTML = '';

    if (items.length === 0) {
      elements.emptyState.style.display = 'block';
      elements.emptyTitle.textContent = elements.filterInput.value ? 'No Matches Found' : 'Directory is Empty';
      elements.emptyMessage.textContent = elements.filterInput.value 
        ? `No files match "${elements.filterInput.value}" in this folder.`
        : 'This folder contains no files or subdirectories.';
      if (window.lucide) window.lucide.createIcons();
      return;
    }

    elements.emptyState.style.display = 'none';

    // If we are in a subdirectory, add a ".." back row
    if (state.currentPath) {
      const backRow = document.createElement('li');
      backRow.className = 'file-row';
      backRow.innerHTML = `
        <div class="file-icon folder">
          <i data-lucide="corner-left-up"></i>
        </div>
        <div class="file-name-col">
          <span class="file-name" style="font-weight: 600; color: var(--accent-primary);">.. (Go Up One Level)</span>
        </div>
        <div class="file-size"></div>
        <div class="file-actions"></div>
      `;
      backRow.addEventListener('click', () => {
        const parts = state.currentPath.split('/').filter(Boolean);
        parts.pop();
        fetchDirectoryContents(parts.join('/'));
      });
      elements.fileList.appendChild(backRow);
    }

    items.forEach(item => {
      const isDir = item.type === 'dir';
      const fileExt = getFileExtension(item.name);
      const iconInfo = getIconForFile(item.name, isDir);

      const li = document.createElement('li');
      li.className = 'file-row';

      li.innerHTML = `
        <div class="file-icon ${iconInfo.typeClass}">
          <i data-lucide="${iconInfo.iconName}"></i>
        </div>
        <div class="file-name-col">
          <span class="file-name" title="${item.name}">${escapeHtml(item.name)}</span>
          ${fileExt ? `<span class="file-badge">${fileExt}</span>` : ''}
        </div>
        <div class="file-size">${isDir ? 'Folder' : formatFileSize(item.size)}</div>
        <div class="file-actions">
          ${isDir ? `
            <button class="btn btn-secondary btn-sm" title="Open folder">
              <i data-lucide="folder-open"></i>
              <span>Open</span>
            </button>
          ` : `
            <button class="btn btn-ghost btn-sm preview-btn" title="Quick Preview">
              <i data-lucide="eye"></i>
              <span>Preview</span>
            </button>
            <button class="btn btn-secondary btn-sm dl-btn" title="Download file to device">
              <i data-lucide="download"></i>
              <span>Download</span>
            </button>
            <button class="btn btn-ghost btn-icon-only btn-sm copy-btn" title="Copy raw download link">
              <i data-lucide="copy"></i>
            </button>
          `}
        </div>
      `;

      // Row click behavior
      if (isDir) {
        li.addEventListener('click', () => fetchDirectoryContents(item.path));
      } else {
        const previewBtn = li.querySelector('.preview-btn');
        const dlBtn = li.querySelector('.dl-btn');
        const copyBtn = li.querySelector('.copy-btn');

        previewBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          openPreviewModal(item);
        });

        dlBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          downloadSingleFile(item);
        });

        copyBtn.addEventListener('click', (e) => {
          e.stopPropagation();
          copyToClipboard(item.download_url);
        });

        // Clicking the row opens preview
        li.addEventListener('click', () => openPreviewModal(item));
      }

      elements.fileList.appendChild(li);
    });

    if (window.lucide) window.lucide.createIcons();
  }

  // In-App File Preview Modal
  async function openPreviewModal(item) {
    elements.previewTitle.textContent = item.name;
    elements.previewSizeBadge.textContent = formatFileSize(item.size);
    elements.previewDownloadBtn.onclick = () => downloadSingleFile(item);

    const ext = getFileExtension(item.name).toLowerCase();
    const iconInfo = getIconForFile(item.name, false);
    elements.previewIcon.setAttribute('data-lucide', iconInfo.iconName);

    elements.previewBody.innerHTML = `
      <div class="spinner"></div>
    `;
    elements.previewModal.classList.add('active');

    try {
      // PDF Preview
      if (ext === 'pdf') {
        elements.previewBody.innerHTML = `
          <iframe src="${item.download_url}#toolbar=1" type="application/pdf" title="${item.name}"></iframe>
        `;
      }
      // Image Preview
      else if (['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'bmp', 'ico'].includes(ext)) {
        elements.previewBody.innerHTML = `
          <img src="${item.download_url}" alt="${item.name}" loading="lazy" />
        `;
      }
      // Audio Preview
      else if (['mp3', 'wav', 'ogg', 'm4a'].includes(ext)) {
        elements.previewBody.innerHTML = `
          <audio controls autoplay style="width: 80%; max-width: 500px;">
            <source src="${item.download_url}">
            Your browser does not support the audio element.
          </audio>
        `;
      }
      // Video Preview
      else if (['mp4', 'webm', 'mov'].includes(ext)) {
        elements.previewBody.innerHTML = `
          <video controls autoplay style="max-width: 95%; max-height: 70vh;">
            <source src="${item.download_url}">
            Your browser does not support the video element.
          </video>
        `;
      }
      // Code & Text Files
      else {
        // Fetch raw text content
        const resp = await fetch(item.download_url);
        if (!resp.ok) throw new Error('Could not fetch file content.');
        const text = await resp.text();

        // Detect language for Prism
        const lang = getPrismLanguage(ext);
        const codeElement = document.createElement('code');
        codeElement.className = `language-${lang}`;
        codeElement.textContent = text;

        const preElement = document.createElement('pre');
        preElement.appendChild(codeElement);

        elements.previewBody.innerHTML = '';
        elements.previewBody.appendChild(preElement);

        if (window.Prism) {
          window.Prism.highlightElement(codeElement);
        }
      }
    } catch (err) {
      elements.previewBody.innerHTML = `
        <div style="text-align: center; padding: 40px 20px;">
          <i data-lucide="alert-circle" style="width: 48px; height: 48px; color: #ef4444; margin-bottom: 12px;"></i>
          <h4 style="font-weight: 600; margin-bottom: 6px;">Cannot Preview File</h4>
          <p style="font-size: 0.85rem; color: var(--text-secondary); margin-bottom: 16px;">${err.message || 'This file format is not supported for inline preview.'}</p>
          <button class="btn btn-primary btn-sm" onclick="window.app.downloadSingleFileByName('${item.name}', '${item.download_url}')">
            <i data-lucide="download"></i>
            <span>Download Directly</span>
          </button>
        </div>
      `;
    }

    if (window.lucide) window.lucide.createIcons();
  }

  function closePreviewModal() {
    elements.previewModal.classList.remove('active');
    elements.previewBody.innerHTML = '';
  }

  // Single File Download Trigger
  function downloadSingleFile(item) {
    downloadFileByUrl(item.download_url, item.name);
  }

  async function downloadFileByUrl(url, filename) {
    showToast(`Downloading ${filename}...`, 'download');
    try {
      const res = await fetch(url);
      if (!res.ok) throw new Error('Download request failed');
      const blob = await res.blob();
      const blobUrl = URL.createObjectURL(blob);
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = filename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(blobUrl);
      showToast(`Downloaded ${filename} successfully!`, 'check-circle');
    } catch (err) {
      // Fallback: open in new tab
      window.open(url, '_blank');
      showToast('Opened download link in browser.', 'info');
    }
  }

  // Folder ZIP Download using Git Trees API & JSZip
  async function downloadCurrentFolderZip() {
    if (!window.JSZip) {
      showToast('JSZip library is loading, please try again in a moment.', 'alert-triangle');
      return;
    }

    const folderName = state.currentPath ? state.currentPath.split('/').pop() : state.repo;
    const zipFilename = `${state.repo}-${folderName || 'files'}.zip`;

    // Open progress modal
    elements.zipStatusTitle.textContent = `Preparing ZIP archive for "${folderName}"`;
    elements.zipStatusDetail.textContent = 'Querying repository tree structure...';
    elements.zipProgressBar.style.width = '0%';
    elements.zipProgressPercent.textContent = '0%';
    elements.zipModal.classList.add('active');

    state.activeZipAbortController = new AbortController();
    const signal = state.activeZipAbortController.signal;

    try {
      // 1. Fetch entire repo tree recursively in 1 API call via Git Trees API
      const treeUrl = `https://api.github.com/repos/${state.owner}/${state.repo}/git/trees/${state.branch}?recursive=1`;
      const treeData = await apiFetch(treeUrl);

      if (!treeData.tree || !Array.isArray(treeData.tree)) {
        throw new Error('Unable to read repository tree.');
      }

      // Filter for items inside the current folder
      const prefix = state.currentPath ? state.currentPath.replace(/^\/+|\/+$/g, '') + '/' : '';
      const blobsToDownload = treeData.tree.filter(node => {
        if (node.type !== 'blob') return false;
        if (!prefix) return true; // root includes all
        return node.path.startsWith(prefix);
      });

      if (blobsToDownload.length === 0) {
        throw new Error('No files found to archive in this folder.');
      }

      const zip = new window.JSZip();
      let completedCount = 0;
      const totalCount = blobsToDownload.length;

      elements.zipStatusTitle.textContent = `Downloading ${totalCount} file(s)...`;

      // Download files concurrently in batches of 4
      const concurrency = 4;
      for (let i = 0; i < totalCount; i += concurrency) {
        if (signal.aborted) throw new Error('ZIP creation was cancelled.');

        const batch = blobsToDownload.slice(i, i + concurrency);
        await Promise.all(batch.map(async (node) => {
          const relativePath = prefix ? node.path.substring(prefix.length) : node.path;
          const rawUrl = `https://raw.githubusercontent.com/${state.owner}/${state.repo}/${state.branch}/${node.path}`;

          elements.zipStatusDetail.textContent = `Fetching: ${relativePath}`;

          const fileResp = await fetch(rawUrl, { signal });
          if (!fileResp.ok) throw new Error(`Failed to download ${relativePath}`);
          const fileBlob = await fileResp.blob();

          zip.file(relativePath, fileBlob);
          completedCount++;

          const percent = Math.round((completedCount / totalCount) * 80);
          elements.zipProgressBar.style.width = `${percent}%`;
          elements.zipProgressPercent.textContent = `${percent}% (${completedCount}/${totalCount})`;
        }));
      }

      // 2. Compress into ZIP
      elements.zipStatusTitle.textContent = 'Generating .ZIP file...';
      elements.zipStatusDetail.textContent = 'Compressing collected files...';

      const zipBlob = await zip.generateAsync(
        { type: 'blob', compression: 'DEFLATE', compressionOptions: { level: 6 } },
        (metadata) => {
          const compPercent = 80 + Math.round(metadata.percent * 0.2);
          elements.zipProgressBar.style.width = `${compPercent}%`;
          elements.zipProgressPercent.textContent = `${compPercent}%`;
        }
      );

      // 3. Trigger Download
      const blobUrl = URL.createObjectURL(zipBlob);
      const a = document.createElement('a');
      a.href = blobUrl;
      a.download = zipFilename;
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
      URL.revokeObjectURL(blobUrl);

      elements.zipModal.classList.remove('active');
      showToast(`ZIP saved: ${zipFilename}`, 'check-circle');

    } catch (err) {
      elements.zipModal.classList.remove('active');
      if (err.message !== 'ZIP creation was cancelled.') {
        showToast(`Failed to create ZIP: ${err.message}`, 'alert-triangle');
      }
    } finally {
      state.activeZipAbortController = null;
    }
  }

  function cancelZipDownload() {
    if (state.activeZipAbortController) {
      state.activeZipAbortController.abort();
    }
    elements.zipModal.classList.remove('active');
    showToast('ZIP download cancelled.', 'info');
  }

  // Helpers
  function copyToClipboard(text) {
    if (!text) return;
    navigator.clipboard.writeText(text).then(() => {
      showToast('Raw download link copied to clipboard!', 'copy');
    }).catch(() => {
      showToast('Failed to copy link.', 'alert-triangle');
    });
  }

  function showToast(message, iconName = 'check-circle') {
    elements.toastMessage.textContent = message;
    elements.toastIcon.setAttribute('data-lucide', iconName);
    if (window.lucide) window.lucide.createIcons();

    elements.toast.classList.add('show');
    clearTimeout(elements.toast._timer);
    elements.toast._timer = setTimeout(() => {
      elements.toast.classList.remove('show');
    }, 3200);
  }

  function showLoading(show) {
    elements.loadingState.style.display = show ? 'block' : 'none';
    if (show) elements.emptyState.style.display = 'none';
  }

  function showErrorState(title, message) {
    elements.fileList.innerHTML = '';
    elements.emptyState.style.display = 'block';
    elements.emptyTitle.textContent = title;
    elements.emptyMessage.textContent = message;
    if (window.lucide) window.lucide.createIcons();
  }

  function formatFileSize(bytes) {
    if (!bytes || bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB', 'GB', 'TB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return parseFloat((bytes / Math.pow(k, i)).toFixed(2)) + ' ' + sizes[i];
  }

  function getFileExtension(filename) {
    const idx = filename.lastIndexOf('.');
    return idx !== -1 ? filename.slice(idx + 1) : '';
  }

  function escapeHtml(str) {
    return str
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  function getIconForFile(filename, isDir) {
    if (isDir) return { iconName: 'folder', typeClass: 'folder' };
    const ext = getFileExtension(filename).toLowerCase();

    if (ext === 'pdf') return { iconName: 'file-text', typeClass: 'pdf' };
    if (['png', 'jpg', 'jpeg', 'gif', 'svg', 'webp', 'bmp'].includes(ext)) return { iconName: 'image', typeClass: 'image' };
    if (['zip', 'tar', 'gz', '7z', 'rar'].includes(ext)) return { iconName: 'archive', typeClass: 'code' };
    if (['v', 'vhd', 'vhdl', 'sv', 'py', 'js', 'ts', 'kt', 'java', 'c', 'cpp', 'h', 'hpp', 'html', 'css', 'json', 'xml', 'md'].includes(ext)) {
      return { iconName: 'file-code-2', typeClass: 'code' };
    }
    return { iconName: 'file', typeClass: '' };
  }

  function getPrismLanguage(ext) {
    const map = {
      'js': 'javascript',
      'ts': 'typescript',
      'json': 'json',
      'html': 'markup',
      'xml': 'markup',
      'svg': 'markup',
      'css': 'css',
      'py': 'python',
      'c': 'c',
      'cpp': 'cpp',
      'h': 'c',
      'java': 'java',
      'kt': 'kotlin',
      'md': 'markdown',
      'v': 'verilog',
      'sv': 'systemverilog',
      'vhd': 'vhdl',
      'vhdl': 'vhdl'
    };
    return map[ext] || 'clike';
  }

  // Export to window for inline HTML onclick handlers
  window.app = {
    handleLoadRepo,
    setRepoAndLoad,
    toggleTokenBox,
    saveToken,
    handleFilterFiles,
    handleBranchChange,
    downloadCurrentFolderZip,
    cancelZipDownload,
    closePreviewModal,
    downloadSingleFileByName: (name, url) => downloadFileByUrl(url, name)
  };

  // Run on DOM loaded
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
})();
