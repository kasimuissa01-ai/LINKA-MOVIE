package com.example.data.remote

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

data class AppUpdateInfo(
    val isUpdateAvailable: Boolean = false,
    val latestVersion: String = "",
    val releaseTitle: String = "",
    val releaseNotes: String = "",
    val downloadUrl: String = "",
    val apkSizeMb: Double = 0.0
)

sealed class UpdateDownloadState {
    object Idle : UpdateDownloadState()
    data class Downloading(val progress: Float, val downloadedMb: Double, val totalMb: Double) : UpdateDownloadState()
    data class ReadyToInstall(val apkFile: File) : UpdateDownloadState()
    data class Error(val message: String) : UpdateDownloadState()
}

class AppUpdateService(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "AppUpdateService"
        const val GITHUB_REPO = "kasimuissa01-ai/LINKA-MOVIE"
        const val GITHUB_LATEST_RELEASE_API = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    }

    private val _downloadState = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val downloadState: StateFlow<UpdateDownloadState> = _downloadState.asStateFlow()

    suspend fun checkForUpdates(currentVersionName: String): AppUpdateInfo = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url(GITHUB_LATEST_RELEASE_API)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "MovieRoom-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                Log.w(TAG, "GitHub release check response code: ${response.code}")
                return@withContext AppUpdateInfo()
            }

            val bodyString = response.body?.string() ?: return@withContext AppUpdateInfo()
            val json = JSONObject(bodyString)

            val rawTag = json.optString("tag_name", "").trim()
            val tagName = rawTag.removePrefix("v").removePrefix("V")
            val releaseTitle = json.optString("name", "New MovieRoom Release")
            val releaseNotes = json.optString("body", "Bug fixes, performance boosts, and new cinema features.")
            
            var apkDownloadUrl = ""
            var apkSize = 0.0

            val assets = json.optJSONArray("assets")
            if (assets != null && assets.length() > 0) {
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        apkDownloadUrl = asset.optString("browser_download_url")
                        val sizeBytes = asset.optLong("size", 0L)
                        apkSize = (sizeBytes / (1024.0 * 1024.0) * 10.0).toInt() / 10.0
                        break
                    }
                }
            }

            // If no direct apk in assets, check html_url or direct fallback
            if (apkDownloadUrl.isBlank()) {
                val htmlUrl = json.optString("html_url", "")
                if (htmlUrl.isNotBlank()) {
                    apkDownloadUrl = "$htmlUrl/expanded_assets/$rawTag"
                }
            }

            val isNewer = isVersionNewer(newVersion = tagName, currentVersion = currentVersionName)

            Log.d(TAG, "Current: $currentVersionName, Latest: $tagName, isNewer: $isNewer, url: $apkDownloadUrl")

            AppUpdateInfo(
                isUpdateAvailable = isNewer && apkDownloadUrl.isNotBlank(),
                latestVersion = tagName,
                releaseTitle = releaseTitle,
                releaseNotes = releaseNotes,
                downloadUrl = apkDownloadUrl,
                apkSizeMb = if (apkSize > 0.0) apkSize else 24.0
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error checking for app updates: ${e.message}")
            AppUpdateInfo()
        }
    }

    suspend fun downloadAndInstallApk(
        context: Context,
        downloadUrl: String,
        versionTag: String
    ) = withContext(Dispatchers.IO) {
        try {
            _downloadState.value = UpdateDownloadState.Downloading(0f, 0.0, 24.0)

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "MovieRoom-App")
                .build()

            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                _downloadState.value = UpdateDownloadState.Error("Failed to download update file: HTTP ${response.code}")
                return@withContext
            }

            val body = response.body
            if (body == null) {
                _downloadState.value = UpdateDownloadState.Error("Empty response from server")
                return@withContext
            }

            val totalBytes = body.contentLength().let { if (it > 0) it else 24L * 1024 * 1024 }
            val downloadDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: context.cacheDir
            val outputFile = File(downloadDir, "MovieRoom-$versionTag.apk")
            if (outputFile.exists()) {
                outputFile.delete()
            }

            body.byteStream().use { input ->
                FileOutputStream(outputFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    var bytesRead: Int
                    var totalRead = 0L

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead

                        val progress = (totalRead.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
                        val downloadedMb = (totalRead / (1024.0 * 1024.0) * 10.0).toInt() / 10.0
                        val totalMb = (totalBytes / (1024.0 * 1024.0) * 10.0).toInt() / 10.0

                        _downloadState.value = UpdateDownloadState.Downloading(
                            progress = progress,
                            downloadedMb = downloadedMb,
                            totalMb = totalMb
                        )
                    }
                    output.flush()
                }
            }

            _downloadState.value = UpdateDownloadState.ReadyToInstall(outputFile)

            // Trigger Android Package Installer on Main Thread
            withContext(Dispatchers.Main) {
                installApk(context, outputFile)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed downloading update APK: ${e.message}", e)
            _downloadState.value = UpdateDownloadState.Error("Download interrupted: ${e.message}")
        }
    }

    fun installApk(context: Context, apkFile: File) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                if (!context.packageManager.canRequestPackageInstalls()) {
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permissionIntent)
                }
            }

            val apkUri: Uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

            context.startActivity(installIntent)
        } catch (e: Exception) {
            Log.e(TAG, "Error launching package installer: ${e.message}", e)
        }
    }

    fun resetDownloadState() {
        _downloadState.value = UpdateDownloadState.Idle
    }

    private fun isVersionNewer(newVersion: String, currentVersion: String): Boolean {
        if (newVersion.isBlank()) return false
        val newParts = newVersion.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }
        val curParts = currentVersion.split(".").mapNotNull { it.filter { c -> c.isDigit() }.toIntOrNull() }

        val maxLen = maxOf(newParts.size, curParts.size)
        for (i in 0 until maxLen) {
            val n = newParts.getOrElse(i) { 0 }
            val c = curParts.getOrElse(i) { 0 }
            if (n > c) return true
            if (n < c) return false
        }
        return false
    }
}
