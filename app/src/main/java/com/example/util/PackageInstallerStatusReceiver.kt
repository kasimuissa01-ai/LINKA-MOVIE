package com.example.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log

/**
 * BroadcastReceiver for PackageInstaller session installation status callbacks.
 * When user action is pending, immediately launches the small system prompt
 * ("Install / Cancel") without requiring external browser or extra permissions.
 */
class PackageInstallerStatusReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "PkgInstallerReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: ""

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                Log.i(TAG, "PackageInstaller requires user confirmation: launching small system dialog.")
                val confirmationIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmationIntent != null) {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmationIntent)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed launching system install prompt: ${e.message}", e)
                    }
                }
            }

            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "App updated successfully via PackageInstaller Session API!")
            }

            else -> {
                Log.w(TAG, "PackageInstaller session status: $status, message: $message")
            }
        }
    }
}
