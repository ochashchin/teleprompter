package com.example.kotlinmultiplatform.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import android.view.Surface
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.example.kotlinmultiplatform.frame.FrameVmEvent
import com.example.kotlinmultiplatform.frame.FrameVmIntent
import com.example.kotlinmultiplatform.frame.FrameViewModel
import com.example.kotlinmultiplatform.features.player.PlayerIntent
import com.example.kotlinmultiplatform.features.player.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android-only lifecycle observer that bridges [FrameViewModel] events into
 * actual PiP system calls.
 *
 * ## Bug 3 fix — `autoEnterEnabled` was unconditional
 *
 * The original code called `setAutoEnterEnabled(true)` inside `enterPip()`,
 * which runs whenever [FrameVmEvent.RequestPip] is received.  The system
 * interprets `autoEnterEnabled = true` as "enter PiP on ANY onPause", so PiP
 * would activate even when the user never enabled the overlay dropdown.
 *
 * ### Fix
 * `autoEnterEnabled` is now **only** set to `true` when overlay is confirmed
 * on by the [FrameViewModel] state at the moment `enterPip()` is called.
 * It is explicitly reset to `false` in [onResume] so a subsequent navigation
 * without overlay enabled can never re-trigger the auto-enter path.
 *
 * ## PiP gate rules (from spec)
 *
 * **PlayerScreen:**
 * - Overlay dropdown = Enabled + Play button pressed → PiP activates.
 *
 * **DisplayScreen:**
 * - Overlay dropdown = Enabled + Play button pressed
 *   → app backgrounds automatically
 *   → PiP starts
 *   → PiP renders PlayerScreen pipeline via [SurfaceViewRoute].
 *
 * Both paths funnel through [FrameViewModel.onIntent(FrameVmIntent.SetPlaying)]
 * which emits [FrameVmEvent.RequestPip] ONLY when `overlayEnabled == true`.
 *
 * ## Usage (in MainActivity.onCreate)
 * ```kotlin
 * PipController(
 *     activity        = this,
 *     frameViewModel  = frameViewModel,
 *     playerViewModel = playerViewModel,
 *     lifecycleOwner  = this,
 * )
 * ```
 * Lifecycle cleanup is automatic via [DefaultLifecycleObserver.onDestroy].
 */
@RequiresApi(Build.VERSION_CODES.O)
class PipController(
    private val activity:        Activity,
    private val frameViewModel:  FrameViewModel,
    private val playerViewModel: PlayerViewModel,
    lifecycleOwner:              LifecycleOwner,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        observeFrameEvents()
    }

    // ── Observe FrameViewModel events ─────────────────────────────────────────

    private fun observeFrameEvents() {
        scope.launch {
            frameViewModel.events.collect { event ->
                when (event) {
                    is FrameVmEvent.RequestPip -> enterPip()
                    is FrameVmEvent.PipEnded   -> onPipEnded()
                    else                       -> Unit
                }
            }
        }
    }

    // ── Enter PiP ─────────────────────────────────────────────────────────────

    private fun enterPip() {
        if (!supportsPip()) return

        // Bug 3 fix: read overlayEnabled from the live FrameState.
        // autoEnterEnabled must only be true when overlay is actually on —
        // otherwise pressing Home with overlay OFF would still enter PiP.
        val overlayEnabled = frameViewModel.frameStateFlow.value.overlayEnabled

        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(9, 16))
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // autoEnterEnabled = true covers the DisplayScreen → background → PiP flow.
                    // Bug 3 fix: ONLY set it when overlay is enabled.
                    setAutoEnterEnabled(overlayEnabled)
                    setSeamlessResizeEnabled(true)
                }
            }
            .build()

        activity.enterPictureInPictureMode(params)
        playerViewModel.onIntent(PlayerIntent.EnterPip)
    }

    private fun onPipEnded() {
        playerViewModel.onIntent(PlayerIntent.ExitPip)
        frameViewModel.onIntent(FrameVmIntent.SetPlaying(false))
    }

    // ── Lifecycle callbacks ───────────────────────────────────────────────────

    override fun onPause(owner: LifecycleOwner) {
        if (activity.isInPictureInPictureMode) {
            playerViewModel.onIntent(PlayerIntent.EnterPip)
        }
        // If NOT in PiP after onPause, the user backgrounded the app without
        // overlay enabled — do nothing.  The old code set autoEnterEnabled(true)
        // unconditionally here, which was Bug 3.
    }

    override fun onResume(owner: LifecycleOwner) {
        if (!activity.isInPictureInPictureMode) {
            playerViewModel.onIntent(PlayerIntent.ExitPip)
            frameViewModel.onIntent(FrameVmIntent.SetPlaying(false))

            // Bug 3 fix: clear autoEnterEnabled so the next onPause without
            // overlay enabled does NOT re-enter PiP.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val params = PictureInPictureParams.Builder()
                    .setAutoEnterEnabled(false)
                    .build()
                activity.setPictureInPictureParams(params)
            }
        }
    }

    override fun onDestroy(owner: LifecycleOwner) {
        release()
    }

    fun release() {
        scope.cancel()
    }

    private fun supportsPip(): Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}
