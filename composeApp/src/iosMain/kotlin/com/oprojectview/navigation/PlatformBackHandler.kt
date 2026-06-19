package com.oprojectview.navigation

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(
    enabled: Boolean,
    onBack: () -> Unit,
) {
    // No-op on iOS.
    // Back is handled natively via UIScreenEdgePanGestureRecognizer,
    // registered in RootViewModel.init through the registerBackGesture
    // expect/actual bridge.
}
