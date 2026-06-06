package com.example.kotlinmultiplatform.navigation

/**
 * Platform back-gesture registration.
 *
 * commonMain declares the contract.
 * androidMain → no-op (BackHandler in AppRoot already covers Android).
 * iosMain     → attaches UIScreenEdgePanGestureRecognizer to the key window.
 *
 * Called once from [RootViewModel.init] so the ViewModel drives registration,
 * not the Compose layer.
 *
 * @param onBack  Called on the main thread when the left-edge swipe is confirmed.
 */
expect fun registerBackGesture(onBack: () -> Unit)
