# Lab Files & Repository Explorer 🧪📱

Repository containing lab files, assignments, and cross-platform tools to browse and download repository folders and files.

---

## 📦 What's Inside

### 1. 📂 Lab Files (`sit_sem5/`)
Contains semester course work, lab reports, and assignments:
- `sit_sem5/Dhruv_U24EV057/`
  - `sit_assignment6.pdf`
  - `sit_lab10_keithley_mosfet.pdf`
  - `sit_lab11_raman.pdf`

---

### 2. 📱 Android App (`RepoFileExplorer/`)
A native Android application built with **Kotlin**, **Jetpack Compose**, and **Material 3**.
- **Interactive Breadcrumb Navigation**: Seamless directory browsing and ".." back-up navigation.
- **Android Download Manager**: One-click download directly into device storage (`Downloads/`) with system notifications.
- **Folder ZIP Archiving**: Downloads and compresses entire folders into `.zip` archives on-device with live progress tracking.
- **In-App File Previews**: Built-in syntax highlighted code viewer and system PDF launcher.
- **Preconfigured Target**: Defaults to `git@github.com:DHRUV7044/labfiles.git` with instant quick-presets.
- **Direct APK**: Ready-to-install debug APK available in [`apk/RepoFileExplorer.apk`](apk/RepoFileExplorer.apk).

---

### 3. 🌐 Web App (`repo-file-downloader/`)
A responsive, client-side web application for instant browser exploration.
- Works in any browser without complicated build steps.
- Real-time search filter and directory breadcrumbs.
- Client-side folder ZIP generator using JSZip.
- Dark & Light mode toggle.
- **Quick Run**:
  ```bash
  cd repo-file-downloader
  ./run.sh
  ```
  Or launch `python3 -m http.server 8080`.
