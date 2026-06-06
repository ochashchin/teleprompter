package com.example.kotlinmultiplatform.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Platform-agnostic singleton that carries window-mode signals from the
 * platform layer (androidMain Activity) into commonMain ViewModels.
 *
 * Why a singleton and not a CompositionLocal:
 *   onMultiWindowModeChanged fires on the Activity, which has no access to
 *   the Compose tree or its CompositionLocals.  A StateFlow singleton is the
 *   minimal, zero-dependency bridge that works across the boundary.
 *
 * Usage — androidMain (MainActivity.kt):
 *   override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, ...) {
 *       super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
 *       WindowModeObserver.setMultiWindow(isInMultiWindowMode)
 *   }
 *
 * Usage — commonMain (AppNavigation.kt LaunchedEffect):
 *   LaunchedEffect(playerVm) {
 *       WindowModeObserver.isMultiWindow.collect { isMultiWindow ->
 *           playerVm.onIntent(
 *               if (isMultiWindow) PlayerIntent.EnterPip else PlayerIntent.ExitPip
 *           )
 *       }
 *   }
 */
object WindowModeObserver {

    private val _isMultiWindow = MutableStateFlow(false)

    /** True when the app is in split-screen or multi-window mode. */
    val isMultiWindow: StateFlow<Boolean> = _isMultiWindow.asStateFlow()

    /** Called from the platform layer when multi-window mode changes. */
    fun setMultiWindow(active: Boolean) {
        _isMultiWindow.value = active
    }
}
