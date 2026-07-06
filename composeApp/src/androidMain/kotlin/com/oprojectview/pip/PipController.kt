package com.oprojectview.pip

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.oprojectview.features.player.PlayerIntent
import com.oprojectview.features.player.PlayerViewModel
import com.oprojectview.frame.FrameViewModel
import com.oprojectview.frame.FrameVmEvent
import com.oprojectview.frame.FrameVmIntent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.app.PendingIntent
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.Icon
import androidx.core.content.ContextCompat

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
class PipController(
    private val activity:        Activity,
    private val frameViewModel: FrameViewModel,
    private val playerViewModel: PlayerViewModel,
    lifecycleOwner:              LifecycleOwner,
) : DefaultLifecycleObserver {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    
    private val ACTION_PLAY_PAUSE = "com.oprojectview.pip.PLAY_PAUSE"
    private var isReceiverRegistered = false

    private val pipReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_PLAY_PAUSE) {
                val isPlaying = playerViewModel.state.value.isPlaying
                playerViewModel.onIntent(PlayerIntent.SetPlaying(!isPlaying))
            }
        }
    }

    init {
        lifecycleOwner.lifecycle.addObserver(this)
        observeFrameEvents()
        observePlayerState()
    }

    private fun observePlayerState() {
        scope.launch {
            playerViewModel.state.collect { state ->
                if (activity.isInPictureInPictureMode) {
                    updatePipParams(state.isPlaying)
                }
            }
        }
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
        val overlayEnabled = frameViewModel.frameStateFlow.value.overlayEnabled

        val frameState  = frameViewModel.frameStateFlow.value
        val contentW    = frameState.frameWidthPx.coerceAtLeast(1)
        val contentH    = frameState.frameHeightPx.coerceAtLeast(1)
        val aspectRatio = Rational(contentW, contentH)

        val params = createPipParams(playerViewModel.state.value.isPlaying, aspectRatio, overlayEnabled)

        activity.enterPictureInPictureMode(params)
        playerViewModel.onIntent(PlayerIntent.EnterPip())
    }

    private fun updatePipParams(isPlaying: Boolean) {
        if (!supportsPip()) return
        val overlayEnabled = frameViewModel.frameStateFlow.value.overlayEnabled
        
        val frameState  = frameViewModel.frameStateFlow.value
        val contentW    = frameState.frameWidthPx.coerceAtLeast(1)
        val contentH    = frameState.frameHeightPx.coerceAtLeast(1)
        val aspectRatio = Rational(contentW, contentH)

        activity.setPictureInPictureParams(createPipParams(isPlaying, aspectRatio, overlayEnabled))
    }

    private fun createPipParams(isPlaying: Boolean, aspectRatio: Rational, overlayEnabled: Boolean): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(aspectRatio)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val iconRes = if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play
            val title = if (isPlaying) "Pause" else "Play"
            val intent = Intent(ACTION_PLAY_PAUSE).setPackage(activity.packageName)
            val pendingIntent = PendingIntent.getBroadcast(
                activity, 
                0, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val action = RemoteAction(Icon.createWithResource(activity, iconRes), title, title, pendingIntent)
            builder.setActions(listOf(action))
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(overlayEnabled && isPlaying)
            builder.setSeamlessResizeEnabled(true)
        }

        return builder.build()
    }

    private fun onPipEnded() {
        playerViewModel.onIntent(PlayerIntent.ExitPip)
        frameViewModel.onIntent(FrameVmIntent.SetPlaying(false))
    }

    // ── Lifecycle callbacks ───────────────────────────────────────────────────

    override fun onStart(owner: LifecycleOwner) {
        super.onStart(owner)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!isReceiverRegistered) {
                ContextCompat.registerReceiver(
                    activity,
                    pipReceiver,
                    IntentFilter(ACTION_PLAY_PAUSE),
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                isReceiverRegistered = true
            }
        }
    }

    override fun onStop(owner: LifecycleOwner) {
        super.onStop(owner)
        if (isReceiverRegistered) {
            activity.unregisterReceiver(pipReceiver)
            isReceiverRegistered = false
        }
    }

    override fun onPause(owner: LifecycleOwner) {
        if (activity.isInPictureInPictureMode) {
            playerViewModel.onIntent(PlayerIntent.EnterPip())
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
        super.onDestroy(owner)
        if (isReceiverRegistered) {
            activity.unregisterReceiver(pipReceiver)
            isReceiverRegistered = false
        }
        scope.cancel()
    }

    fun release() {
        scope.cancel()
    }

    private fun supportsPip(): Boolean =
        activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
}
