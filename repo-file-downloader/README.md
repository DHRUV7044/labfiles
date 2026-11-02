# RepoFile Explorer & Downloader 🚀

A modern, responsive web application to inspect and download folders, individual files, or full ZIP archives directly from any GitHub repository.

Preconfigured for: `git@github.com:DHRUV7044/labfiles.git`

---

## ✨ Features

- 📁 **Repository & Folder Browser**: Seamlessly navigate subdirectories with interactive breadcrumbs and "Go up one level" shortcuts.
- ⬇️ **Folder ZIP Archiving**: Download any directory or the entire repository as a zipped `.zip` archive on the fly using client-side JSZip with a real-time progress bar.
- 👁️ **In-App File Previews**:
  - **PDFs**: Preview PDF documents with browser-native viewing controls.
  - **Code & Text**: Syntax highlighted code viewer supporting Verilog, VHDL, Python, C/C++, Java, Kotlin, Markdown, and JSON.
  - **Images**: In-app image preview (PNG, JPG, SVG, WebP).
- 🔗 **Direct File Download & Link Copy**: Download single files directly to your machine or copy raw download links with one click.
- 🔄 **Repository Switcher**: Switch to any public or private GitHub repository using:
  - SSH: `git@github.com:owner/repo.git`
  - HTTPS: `https://github.com/owner/repo`
  - Deep paths: `https://github.com/owner/repo/tree/main/folder`
  - Short notation: `owner/repo`
- 🌿 **Branch & Tag Selector**: Switch dynamically between branches and tags.
- 🔍 **Real-time Filter**: Instant search filter to find files in deep or large directories.
- 🔑 **GitHub Token Support**: Optional Personal Access Token input to bypass public rate limits or explore private repositories.
- 🌓 **Dark & Light Mode**: Smooth theme switcher with persistent user preference in `localStorage`.

---

## 🚀 How to Run

### Option 1: Quick Launcher Script
From this directory, run:
```bash
./run.sh
```
Or open [http://localhost:8080](http://localhost:8080) in your web browser.

### Option 2: Python HTTP Server
```bash
python3 -m http.server 8080
```

### Option 3: Direct Browser File
You can also open `index.html` directly in your browser.
