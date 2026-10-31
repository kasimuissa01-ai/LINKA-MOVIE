package com.example.presentation.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary

/**
 * Prompts the user once on first launch to grant the "Install unknown apps" permission.
 * Once granted or dismissed, it is never shown again, ensuring all future in-app updates
 * install silently or with only the native system dialog ([Install / Cancel]) via the
 * PackageInstaller Session API.
 */
@Composable
fun FirstLaunchInstallPermissionDialog() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_update_prefs", Context.MODE_PRIVATE) }

    var shouldShowDialog by remember {
        val hasPrompted = prefs.getBoolean("has_prompted_unknown_sources", false)
        val needsPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            !context.packageManager.canRequestPackageInstalls()
        } else {
            false
        }
        mutableStateOf(!hasPrompted && needsPermission)
    }

    if (!shouldShowDialog) return

    AlertDialog(
        onDismissRequest = {
            prefs.edit().putBoolean("has_prompted_unknown_sources", true).apply()
            shouldShowDialog = false
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    tint = CinematicRed,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Seamless App Updates",
                    color = TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "Enable update permission once to allow MovieRoom to install new versions seamlessly in the background.\n\nOnce granted, future updates will install silently with a single tap and no browser or permission prompts.",
                    color = TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    prefs.edit().putBoolean("has_prompted_unknown_sources", true).apply()
                    shouldShowDialog = false
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                data = Uri.parse("package:${context.packageName}")
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            android.util.Log.e("InstallDialog", "Failed to open settings: ${e.message}")
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = CinematicRed),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("btn_grant_update_permission")
            ) {
                Text("Grant Permission", fontWeight = FontWeight.Bold, color = Color.White)
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    prefs.edit().putBoolean("has_prompted_unknown_sources", true).apply()
                    shouldShowDialog = false
                },
                modifier = Modifier.testTag("btn_dismiss_update_permission")
            ) {
                Text("Later", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.testTag("first_launch_install_permission_dialog")
    )
}
