package com.oprojectview

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformSplashScreen(content: @Composable () -> Unit) {
    // On Android, the system handles the splash screen natively (Android 12 Splash Screen API).
    // We just return the content directly.
    content()
}
