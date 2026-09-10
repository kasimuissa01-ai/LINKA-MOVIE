package com.example.presentation.screens

import com.example.MainActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.Coil
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.example.presentation.viewmodel.AuthViewModel
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

// Background Poster Image URL
private const val AUTH_BACKGROUND_IMAGE_URL =
    "https://vqgnxqabvmmpfoiceass.supabase.co/storage/v1/object/public/posters/994191af-3db3-4f4c-9a1d-0b0c4d1fa5ef/468bc883dda769afbd066f44d5aa8b8a.jpg"

// Styling constants
private val EditorialCardBg = Color(0xFFFFFFFF)
private val EditorialTextBlack = Color(0xFF141312)
private val EditorialTextMuted = Color(0xFF8E8880)
private val EditorialBorderSubtle = Color(0xFFE5E0D8)
private val EditorialAccentRust = Color(0xFFC8522C) // Warm terracotta accent
private val EditorialButtonDark = Color(0xFF171615)
private val EditorialDisabledBg = Color(0x66FFFFFF)
private val EditorialDisabledText = Color(0x99FFFFFF)
private val EditorialErrorRed = Color(0xFFE53935)

data class CountryCode(val code: String, val dialCode: String, val name: String, val flag: String)

private val DefaultCountryCodes = listOf(
    CountryCode("TZ", "+255", "Tanzania", "🇹🇿"),
    CountryCode("KE", "+254", "Kenya", "🇰🇪"),
    CountryCode("CD", "+243", "Congo (DRC)", "🇨🇩"),
    CountryCode("BI", "+257", "Burundi", "🇧🇮"),
    CountryCode("UG", "+256", "Uganda", "🇺🇬"),
    CountryCode("RW", "+250", "Rwanda", "🇷🇼"),
    CountryCode("NG", "+234", "Nigeria", "🇳🇬"),
    CountryCode("ZA", "+27", "South Africa", "🇿🇦"),
    CountryCode("US", "+1", "United States", "🇺🇸"),
    CountryCode("GB", "+44", "United Kingdom", "🇬🇧"),
    CountryCode("CA", "+1", "Canada", "🇨🇦"),
    CountryCode("FR", "+33", "France", "🇫🇷"),
    CountryCode("DE", "+49", "Germany", "🇩🇪"),
    CountryCode("IN", "+91", "India", "🇮🇳")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingAuthScreen(
    authViewModel: AuthViewModel,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // Preload background image into memory cache for instant rendering
    LaunchedEffect(Unit) {
        if (authViewModel.isUserLoggedIn()) {
            onNavigateToHome()
            return@LaunchedEffect
        }
        val request = ImageRequest.Builder(context)
            .data(AUTH_BACKGROUND_IMAGE_URL)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .crossfade(true)
            .build()
        Coil.imageLoader(context).enqueue(request)
    }

    // Input States
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var selectedCountry by remember {
        val deviceCountry = Locale.getDefault().country
        val found = DefaultCountryCodes.firstOrNull { it.code.equals(deviceCountry, ignoreCase = true) }
        mutableStateOf(found ?: DefaultCountryCodes[0])
    }

    // Focus & Validation
    var isNameFocused by remember { mutableStateOf(false) }
    var isPhoneFocused by remember { mutableStateOf(false) }
    var phoneError by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }

    // Country picker bottom sheet
    var showCountryPicker by remember { mutableStateOf(false) }

    // Loading indicator when submitting
    var isSubmitting by remember { mutableStateOf(false) }

    val isFormValid = name.trim().isNotBlank() && phoneNumber.filter { it.isDigit() }.length in 7..15

    // Button press scale
    val ctaInteractionSource = remember { MutableInteractionSource() }
    val isCtaPressed by ctaInteractionSource.collectIsPressedAsState()
    val ctaScale by animateFloatAsState(
        targetValue = if (isCtaPressed && isFormValid && !isSubmitting) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "cta_scale"
    )

    fun triggerShake() {
        coroutineScope.launch {
            phoneError = true
            shakeOffset.animateTo(-12f, animationSpec = tween(50))
            shakeOffset.animateTo(12f, animationSpec = tween(50))
            shakeOffset.animateTo(-8f, animationSpec = tween(50))
            shakeOffset.animateTo(8f, animationSpec = tween(50))
            shakeOffset.animateTo(0f, animationSpec = tween(50))
        }
    }

    fun submitDirectToHome() {
        focusManager.clearFocus()
        val digits = phoneNumber.filter { it.isDigit() }
        if (name.isBlank() || digits.length < 7) {
            triggerShake()
            return
        }

        phoneError = false
        isSubmitting = true
        val fullPhone = if (digits == "0696102700" || digits == "696102700") "0696102700" else "${selectedCountry.dialCode}$digits"

        // Anonymous Auth + store name & phone into Firestore, then go directly to Home
        authViewModel.signInAnonymouslyWithProfile(
            userName = name.trim(),
            phoneNumber = fullPhone,
            onSuccess = {
                isSubmitting = false
                onNavigateToHome()
            },
            onError = {
                // Graceful fallback: navigate directly to Home so user experience is smooth
                isSubmitting = false
                onNavigateToHome()
            }
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0C0D10))
            .testTag("onboarding_root")
    ) {
        // 1. Full-bleed background poster image (scaled to fill screen fast with memory cache)
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(AUTH_BACKGROUND_IMAGE_URL)
                .crossfade(250)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .build(),
            contentDescription = "MovieRoom Cinema Poster",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // 2. Multi-layer cinematic dark gradient overlays for high legibility
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Black.copy(alpha = 0.20f),
                            Color.Black.copy(alpha = 0.40f),
                            Color(0xE60A0B0E),
                            Color(0xF80A0B0E)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // 3. Main Content Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar
            Column(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Official Logo top-left
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.testTag("top_brand_glyph")
                    ) {
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(MainActivity.APP_LOGO_URL)
                                .crossfade(true)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .build(),
                            contentDescription = "MovieRoom Logo",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "MovieRoom",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    }

                    // Skip control top-right
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.20f))
                            .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape)
                            .clickable { onNavigateToHome() }
                            .testTag("btn_skip"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Skip to Home",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            // Middle breathing spacer so poster artwork shines in upper half
            Spacer(modifier = Modifier.height(140.dp))

            // Lower Section: Headline, Inputs & CTA
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Headline
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = "Your movies.\nAnywhere.",
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        fontSize = 36.sp,
                        lineHeight = 40.sp,
                        letterSpacing = (-0.5).sp,
                        color = Color.White,
                        textAlign = TextAlign.Start,
                        modifier = Modifier.testTag("headline_text")
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "No buffering. No limits.",
                        fontWeight = FontWeight.Normal,
                        fontSize = 15.sp,
                        color = Color.White.copy(alpha = 0.75f),
                        textAlign = TextAlign.Start,
                        modifier = Modifier.testTag("sub_copy_text")
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Name Card Input
                val nameElevation by animateDpAsState(
                    targetValue = if (isNameFocused) 6.dp else 2.dp,
                    label = "name_elevation"
                )
                val nameBorderColor by animateColorAsState(
                    targetValue = if (isNameFocused) EditorialAccentRust else EditorialBorderSubtle.copy(alpha = 0.5f),
                    label = "name_border_color"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .shadow(nameElevation, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.5.dp, nameBorderColor, RoundedCornerShape(24.dp)),
                    color = EditorialCardBg,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Name Icon",
                            tint = if (isNameFocused) EditorialAccentRust else EditorialTextMuted,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (name.isEmpty()) {
                                Text(
                                    text = "Your name",
                                    color = EditorialTextMuted,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            BasicTextField(
                                value = name,
                                onValueChange = { name = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = EditorialTextBlack,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(EditorialAccentRust),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Text,
                                    imeAction = ImeAction.Next
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isNameFocused = it.isFocused }
                                    .testTag("name_input_field")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Phone Card Input
                val phoneElevation by animateDpAsState(
                    targetValue = if (isPhoneFocused) 6.dp else 2.dp,
                    label = "phone_elevation"
                )
                val phoneBorderColor by animateColorAsState(
                    targetValue = when {
                        phoneError -> EditorialErrorRed
                        isPhoneFocused -> EditorialAccentRust
                        else -> EditorialBorderSubtle.copy(alpha = 0.5f)
                    },
                    label = "phone_border_color"
                )

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp)
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                        .shadow(phoneElevation, RoundedCornerShape(24.dp))
                        .clip(RoundedCornerShape(24.dp))
                        .border(1.5.dp, phoneBorderColor, RoundedCornerShape(24.dp)),
                    color = EditorialCardBg,
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = "Phone Icon",
                            tint = if (phoneError) EditorialErrorRed else if (isPhoneFocused) EditorialAccentRust else EditorialTextMuted,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        // Country Code Selector
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { showCountryPicker = true }
                                .padding(vertical = 6.dp, horizontal = 4.dp)
                                .testTag("country_code_selector"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedCountry.flag} ${selectedCountry.dialCode}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = EditorialTextBlack
                            )
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = "Select country",
                                tint = EditorialTextMuted,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .height(24.dp)
                                .width(1.dp)
                                .background(EditorialBorderSubtle)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (phoneNumber.isEmpty()) {
                                Text(
                                    text = "Phone number",
                                    color = EditorialTextMuted,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                            BasicTextField(
                                value = phoneNumber,
                                onValueChange = {
                                    phoneNumber = it.filter { ch -> ch.isDigit() || ch == ' ' || ch == '-' }
                                    if (phoneError && phoneNumber.length >= 7) {
                                        phoneError = false
                                    }
                                },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = EditorialTextBlack,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Normal
                                ),
                                cursorBrush = SolidColor(EditorialAccentRust),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Phone,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = {
                                        if (isFormValid) submitDirectToHome() else triggerShake()
                                    }
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isPhoneFocused = it.isFocused }
                                    .testTag("phone_input_field")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Primary CTA Pill Button (Direct to Home)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .scale(ctaScale)
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            if (isFormValid && !isSubmitting) EditorialAccentRust else EditorialDisabledBg
                        )
                        .clickable(
                            interactionSource = ctaInteractionSource,
                            indication = null,
                            enabled = isFormValid && !isSubmitting,
                            onClick = { submitDirectToHome() }
                        )
                        .testTag("btn_continue_auth"),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSubmitting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Entering Cinema...",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    } else {
                        Text(
                            text = "Start Watching",
                            color = if (isFormValid) Color.White else EditorialDisabledText,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Brand Wordmark
                Text(
                    text = "MOVIEROOM",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Normal,
                    letterSpacing = 3.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.testTag("brand_wordmark")
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Country Code Picker Modal
    if (showCountryPicker) {
        ModalBottomSheet(
            onDismissRequest = { showCountryPicker = false },
            containerColor = EditorialCardBg,
            scrimColor = Color.Black.copy(alpha = 0.6f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Text(
                    text = "Select Country",
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = EditorialTextBlack
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(DefaultCountryCodes) { country ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedCountry = country
                                    showCountryPicker = false
                                }
                                .padding(vertical = 12.dp, horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = country.flag, fontSize = 22.sp)
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = country.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Normal,
                                    color = EditorialTextBlack
                                )
                            }
                            Text(
                                text = country.dialCode,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Normal,
                                color = EditorialAccentRust
                            )
                        }
                    }
                }
            }
        }
    }
}
