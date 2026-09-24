package com.example.presentation.navigation

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

@OptIn(ExperimentalSharedTransitionApi::class)
val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

val LocalNavAnimatedVisibilityScope = staticCompositionLocalOf<AnimatedVisibilityScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
val MaterialSharedElementBoundsTransform: BoundsTransform = BoundsTransform { _, _ ->
    tween(
        durationMillis = 380,
        easing = FastOutSlowInEasing
    )
}

/**
 * Shared Element modifier for movie poster/artwork.
 * Seamlessly morphs between source movie card in the list and destination in movie detail.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.movieSharedElement(
    key: String,
    sharedTransitionScope: SharedTransitionScope? = LocalSharedTransitionScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope? = LocalNavAnimatedVisibilityScope.current
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this
    return with(sharedTransitionScope) {
        sharedElement(
            state = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
            boundsTransform = MaterialSharedElementBoundsTransform
        )
    }
}

/**
 * Shared Bounds modifier for container transforms, text titles, and card surfaces.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.movieSharedBounds(
    key: String,
    sharedTransitionScope: SharedTransitionScope? = LocalSharedTransitionScope.current,
    animatedVisibilityScope: AnimatedVisibilityScope? = LocalNavAnimatedVisibilityScope.current,
    enter: EnterTransition = fadeIn(tween(300)),
    exit: ExitTransition = fadeOut(tween(250))
): Modifier {
    if (sharedTransitionScope == null || animatedVisibilityScope == null) return this
    return with(sharedTransitionScope) {
        sharedBounds(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
            enter = enter,
            exit = exit,
            boundsTransform = MaterialSharedElementBoundsTransform
        )
    }
}
