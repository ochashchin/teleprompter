package com.example.kotlinmultiplatform

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.example.kotlinmultiplatform.navigation.RootViewModel
import com.example.kotlinmultiplatform.navigation.registerBackGesture
import platform.UIKit.UIViewController

/**
 * iOS entry point.
 *
 * RootViewModel is hoisted here so the same instance is shared between
 * App() (navigation) and registerBackGesture (left-edge swipe).
 * If it were created inside App() via remember{}, the gesture registration
 * in LaunchedEffect could not reach it.
 */
fun MainViewController(): UIViewController = ComposeUIViewController {

    val rootViewModel = remember { RootViewModel() }

    // Retry after first frame — keyWindow may not be ready on cold start.
    // The iosMain actual guards against double-registration.
    LaunchedEffect(Unit) {
        registerBackGesture { rootViewModel.handleBack() }
    }

    App(
        rootViewModel = rootViewModel,
        onExitApp     = { /* iOS: home button exits — no-op is correct */ },
    )
}
