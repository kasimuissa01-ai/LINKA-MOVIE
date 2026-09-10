package com.example.presentation.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.UpdateDownloadState
import com.example.presentation.viewmodel.AppUpdateViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@Composable
fun BottomUpdateAlert(
    updateViewModel: AppUpdateViewModel,
    modifier: Modifier = Modifier
) {
    val showBottomAlert by updateViewModel.showBottomAlert.collectAsState()
    val updateInfo by updateViewModel.updateInfo.collectAsState()
    val downloadState by updateViewModel.downloadState.collectAsState()
    val context = LocalContext.current

    val isVisible = showBottomAlert && updateInfo?.isUpdateAvailable == true

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(
            initialOffsetY = { it * 2 },
            animationSpec = spring(dampingRatio = 0.75f, stiffness = 300f)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutVertically(
            targetOffsetY = { it * 2 },
            animationSpec = tween(250, easing = FastOutSlowInEasing)
        ) + fadeOut(animationSpec = tween(200)),
        modifier = modifier
    ) {
        val info = updateInfo ?: return@AnimatedVisibility

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .shadow(16.dp, RoundedCornerShape(22.dp), ambientColor = CinematicRed.copy(alpha = 0.35f), spotColor = CinematicRed.copy(alpha = 0.5f))
                .border(
                    width = 1.2.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            CinematicRed,
                            Color(0xFFFF5252),
                            CinematicRed.copy(alpha = 0.6f)
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
                .testTag("bottom_update_alert_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF1E1418),
                                SurfaceDark
                            )
                        )
                    )
                    .padding(14.dp)
            ) {
                // Top Row: Icon, Title & Message, Action / Close
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Left: Glowing Update Badge
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = when (downloadState) {
                                        is UpdateDownloadState.ReadyToInstall -> listOf(Color(0xFF2E7D32), Color(0xFF4CAF50))
                                        is UpdateDownloadState.Downloading -> listOf(ElectricBlue, Color(0xFF29B6F6))
                                        else -> listOf(CinematicRed, Color(0xFFFF5252))
                                    }
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (downloadState) {
                                is UpdateDownloadState.ReadyToInstall -> Icons.Default.CheckCircle
                                is UpdateDownloadState.Downloading -> Icons.Default.CloudDownload
                                else -> Icons.Default.RocketLaunch
                            },
                            contentDescription = "Update Available",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    // Center: Update Headline & Description
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { updateViewModel.openUpdateDialog() }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = when (downloadState) {
                                    is UpdateDownloadState.ReadyToInstall -> "Update Ready to Install"
                                    is UpdateDownloadState.Downloading -> "Updating in Background..."
                                    else -> "Update App for New Features"
                                },
                                color = TextPrimary,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = CinematicRed.copy(alpha = 0.25f),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = "v${info.latestVersion}",
                                    color = Color(0xFFFF8A80),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        Text(
                            text = when (val state = downloadState) {
                                is UpdateDownloadState.Downloading ->
                                    "Downloading (${(state.progress * 100).toInt()}%) • You can keep using the app"
                                is UpdateDownloadState.ReadyToInstall ->
                                    "Download completed (${info.apkSizeMb} MB). Tap to install."
                                else ->
                                    "New streaming upgrades & features ready to install"
                            },
                            color = TextSecondary,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Right: Action Button
                    when (val state = downloadState) {
                        is UpdateDownloadState.Downloading -> {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier.size(36.dp)
                            ) {
                                CircularProgressIndicator(
                                    progress = { state.progress },
                                    color = CinematicRed,
                                    strokeWidth = 3.dp,
                                    trackColor = SurfaceElevated,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        is UpdateDownloadState.ReadyToInstall -> {
                            Button(
                                onClick = { updateViewModel.retryInstall(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF2E7D32),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("btn_bottom_alert_install")
                            ) {
                                Text("Install", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        else -> {
                            Button(
                                onClick = { updateViewModel.startDownloadAndInstall(context) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CinematicRed,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .height(36.dp)
                                    .testTag("btn_bottom_alert_update")
                            ) {
                                Text("Update", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Dismiss '✕' button
                    IconButton(
                        onClick = { updateViewModel.dismissBottomAlert() },
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("btn_dismiss_bottom_alert")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = TextTertiary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // If downloading, show background progress bar below
                if (downloadState is UpdateDownloadState.Downloading) {
                    val state = downloadState as UpdateDownloadState.Downloading
                    Spacer(modifier = Modifier.height(10.dp))
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Background download active",
                                color = TextTertiary,
                                fontSize = 10.sp
                            )
                            Text(
                                text = "${state.downloadedMb} MB / ${state.totalMb} MB (${(state.progress * 100).toInt()}%)",
                                color = Color(0xFFFF8A80),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { state.progress },
                            color = CinematicRed,
                            trackColor = SurfaceElevated,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
        }
    }
}
