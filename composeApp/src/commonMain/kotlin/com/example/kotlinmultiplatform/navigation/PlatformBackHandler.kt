package com.example.kotlinmultiplatform.navigation

import androidx.compose.runtime.Composable

/**
 * Platform-specific back handler.
 *
 * androidMain → wraps androidx.activity.compose.BackHandler
 * iosMain     → no-op (back is handled by UIScreenEdgePanGestureRecognizer
 *               registered in RootViewModel.init via registerBackGesture)
 */
@Composable
expect fun PlatformBackHandler(
    enabled: Boolean = true,
    onBack: () -> Unit,
)
