package com.example.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.remote.UpdateDownloadState
import com.example.presentation.viewmodel.AppUpdateViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun AppUpdateDialog(
    updateViewModel: AppUpdateViewModel,
    modifier: Modifier = Modifier
) {
    val showDialog by updateViewModel.showUpdateDialog.collectAsState()
    val updateInfo by updateViewModel.updateInfo.collectAsState()
    val downloadState by updateViewModel.downloadState.collectAsState()
    val context = LocalContext.current

    if (!showDialog || updateInfo == null) return

    val info = updateInfo!!

    Dialog(
        onDismissRequest = {
            if (downloadState !is UpdateDownloadState.Downloading) {
                updateViewModel.dismissUpdateDialog()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = downloadState !is UpdateDownloadState.Downloading,
            dismissOnClickOutside = downloadState !is UpdateDownloadState.Downloading
        )
    ) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = modifier
                .fillMaxWidth()
                .border(1.dp, CinematicRed.copy(alpha = 0.3f), RoundedCornerShape(24.dp))
                .testTag("app_update_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(CinematicRed, Color(0xFFFF5252))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (downloadState) {
                            is UpdateDownloadState.ReadyToInstall -> Icons.Default.DownloadDone
                            is UpdateDownloadState.Downloading -> Icons.Default.CloudDownload
                            else -> Icons.Default.RocketLaunch
                        },
                        contentDescription = "App Update",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Update Available!",
                    color = TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Version comparison chips
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Surface(
                        color = SurfaceElevated,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Current: v${updateViewModel.currentAppVersion}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "➔", color = CinematicRed, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))

                    Surface(
                        color = CinematicRed.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "New: v${info.latestVersion}",
                            color = CinematicRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Changelog / Release Notes Box
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceElevated),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 140.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(14.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.NewReleases, contentDescription = null, tint = AmberGold, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = info.releaseTitle.ifBlank { "What's New in this Release:" },
                                color = TextPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = info.releaseNotes.ifBlank {
                                "• Performance improvements & streaming optimizations\n• Enhanced movie catalog\n• UI & playback stability upgrades"
                            },
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 17.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Download Progress State
                when (val state = downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Downloading update...",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = "${(state.progress * 100).toInt()}% (${state.downloadedMb}MB / ${state.totalMb}MB)",
                                    color = CinematicRed,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            LinearProgressIndicator(
                                progress = { state.progress },
                                color = CinematicRed,
                                trackColor = SurfaceElevated,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                        }
                    }

                    is UpdateDownloadState.Error -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(CinematicRed.copy(alpha = 0.15f))
                                .padding(10.dp)
                        ) {
                            Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = CinematicRed, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = state.message,
                                color = CinematicRed,
                                fontSize = 12.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Surface(
                            color = Color(0xFF1B3D2B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(10.dp)
                            ) {
                                Icon(Icons.Default.DownloadDone, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Download complete! Tap Install to update.",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    UpdateDownloadState.Idle -> {
                        // Regular prompt
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Action Buttons
                when (val state = downloadState) {
                    is UpdateDownloadState.Downloading -> {
                        // Disable actions while downloading
                        Text(
                            text = "Please wait while the update downloads...",
                            color = TextTertiary,
                            fontSize = 12.sp
                        )
                    }

                    is UpdateDownloadState.ReadyToInstall -> {
                        Button(
                            onClick = { updateViewModel.retryInstall(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF2E7D32),
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_install_downloaded_apk")
                        ) {
                            Icon(Icons.Default.DownloadDone, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Install Update Now", fontWeight = FontWeight.Bold)
                        }
                    }

                    else -> {
                        Button(
                            onClick = { updateViewModel.startDownloadAndInstall(context) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = CinematicRed,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("btn_update_now")
                        ) {
                            Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Update & Install (${info.apkSizeMb} MB)", fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TextButton(
                            onClick = { updateViewModel.dismissUpdateDialog() },
                            modifier = Modifier.testTag("btn_remind_later")
                        ) {
                            Text("Later", color = TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}

// Helper to provide heightIn for Composable
private fun Modifier.heightIn(max: androidx.compose.ui.unit.Dp): Modifier {
    return this.then(Modifier.height(max))
}
