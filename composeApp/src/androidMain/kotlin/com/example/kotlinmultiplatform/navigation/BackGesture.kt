package com.example.kotlinmultiplatform.navigation

// Android no-op.
// AppRoot.kt's BackHandler { viewModel.handleBack() } already intercepts
// both the gesture-navigation swipe and the hardware/software back button
// on every Android version. Nothing extra is needed here.
actual fun registerBackGesture(onBack: () -> Unit) = Unit
