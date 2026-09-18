package com.example.presentation.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GTranslate
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.util.TranslationManager
import com.example.util.TranslationResult
import kotlinx.coroutines.launch

/**
 * Instagram-style Description Composable with On-Demand Swahili Translation.
 *
 * Provides:
 * - Natural display of the original synopsis.
 * - Instagram-style "See translation" / "Tafsiri kwa Kiswahili" button.
 * - Smooth inline translation loading state ("Inatafsiri...").
 * - Instant toggle back to "See original" ("Onyesha ya asili").
 * - Status indicator "Imetafsiriwa kwa Kiswahili".
 */
@Composable
fun SwahiliDescriptionSection(
    originalDescription: String,
    movieTitle: String = "",
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isShowingTranslated by remember { mutableStateOf(false) }
    var translatedText by remember { mutableStateOf<String?>(null) }
    var isLoadingTranslation by remember { mutableStateOf(false) }
    var translationError by remember { mutableStateOf<String?>(null) }

    val interactionSource = remember { MutableInteractionSource() }

    Column(modifier = modifier.fillMaxWidth()) {
        // Synopsis text with smooth fade transition
        AnimatedContent(
            targetState = if (isShowingTranslated && !translatedText.isNullOrBlank()) translatedText!! else originalDescription,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "description_translation_anim"
        ) { textToDisplay ->
            Text(
                text = textToDisplay,
                color = TextSecondary,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Instagram-style action row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            when {
                isLoadingTranslation -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = CinematicRed
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Inatafsiri kwa Kiswahili...",
                        color = CinematicRed,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                isShowingTranslated && !translatedText.isNullOrBlank() -> {
                    // "See original" (Onyesha ya asili) toggle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                isShowingTranslated = false
                            }
                            .testTag("see_original_button")
                    ) {
                        Text(
                            text = "Onyesha ya asili",
                            color = CinematicRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = " (See original)",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }

                    Text(
                        text = "•",
                        color = TextSecondary.copy(alpha = 0.5f),
                        fontSize = 12.sp
                    )

                    Surface(
                        color = Color(0xFF1E293B),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Kiswahili",
                            color = Color(0xFF38BDF8),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.then(Modifier)
                        )
                    }
                }

                else -> {
                    // "See translation" button (Instagram look)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) {
                                if (!translatedText.isNullOrBlank()) {
                                    isShowingTranslated = true
                                } else {
                                    isLoadingTranslation = true
                                    translationError = null
                                    coroutineScope.launch {
                                        when (val result = TranslationManager.translateToSwahili(context, originalDescription, movieTitle)) {
                                            is TranslationResult.Success -> {
                                                translatedText = result.translatedText
                                                isShowingTranslated = true
                                                isLoadingTranslation = false
                                            }
                                            is TranslationResult.Error -> {
                                                translationError = result.message
                                                isLoadingTranslation = false
                                                if (!result.fallbackText.isNullOrBlank()) {
                                                    translatedText = result.fallbackText
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            .testTag("translate_to_swahili_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = "Translate to Swahili",
                            tint = CinematicRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Tafsiri kwa Kiswahili",
                            color = CinematicRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = " (See translation)",
                            color = TextSecondary.copy(alpha = 0.7f),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        if (translationError != null && !isLoadingTranslation && !isShowingTranslated) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = translationError ?: "",
                color = Color(0xFFF87171),
                fontSize = 11.sp
            )
        }
    }
}
