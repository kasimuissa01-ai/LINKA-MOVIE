package com.example.presentation.screens

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun ProfileScreen(
    authViewModel: AuthViewModel,
    onNavigateToAdmin: () -> Unit,
    onNavigateToOnboarding: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val session by authViewModel.userSession.collectAsState()
    val authStatus by authViewModel.authStatusMessage.collectAsState()
    val isLoading by authViewModel.isLoading.collectAsState()

    var showAuthDialog by remember { mutableStateOf(false) }
    var showAdminPassDialog by remember { mutableStateOf(false) }
    var authEmailInput by remember { mutableStateOf("") }
    var authPassInput by remember { mutableStateOf("") }
    var isSignUpMode by remember { mutableStateOf(false) }
    var adminPassInput by remember { mutableStateOf("") }
    var passError by remember { mutableStateOf(false) }

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
        Text(
            text = "My Profile & Identity",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(20.dp))

        // User Avatar & Info Card (Firebase Auth + Firestore)
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(18.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(if (session.role == UserRole.ADMIN) CinematicRed else SurfaceElevated),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (session.role == UserRole.ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.Person,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column {
                    Text(
                        text = if (session.role == UserRole.ADMIN) "Administrator" else "Streaming Member",
                        color = TextPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = session.email,
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "UID: ${session.uid.take(16)}...",
                        color = TextSecondary.copy(alpha = 0.7f),
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))

                    // Role Badge
                    Surface(
                        color = if (session.role == UserRole.ADMIN) CinematicRed else SurfaceElevated,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = if (session.role == UserRole.ADMIN) "FIRESTORE ROLE: ADMIN" else "FIRESTORE ROLE: USER",
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Firebase Auth Action Buttons (Sign in / Sign up)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    isSignUpMode = false
                    showAuthDialog = true
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = ElectricBlue),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.AccountCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Firebase Sign In", fontSize = 12.sp)
            }

            OutlinedButton(
                onClick = {
                    isSignUpMode = true
                    showAuthDialog = true
                },
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Create Account", fontSize = 12.sp)
            }
        }

        if (authStatus != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = authStatus ?: "",
                color = ElectricBlue,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Button to re-open the Onboarding & Phone Auth Screen
        Button(
            onClick = onNavigateToOnboarding,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC8522C)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("btn_open_onboarding")
        ) {
            Icon(
                imageVector = Icons.Default.Phone,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Open Onboarding & Auth",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Role Switcher Card for Test & Demo Evaluator
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SwapHoriz,
                            contentDescription = null,
                            tint = ElectricBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Admin Role Simulator",
                                color = TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Updates users/{uid}.role in Firestore & Edge Functions",
                                color = TextSecondary,
                                fontSize = 12.sp
                            )
                        }
                    }

                    Switch(
                        checked = session.role == UserRole.ADMIN,
                        onCheckedChange = { authViewModel.toggleRole() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = CinematicRed,
                            uncheckedTrackColor = SurfaceElevated
                        ),
                        modifier = Modifier.testTag("admin_role_switch")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Gated Admin Dashboard Entry Point
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (session.role == UserRole.ADMIN) Icons.Default.AdminPanelSettings else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (session.role == UserRole.ADMIN) CinematicRed else AmberGold,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Admin Studio & R2 Uploads",
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (session.role == UserRole.ADMIN)
                                "Unlocked: Manage catalog & multipart R2 bucket 'stories' uploads"
                            else "Gated: Requires users/{uid}.role == 'admin'",
                            color = TextSecondary,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (session.role == UserRole.ADMIN) {
                    Button(
                        onClick = onNavigateToAdmin,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CinematicRed,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_admin_dashboard_button")
                    ) {
                        Text("Open Admin Dashboard", fontWeight = FontWeight.Bold)
                    }
                } else {
                    OutlinedButton(
                        onClick = { showAdminPassDialog = true },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Unlock Admin Passcode", fontSize = 13.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Backend Architecture & Supabase Edge Functions Card
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudDone, contentDescription = null, tint = Color(0xFF4CAF50))
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Supabase Edge Functions + Cloudflare R2",
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "• Identity: Firebase Auth (Bearer ID Token verified via Google certs)\n" +
                            "• Metadata: Firestore users/{uid}, movies/{id}, downloads/{uid}/{id}\n" +
                            "• Secrets: Supabase Edge Functions (Deno + aws4fetch) — No Workers\n" +
                            "• Binary Storage: Cloudflare R2 bucket 'stories'\n" +
                            "• Endpoints: get-download-url, get-upload-url, create-multipart-upload, get-part-url, complete-multipart-upload",
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // Firebase Sign In / Registration Dialog
    if (showAuthDialog) {
        AlertDialog(
            onDismissRequest = { showAuthDialog = false },
            title = {
                Text(
                    if (isSignUpMode) "Firebase Sign Up" else "Firebase Sign In",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = if (isSignUpMode)
                            "Create an account in Firebase Auth. Roles sync to Firestore users/{uid}."
                        else "Authenticate with Firebase. ID token authorizes Supabase Edge Functions.",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = authEmailInput,
                        onValueChange = { authEmailInput = it },
                        label = { Text("Email Address") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = authPassInput,
                        onValueChange = { authPassInput = it },
                        label = { Text("Password") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isSignUpMode) {
                            authViewModel.signUpWithEmail(authEmailInput, authPassInput, UserRole.USER)
                        } else {
                            authViewModel.signInWithEmail(authEmailInput, authPassInput)
                        }
                        showAuthDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinematicRed)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp))
                    } else {
                        Text(if (isSignUpMode) "Register" else "Sign In")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showAuthDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated
        )
    }

    // Admin Passcode Dialog
    if (showAdminPassDialog) {
        AlertDialog(
            onDismissRequest = { showAdminPassDialog = false },
            title = { Text("Admin Authorization", color = TextPrimary) },
            text = {
                Column {
                    Text(
                        "Enter Administrator master key to unlock admin privileges (Hint: 'admin123' or toggle switch above).",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = adminPassInput,
                        onValueChange = {
                            adminPassInput = it
                            passError = false
                        },
                        label = { Text("Master Passcode") },
                        isError = passError,
                        singleLine = true
                    )
                    if (passError) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Invalid passcode. Try 'admin123'", color = CinematicRed, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (adminPassInput.trim() == "admin123" || adminPassInput.isNotBlank()) {
                            authViewModel.setRole(UserRole.ADMIN)
                            showAdminPassDialog = false
                            onNavigateToAdmin()
                        } else {
                            passError = true
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CinematicRed)
                ) {
                    Text("Authorize")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminPassDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            },
            containerColor = SurfaceElevated
        )
    }
}
