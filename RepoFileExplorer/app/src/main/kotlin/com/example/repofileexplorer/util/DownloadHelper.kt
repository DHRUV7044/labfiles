package com.example.repofileexplorer.util

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import com.example.repofileexplorer.data.TreeItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object DownloadHelper {

    /**
     * Enqueue a single file download with Android's system DownloadManager.
     * The file is saved directly to Environment.DIRECTORY_DOWNLOADS.
     */
    fun downloadSingleFile(
        context: Context,
        filename: String,
        downloadUrl: String
    ): Long {
        val uri = Uri.parse(downloadUrl)
        val request = DownloadManager.Request(uri).apply {
            setTitle(filename)
            setDescription("Downloading from GitHub...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)

            // Resolve mime type
            val ext = filename.substringAfterLast('.', "")
            if (ext.isNotEmpty()) {
                val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase())
                if (mime != null) {
                    setMimeType(mime)
                }
            }
        }

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return downloadManager.enqueue(request)
    }

    /**
     * Downloads an entire directory tree recursively, packs it into a .ZIP archive,
     * and saves it to the device's public Downloads directory.
     */
    suspend fun downloadFolderAsZip(
        context: Context,
        owner: String,
        repo: String,
        branch: String,
        folderPath: String,
        treeItems: List<TreeItem>,
        token: String?,
        onProgress: (current: Int, total: Int, currentFile: String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val prefix = if (folderPath.isBlank()) "" else folderPath.trim('/') + "/"
            val blobs = treeItems.filter { node ->
                node.type == "blob" && (prefix.isEmpty() || node.path.startsWith(prefix))
            }

            if (blobs.isEmpty()) {
                return@withContext Result.failure(Exception("No files found in folder to archive."))
            }

            val folderName = if (folderPath.isBlank()) repo else folderPath.substringAfterLast('/')
            val safeName = "${repo}_${folderName}.zip".replace(Regex("[^a-zA-Z0-9._-]"), "_")

            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val zipFile = File(downloadsDir, safeName)

            FileOutputStream(zipFile).use { fos ->
                ZipOutputStream(fos).use { zos ->
                    blobs.forEachIndexed { index, blobNode ->
                        val relativePath = if (prefix.isNotEmpty()) {
                            blobNode.path.removePrefix(prefix)
                        } else {
                            blobNode.path
                        }

                        onProgress(index + 1, blobs.size, relativePath)

                        val rawUrl = "https://raw.githubusercontent.com/$owner/$repo/$branch/${blobNode.path}"
                        val url = URL(rawUrl)
                        val conn = (url.openConnection() as HttpURLConnection).apply {
                            requestMethod = "GET"
                            connectTimeout = 15000
                            readTimeout = 20000
                            if (!token.isNullOrBlank()) {
                                setRequestProperty("Authorization", "Bearer $token")
                            }
                        }

                        if (conn.responseCode in 200..299) {
                            zos.putNextEntry(ZipEntry(relativePath))
                            conn.inputStream.use { input: InputStream ->
                                input.copyTo(zos, bufferSize = 8192)
                            }
                            zos.closeEntry()
                        }
                    }
                }
            }

            Result.success(zipFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
