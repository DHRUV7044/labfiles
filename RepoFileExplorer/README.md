# RepoFile Explorer (Android App) 📱

A native Android application built with Kotlin, Jetpack Compose, and Material 3 to explore GitHub repositories, browse directories with interactive breadcrumbs, preview files, and download individual files or complete directory ZIP archives.

Pre-configured default: `git@github.com:DHRUV7044/labfiles.git`

---

## ✨ Features

- 🔗 **GitHub Repository Input**:
  - Automatically loads `git@github.com:DHRUV7044/labfiles.git`.
  - Supports SSH (`git@github.com:...`), HTTPS (`https://github.com/...`), deep branch/folder URLs (`https://github.com/.../tree/main/...`), and shorthand (`owner/repo`).
  - Quick preset chip to restore or re-explore `DHRUV7044/labfiles`.
  - Optional GitHub Personal Access Token field for higher rate limits (5,000 reqs/hr) or private repositories.
- 📁 **Directory & Breadcrumb Navigation**:
  - Horizontally scrollable breadcrumb bar to jump between folder levels.
  - ".." Back to parent folder row.
  - Subdirectories displayed with folder icons and badges.
- ⬇️ **Download Manager Integration**:
  - Individual file downloads are enqueued via Android's native `DownloadManager` directly into the public `Downloads` directory with system progress notifications.
  - Copy direct raw download links (`https://raw.githubusercontent.com/...`) to the clipboard with one tap.
- 🗜️ **Folder ZIP Archiving**:
  - Recursively collects files inside the current directory using GitHub's Git Trees API.
  - Compresses them on-device into a clean `.zip` archive saved to `Downloads/`.
  - Live progress dialog tracking downloaded file count and progress percentage.
- 👁️ **In-App File Previews**:
  - **Code & Text**: Scrollable monospace text viewer for Verilog (`.v`), VHDL, Python, C/C++, Java, Kotlin, JSON, and Markdown.
  - **PDFs**: Direct "Open in PDF Reader" intent launching device PDF viewers (Drive, Acrobat, Chrome, etc.) or direct download.
- 🌿 **Branch & Tag Selector**: Switch dynamically between branches.
- 🔍 **Real-Time Filter**: Instant file search inside current directory.
- 🌓 **Material 3 Theme**: Clean light and dark theme support matching system styling.

---

## 🛠️ Build & Installation

### Build Debug APK:
```bash
./gradlew assembleDebug
```
The APK is located at:
`app/build/outputs/apk/debug/app-debug.apk`

### Install onto Connected Device / Emulator:
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Run Unit Tests:
```bash
./gradlew testDebugUnitTest
```
