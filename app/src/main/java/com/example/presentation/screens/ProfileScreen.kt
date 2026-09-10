package com.example.presentation.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.MainActivity
import com.example.data.repository.AuthRepository
import com.example.domain.model.UserRole
import com.example.presentation.viewmodel.AuthViewModel
import com.example.ui.theme.AmberGold
import com.example.ui.theme.CinematicRed
import com.example.ui.theme.ElectricBlue
import com.example.ui.theme.ObsidianBlack
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceElevated
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import com.example.presentation.viewmodel.AppUpdateViewModel

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    onNavigateToAdmin: () -> Unit,
    onNavigateToOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val session by authViewModel.userSession.collectAsState()
    val context = LocalContext.current

    // Settings state
    var wifiOnlyDownload by remember { mutableStateOf(true) }
    var autoplayNext by remember { mutableStateOf(true) }
    var pushNotifications by remember { mutableStateOf(true) }
    var showAdminPassDialog by remember { mutableStateOf(false) }
    var adminPassInput by remember { mutableStateOf("") }
    var passError by remember { mutableStateOf(false) }

    val isAdmin = session.role == UserRole.ADMIN || AuthRepository.isAdminPhoneNumber(session.phoneNumber)
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBlack)
            .statusBarsPadding()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .testTag("profile_screen")
    ) {
        // App Branding / Header
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(MainActivity.APP_LOGO_URL)
                    .crossfade(true)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .build(),
                contentDescription = "MovieRoom Logo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "My Profile",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Account & Streaming Settings",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Main User Identity Card
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = if (isAdmin) listOf(CinematicRed, Color(0xFFE53935))
                                    else listOf(ElectricBlue, Color(0xFF7C4DFF))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isAdmin) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(36.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = session.displayName.ifBlank { "Alex Vance" },
                            color = TextPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Phone,
                                contentDescription = null,
                                tint = TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = session.phoneNumber.ifBlank { "+255 696 102 700" },
                                color = TextSecondary,
                                fontSize = 13.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = if (isAdmin) CinematicRed.copy(alpha = 0.2f) else ElectricBlue.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(if (isAdmin) CinematicRed else ElectricBlue)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isAdmin) "Administrator Access" else "Active Streaming Member",
                                    color = if (isAdmin) CinematicRed else ElectricBlue,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Profile Activity Stats (Movies Watched, Favorites, Downloads)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ProfileStatCard(
                icon = Icons.Default.PlayCircleFilled,
                iconColor = CinematicRed,
                count = "${session.watchedCount}",
                label = "Watched",
                modifier = Modifier.weight(1f)
            )
            ProfileStatCard(
                icon = Icons.Default.Bookmark,
                iconColor = AmberGold,
                count = "${session.favoriteCount}",
                label = "Watchlist",
                modifier = Modifier.weight(1f)
            )
            ProfileStatCard(
                icon = Icons.Default.Download,
                iconColor = ElectricBlue,
                count = "3",
                label = "Offline",
                modifier = Modifier.weight(1f)
            )
        }

        // Dedicated Admin Movie Studio Card (Only shown if user is admin)
        if (isAdmin) {
            Spacer(modifier = Modifier.height(20.dp))
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(CinematicRed.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = CinematicRed,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Admin Movie Studio",
                                color = TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Search TMDB & Upload to Cloudflare R2",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Button(
                        onClick = onNavigateToAdmin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinematicRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("open_admin_dashboard_button")
                    ) {
                        Icon(Icons.Default.Videocam, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open Upload & Movie Manager", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Streaming Preferences Section
        Text(
            text = "Playback & Downloads",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                SettingToggleRow(
                    icon = Icons.Default.HighQuality,
                    title = "Video Quality",
                    subtitle = "Auto 4K Ultra HD / 1080p",
                    trailing = {
                        Text("4K UHD", color = CinematicRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                )

                HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)

                SettingToggleRow(
                    icon = Icons.Default.Wifi,
                    title = "Download over Wi-Fi only",
                    subtitle = "Save mobile data plan",
                    trailing = {
                        Switch(
                            checked = wifiOnlyDownload,
                            onCheckedChange = { wifiOnlyDownload = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CinematicRed,
                                uncheckedTrackColor = SurfaceElevated
                            )
                        )
                    }
                )

                HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)

                SettingToggleRow(
                    icon = Icons.Default.PlayCircleFilled,
                    title = "Autoplay Next",
                    subtitle = "Automatically play next recommended movie",
                    trailing = {
                        Switch(
                            checked = autoplayNext,
                            onCheckedChange = { autoplayNext = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CinematicRed,
                                uncheckedTrackColor = SurfaceElevated
                            )
                        )
                    }
                )

                HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)

                SettingToggleRow(
                    icon = Icons.Default.Notifications,
                    title = "New Movie Releases",
                    subtitle = "Get notified when new blockbusters drop",
                    trailing = {
                        Switch(
                            checked = pushNotifications,
                            onCheckedChange = { pushNotifications = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = CinematicRed,
                                uncheckedTrackColor = SurfaceElevated
                            )
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Account Actions
        Text(
            text = "Account",
            color = TextPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(10.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToOnboarding() }
                        .padding(16.dp)
                        .testTag("btn_switch_account")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Person, contentDescription = null, tint = ElectricBlue, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Switch Phone Number / Profile", color = TextPrimary, fontSize = 14.sp)
                    }
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(14.dp))
                }

                HorizontalDivider(color = Color(0x1AFFFFFF), thickness = 0.5.dp)

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            authViewModel.signOut()
                            onNavigateToOnboarding()
                        }
                        .padding(16.dp)
                        .testTag("btn_sign_out")
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null, tint = CinematicRed, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Sign Out", color = CinematicRed, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Clean Footer App Version
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "MovieRoom Cinema v1.0",
                color = TextTertiary,
                fontSize = 12.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ultra HD Streaming & Cloudflare R2 Storage",
                color = TextTertiary.copy(alpha = 0.6f),
                fontSize = 11.sp
            )
        }

        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ProfileStatCard(
    icon: ImageVector,
    iconColor: Color,
    count: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp, horizontal = 8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = count,
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp
                )
            }
        }
        trailing()
    }
}
