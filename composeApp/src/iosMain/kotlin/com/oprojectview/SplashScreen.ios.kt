@file:OptIn(ExperimentalResourceApi::class)

package com.oprojectview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.ExperimentalResourceApi
import kotlinx.coroutines.delay

@Composable
actual fun PlatformSplashScreen(content: @Composable () -> Unit) {
    var showSplash by remember { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        // Content renders underneath the splash screen immediately, so there's no layout jump.
        content()

        AnimatedVisibility(
            visible = showSplash,
            enter = androidx.compose.animation.EnterTransition.None,
            exit = fadeOut(tween(durationMillis = 200))
        ) {
            IosSplashScreenOverlay(onComplete = { showSplash = false })
        }
    }
}

@Composable
private fun IosSplashScreenOverlay(onComplete: () -> Unit) {
    // Drive the pure Compose native animation from 0f to 1f
    val progress = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Timing sequence matching user requirements:
        // 1. Play effect: 600ms
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 600, easing = LinearEasing)
        )
        
        // 2. Pause: 600ms
        delay(600)
        
        // 3. Fade out triggers (200ms duration)
        onComplete()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFFF0000)), // Mirroring Android 12 Splash Screen Background
        contentAlignment = Alignment.Center
    ) {
        AnimatedAppIcon(
            progress = progress.value,
            modifier = Modifier.size(256.dp) // Adjusted to user preference
        )
    }
}
