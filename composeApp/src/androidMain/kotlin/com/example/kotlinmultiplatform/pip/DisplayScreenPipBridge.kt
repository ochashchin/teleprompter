package com.example.kotlinmultiplatform.pip

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.example.kotlinmultiplatform.DisplayTaskState
import com.example.kotlinmultiplatform.frame.FrameVmIntent
import com.example.kotlinmultiplatform.frame.FrameViewModel
import com.example.kotlinmultiplatform.features.display.DisplayEvent
import com.example.kotlinmultiplatform.features.display.DisplayViewModel

/**
 * Composable hook placed inside [AppRoot]'s [Screen.DisplayScreen] branch.
 *
 * ## Flow (overlay OFF — normal path, unchanged)
 * ```
 * Play pressed
 *   → displayVm.onIntent(PlayClicked)
 *   → DisplayEvent.NavigateToPlay
 *   → AppRoot: playerVm.load() + viewModel.goToPlayer()
 * ```
 *
 * ## Flow (overlay ON — PiP path)
 * ```
 * Play pressed
 *   → displayVm.onIntent(PlayClicked)
 *   → DisplayEvent.NavigateToPlay
 *   → THIS bridge intercepts:
 *       1. Reads all 8 DisplayTaskState indices.
 *       2. frameVm.SyncDisplayState(...)  ← translate to FrameState
 *       3. frameVm.SetOverlay(true)
 *       4. frameVm.SetPlaying(true)       ← emits RequestPip
 *          → PipController calls Activity.enterPictureInPictureMode()
 *          → app backgrounds → PiP window opens → PlayerScreen pipeline runs
 *   → Normal navigation ALSO proceeds (AppRoot still pushes PlayerScreen)
 *     so if the user later dismisses PiP they see the full-screen player.
 * ```
 *
 * ## How to wire into AppRoot
 *
 * Inside the `is Screen.DisplayScreen` branch of [AppRoot.dynamicContent],
 * call this composable alongside [DisplayScreenBody]:
 *
 * ```kotlin
 * is Screen.DisplayScreen -> {
 *     val task = displayState.task?.let { … } ?: return@AnimatedContent
 *     DisplayScreenBody(task = task, onPlayClick = { displayVm.onIntent(DisplayIntent.PlayClicked) })
 *
 *     // Android-only PiP bridge (no-op on other platforms if wrapped in expect/actual)
 *     DisplayScreenPipBridge(
 *         displayState  = rememberDisplayTaskState(task.id),
 *         displayVm     = displayVm,
 *         frameVm       = frameViewModel,
 *     )
 * }
 * ```
 */
@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun DisplayScreenPipBridge(
    displayState: DisplayTaskState,
    displayVm:    DisplayViewModel,
    frameVm:      FrameViewModel,
) {
    LaunchedEffect(displayVm) {
        displayVm.events.collect { event ->
            if (event !is DisplayEvent.NavigateToPlay) return@collect

            // Bug 1 / Bug 3 fix: overlayEnabled is now carried directly on the
            // NavigateToPlay event.  DisplayViewModel receives it from
            // DisplayScreenBody.onPlayClick(selectedOverlayIndex == 1), which
            // reads the live Compose state — not a stale remember(task.id) snapshot.
            // We no longer re-read DisplayTaskState here; that was the source of
            // the staleness in the original DisplayScreenPipBridge.
            val overlayOn = event.overlayEnabled

            if (!overlayOn) return@collect   // normal play path — let AppRoot handle it

            // ── PiP path ──────────────────────────────────────────────────────
            // 1. Sync all display settings into the frame pipeline.
            frameVm.onIntent(FrameVmIntent.SyncDisplayState(displayState))
            // 2. Confirm overlay flag (SyncDisplayState reads it from Settings
            //    but we set it explicitly to be safe).
            frameVm.onIntent(FrameVmIntent.SetOverlay(true))
            // 3. Start playing → emits RequestPip → PipController enters PiP.
            //    Bug 3 is fixed in PipController: autoEnterEnabled is only set
            //    when overlayEnabled is true.
            frameVm.onIntent(FrameVmIntent.SetPlaying(true))

            // AppRoot's event collector still fires NavigateToPlay → goToPlayer()
            // so the full-screen PlayerScreen is also pushed onto the stack.
            // When PiP is dismissed the user lands on it naturally.
        }
    }
}
