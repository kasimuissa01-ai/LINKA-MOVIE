package com.example.util

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileInputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * PackageInstallerHelper provides silent/seamless in-app APK installation
 * via the Android PackageInstaller Session API.
 * 
 * Result: Users see only the small native system dialog with [Install / Cancel]
 * or an entirely silent background update on Android 12+ (API 31+) when
 * USER_ACTION_NOT_REQUIRED is active and unknown sources permission is granted.
 */
object PackageInstallerHelper {
    private const val TAG = "PackageInstallerHelper"
    const val ACTION_INSTALL_STATUS = "com.example.ACTION_INSTALL_STATUS"

    fun installApk(context: Context, apkFile: File) {
        if (!apkFile.exists() || apkFile.length() <= 0) {
            Log.e(TAG, "Cannot install: APK file does not exist or is empty.")
            return
        }

        // Check if unknown sources permission is granted on Oreo+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!context.packageManager.canRequestPackageInstalls()) {
                Log.w(TAG, "Unknown app sources permission not yet granted. Prompting user once.")
                try {
                    val permissionIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(permissionIntent)
                    return
                } catch (e: Exception) {
                    Log.e(TAG, "Failed opening unknown app sources settings: ${e.message}")
                }
            }
        }

        installApkWithSession(context, apkFile)
    }

    fun installApkWithSession(context: Context, apkFile: File) {
        try {
            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setAppPackageName(context.packageName)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            apkFile.inputStream().use { input ->
                session.openWrite("package_update_stream", 0, apkFile.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }

            val receiverIntent = Intent(context, PackageInstallerStatusReceiver::class.java).apply {
                action = ACTION_INSTALL_STATUS
                setPackage(context.packageName)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                sessionId,
                receiverIntent,
                flags
            )

            session.commit(pendingIntent.intentSender)
            session.close()
            Log.i(TAG, "PackageInstaller session $sessionId committed successfully.")
        } catch (e: Exception) {
            Log.e(TAG, "PackageInstaller session failed: ${e.message}. Falling back to standard installer.", e)
            fallbackInstall(context, apkFile)
        }
    }

    fun fallbackInstall(context: Context, apkFile: File) {
        try {
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
            Log.e(TAG, "Fallback installation failed: ${e.message}", e)
        }
    }
}
