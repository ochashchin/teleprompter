package com.oprojectview

import androidx.compose.runtime.Composable

/**
 * Platform-specific splash screen wrapper.
 * On Android, this just returns the content, as the native Splash Screen API is used.
 * On iOS, this overlays a custom Compose-based splash screen mimicking the Android layout.
 */
@Composable
expect fun PlatformSplashScreen(content: @Composable () -> Unit)
