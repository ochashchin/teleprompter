@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)
@file:Suppress("CANNOT_ACCESS_CLASS_NAMED", "UNCHECKED_CAST", "CAST_NEVER_SUCCEEDS")

package com.oprojectview.pip

import com.oprojectview.features.player.PlayerIntent
import com.oprojectview.features.player.PlayerViewModel
import com.oprojectview.frame.FrameViewModel
import com.oprojectview.frame.FrameVmEvent
import com.oprojectview.frame.FrameVmIntent
import com.oprojectview.frame.PipSizeClass
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import platform.AVFoundation.AVLayerVideoGravityResize
import platform.AVFoundation.AVSampleBufferDisplayLayer
import platform.AVKit.AVPictureInPictureController
import platform.AVKit.AVPictureInPictureControllerContentSource
import platform.AVKit.AVPictureInPictureControllerDelegateProtocol
import platform.AVKit.AVPictureInPictureSampleBufferPlaybackDelegateProtocol
import platform.AVKit.create
import kotlinx.cinterop.cValue
import platform.CoreMedia.CMTime
import platform.CoreMedia.CMTimeRange
import platform.CoreMedia.CMVideoDimensions
import platform.CoreMedia.CMTimebaseRef
import platform.CoreMedia.CMTimebaseRefVar
import platform.CoreMedia.CMClockGetHostTimeClock
import platform.CoreMedia.CMTimebaseCreateWithSourceClock
import platform.CoreMedia.CMTimebaseSetTime
import platform.CoreMedia.CMTimebaseSetRate
import platform.CoreMedia.kCMTimeZero
import platform.CoreMedia.kCMTimePositiveInfinity
import platform.CoreMedia.CMTimeRangeMake
import platform.CoreFoundation.CFRelease
import platform.Foundation.NSError
import platform.Foundation.NSNotificationCenter
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDidBecomeActiveNotification
import platform.UIKit.UIScreen
import platform.Foundation.NSSelectorFromString
import platform.darwin.NSObject
import kotlinx.cinterop.alloc
import kotlinx.cinterop.ptr
import kotlinx.cinterop.value
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readValue

class IosPipController(
    private val frameViewModel: FrameViewModel,
) {
    val displayLayer: AVSampleBufferDisplayLayer = AVSampleBufferDisplayLayer().also {
        it.videoGravity = AVLayerVideoGravityResize
    }

    var frameSink: IosFrameSink? = null
        set(value) {
            field = value
            value?.getPlaybackState = { frameViewModel.frameStateFlow.value }
            value?.onReadingCompleted = {
                if (!hasDispatchedReadingCompleted) {
                    hasDispatchedReadingCompleted = true
                    println("[PiP] Dispatching ReadingCompleted because scroll ended")
                    playerViewModel?.onIntent(PlayerIntent.ReadingCompleted)
                }
            }
            value?.onReadingStartedOrReset = {
                if (hasDispatchedReadingCompleted) {
                    hasDispatchedReadingCompleted = false
                    println("[PiP] Resetting hasDispatchedReadingCompleted because scroll started or reset")
                }
            }
        }

    @kotlin.concurrent.Volatile
    private var playerViewModel: PlayerViewModel? = null

    private var pipController: AVPictureInPictureController? = null
    private val sizeHeuristics = IosPipSizeHeuristics()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controlTimebase: CMTimebaseRef? = null

    private val pipDelegate = PipDelegate()
    private val playbackDelegate = PlaybackDelegate()

    private var firstFrameEnqueued = false
    private var pipTransitionCompleted = false
    @kotlin.concurrent.Volatile
    private var isPipStarting = false  // guard against rapid repeated taps
    // Fix #3 — auto-switch: fire ReadingCompleted exactly once per task
    private var hasDispatchedReadingCompleted = false

    init {
        observeEvents()

        // Dismiss PiP automatically when the app returns to foreground
        NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationDidBecomeActiveNotification,
            `object` = null,
            queue = platform.Foundation.NSOperationQueue.mainQueue,
        ) { _ ->
            if (pipController?.isPictureInPictureActive() == true) {
                stopPip()
            }
        }
    }

    fun attachPlayerViewModel(vm: PlayerViewModel) {
        playerViewModel = vm
        var lastTaskId: Int? = null
        scope.launch {
            vm.state.collect { pState ->
                val t = pState.task ?: return@collect

                // Fix #3 — on task change dispatch SyncPlayerState so PiP shows new text.
                // Preserve existing fillColorVal so we don't blank the background.
                val taskChanged = t.id != lastTaskId
                if (taskChanged) {
                    lastTaskId = t.id
                    val currentFill = frameViewModel.frameStateFlow.value.fillColorVal
                    frameViewModel.onIntent(
                        FrameVmIntent.SyncPlayerState(
                            isPlaying                = pState.isPlaying,
                            countdownDone            = pState.countdownDone,
                            countdownStartUs         = pState.countdownStartUs,
                            pausedCountdownElapsedUs = pState.pausedCountdownElapsedUs,
                            scrollFraction           = 0f,
                            scriptText               = t.description,
                            styleSpans               = emptyList(),
                            fillColorVal             = currentFill,
                            animationMode            = com.oprojectview.AnimationMode.Scroll,
                            transitionMode           = com.oprojectview.TransitionMode.None,
                            playbackStartUs          = pState.playbackStartUs,
                            pausedElapsedUs          = pState.pausedElapsedUs,
                            totalDurationMs          = pState.totalDurationMs
                        )
                    )
                } else {
                    // Fix #4 — keep timebase in sync with actual playback position.
                    // Compute elapsed seconds and push them into the controlTimebase
                    // so the native progress bar reflects real script progress.
                    val elapsedUs = if (pState.countdownDone) {
                        val elapsed = if (pState.isPlaying && pState.playbackStartUs > 0L) {
                            (com.oprojectview.frame.FrameProducer.currentTimeUs() - pState.playbackStartUs).coerceAtLeast(0L)
                        } else {
                            pState.pausedElapsedUs
                        }
                        6_000_000L + elapsed
                    } else {
                        if (pState.isPlaying && pState.countdownStartUs > 0L) {
                            (com.oprojectview.frame.FrameProducer.currentTimeUs() - pState.countdownStartUs).coerceAtLeast(0L)
                        } else {
                            pState.pausedCountdownElapsedUs
                        }
                    }
                    val elapsedSec = elapsedUs.toDouble() / 1_000_000.0
                    controlTimebase?.let { tb ->
                        CMTimebaseSetTime(
                            tb,
                            platform.CoreMedia.CMTimeMakeWithSeconds(elapsedSec, preferredTimescale = 1000)
                        )
                    }

                    frameViewModel.onIntent(
                        FrameVmIntent.SyncPlayerPlaybackState(
                            isPlaying                = pState.isPlaying,
                            countdownDone            = pState.countdownDone,
                            countdownStartUs         = pState.countdownStartUs,
                            pausedCountdownElapsedUs = pState.pausedCountdownElapsedUs,
                            scrollFraction           = pState.scrollFraction,
                            playbackStartUs          = pState.playbackStartUs,
                            pausedElapsedUs          = pState.pausedElapsedUs
                        )
                    )
                }
            }
        }
    }

    private fun observeEvents() {
        scope.launch {
            frameViewModel.events.collect { event ->
                when (event) {
                    is FrameVmEvent.RequestPip -> startPip()
                    is FrameVmEvent.PipEnded   -> stopPip()
                }
            }
        }
        scope.launch {
            frameViewModel.frameStateFlow.collect { state ->
                updateTimebaseRate(if (state.isPlaying) 1.0 else 0.0)
            }
        }
    }

    private fun updateTimebaseRate(rate: Double) {
        val tb = controlTimebase ?: return
        CMTimebaseSetRate(tb, rate)
        println("[PiP] Updated controlTimebase rate to $rate")
    }

    private fun startPip() {
        if (!AVPictureInPictureController.isPictureInPictureSupported()) return
        if (pipController?.isPictureInPictureActive() == true) {
            println("[PiP] startPip() ignored — PiP is already active")
            return
        }
        if (isPipStarting) {
            println("[PiP] startPip() ignored — already starting")
            return
        }
        isPipStarting = true

        firstFrameEnqueued = false
        pipTransitionCompleted = false
        hasDispatchedReadingCompleted = false

        // Fix #1 — Do NOT call SetPlaying(true) here.
        // Spinner timing: overlay + EnterPip set up the FrameState so the spinner
        // renders correctly. SetPlaying is deferred to didStartPictureInPicture so
        // the countdown begins only after the PiP window is actually open.
        playerViewModel?.onIntent(PlayerIntent.EnterPip(isNativePip = true))
        frameViewModel.onIntent(FrameVmIntent.SetOverlay(true))

        // Create AVPictureInPictureController FIRST — this links AVKit's video pipeline
        // to the display layer, so frames enqueued after this are properly decoded
        // and readyForDisplay will flip to true.
        if (pipController == null) {
            val contentSource = AVPictureInPictureControllerContentSource.create(
                sampleBufferDisplayLayer = displayLayer,
                playbackDelegate         = playbackDelegate
            )
            pipController = AVPictureInPictureController(contentSource = contentSource).apply {
                delegate = pipDelegate
                canStartPictureInPictureAutomaticallyFromInline = true
                requiresLinearPlayback = true
            }
        }

        if (controlTimebase == null) {
            val masterClock = CMClockGetHostTimeClock()
            memScoped {
                val outTimebase = alloc<CMTimebaseRefVar>()
                val status = CMTimebaseCreateWithSourceClock(
                    allocator = null,
                    sourceClock = masterClock,
                    timebaseOut = outTimebase.ptr
                )
                if (status == 0) {
                    controlTimebase = outTimebase.value
                    val tb = outTimebase.value
                    CMTimebaseSetTime(tb, kCMTimeZero.readValue())
                    val initialRate = if (frameViewModel.frameStateFlow.value.isPlaying) 1.0 else 0.0
                    CMTimebaseSetRate(tb, initialRate)
                    displayLayer.controlTimebase = tb
                    println("[PiP] Created controlTimebase with initial rate $initialRate")
                } else {
                    println("[PiP] CMTimebaseCreateWithSourceClock failed: $status")
                }
            }
        }

        // NOW enqueue a placeholder — AVKit's pipeline is active and will
        // decode this frame and set readyForDisplay = true.
        frameSink?.enqueuePlaceholderFrame()

        // Callback for when the first real frame is enqueued (used to suspend the app)
        frameSink?.onFirstFrameEnqueued = {
            firstFrameEnqueued = true
            maybeSuspendApp()
        }

        // Wait for the hardware decoder to process the placeholder and set readyForDisplay = true.
        // We poll isPictureInPicturePossible() because it may take a few run loop turns.
        scope.launch {
            var attempts = 0
            while (attempts < 20) {
                if (pipController?.isPictureInPicturePossible() == true) {
                    println("[PiP] isPictureInPicturePossible is true! Starting PiP...")
                    pipController?.startPictureInPicture()
                    return@launch
                }
                delay(100)
                attempts++
            }
            println("[PiP] Timeout waiting for PiP to become possible")
        }
    }

    private fun maybeSuspendApp() {
        if (firstFrameEnqueued && pipTransitionCompleted) {
            val sharedApp = UIApplication.sharedApplication
            val selector = NSSelectorFromString("suspend")
            if (sharedApp.respondsToSelector(selector)) {
                sharedApp.performSelector(selector)
            }
        }
    }

    private fun stopPip() {
        isPipStarting = false
        updateTimebaseRate(0.0)
        frameViewModel.onIntent(FrameVmIntent.SetOverlay(false))
        frameViewModel.onIntent(FrameVmIntent.ReleasePipSizeOverride)
        val (nativeW, nativeH) = UIScreen.mainScreen.nativeBounds.useContents {
            Pair(size.width, size.height)
        }
        val sourceW = (nativeW * 0.5).toInt().coerceAtLeast(1)
        val sourceH = (nativeH * 0.5).toInt().coerceAtLeast(1)
        frameViewModel.onIntent(FrameVmIntent.SetFrameSize(sourceW, sourceH))
        pipController?.stopPictureInPicture()

        playerViewModel?.let { pvm ->
            pvm.onIntent(PlayerIntent.SetPlaying(false))
            pvm.onIntent(PlayerIntent.ExitPip)
        }

        frameViewModel.onIntent(FrameVmIntent.SetPlaying(false))
        frameViewModel.onIntent(FrameVmIntent.SetSizeClass(PipSizeClass.Compact))
    }

    private fun onRenderSizeChanged(widthPx: Double, heightPx: Double) {
        val sizeClass = sizeHeuristics.evaluate(widthPx, heightPx)
        frameSink?.currentSizeClass = sizeClass
        frameViewModel.onIntent(FrameVmIntent.SetTargetFps(sizeClass.targetFps))
        frameViewModel.onIntent(FrameVmIntent.SetSizeClass(sizeClass))

        // Use the raw dimensions (which are physical pixels for the PiP window)
        // so that the canvas is perfectly 1:1 with the physical PiP size.
        // Combined with Density(scale), this ensures text is rendered at the exact
        // same physical height as the foreground preview.
        val targetW = widthPx.toInt().coerceAtLeast(1)
        val targetH = heightPx.toInt().coerceAtLeast(1)
        frameViewModel.onIntent(
            FrameVmIntent.SetFrameSize(
                widthPx  = targetW,
                heightPx = targetH,
                pipOwned = true,
            )
        )
    }

    fun release() {
        pipController?.stopPictureInPicture()
        pipController = null
        controlTimebase?.let { tb ->
            CFRelease(tb)
        }
        controlTimebase = null
        scope.cancel()
    }

    // ── PipDelegate ───────────────────────────────────────────────────────────

    private inner class PipDelegate : NSObject(), AVPictureInPictureControllerDelegateProtocol {
        override fun pictureInPictureControllerDidStartPictureInPicture(
            pictureInPictureController: AVPictureInPictureController,
        ) {
            displayLayer.bounds.useContents {
                println("[PiP] Did start picture in picture. Initial bounds = ${size.width} x ${size.height}")
            }
            isPipStarting = false
            pipTransitionCompleted = true
            // Fix #1 — start playback NOW that the PiP window is open.
            // The spinner will have been visible during the open animation;
            // starting playback here ensures correct countdown timing.
            frameViewModel.onIntent(FrameVmIntent.SetPlaying(true))
            playerViewModel?.onIntent(PlayerIntent.SetPlaying(true))
            maybeSuspendApp()
        }

        override fun pictureInPictureControllerDidStopPictureInPicture(
            pictureInPictureController: AVPictureInPictureController,
        ) { stopPip() }

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            failedToStartPictureInPictureWithError: NSError,
        ) {
            println("[PiP] failed: ${failedToStartPictureInPictureWithError.localizedDescription}")
            isPipStarting = false
            playerViewModel?.onIntent(PlayerIntent.ExitPip)
        }
    }

    // ── PlaybackDelegate ──────────────────────────────────────────────────────

    private inner class PlaybackDelegate :
        NSObject(),
        AVPictureInPictureSampleBufferPlaybackDelegateProtocol {

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            setPlaying: Boolean,
        ) {
            // Fix #2 — correct pause/resume timestamps so FrameProducer doesn't jump.
            // Mirror exactly what PlayerViewModel.onPlay/onPause does.
            val now = com.oprojectview.frame.FrameProducer.currentTimeUs()
            val fs  = frameViewModel.frameStateFlow.value
            if (setPlaying) {
                // Resume: shift playbackStartUs forward by the paused duration
                val newStartUs = now - fs.pausedElapsedUs
                frameViewModel.onIntent(
                    FrameVmIntent.SyncPlayerPlaybackState(
                        isPlaying                = true,
                        countdownDone            = fs.countdownDone,
                        countdownStartUs         = if (!fs.countdownDone) now - fs.pausedCountdownElapsedUs else fs.countdownStartUs,
                        pausedCountdownElapsedUs = fs.pausedCountdownElapsedUs,
                        scrollFraction           = fs.scrollFraction,
                        playbackStartUs          = newStartUs,
                        pausedElapsedUs          = fs.pausedElapsedUs
                    )
                )
            } else {
                // Pause: snapshot elapsed time
                val elapsed = now - fs.playbackStartUs
                frameViewModel.onIntent(
                    FrameVmIntent.SyncPlayerPlaybackState(
                        isPlaying                = false,
                        countdownDone            = fs.countdownDone,
                        countdownStartUs         = fs.countdownStartUs,
                        pausedCountdownElapsedUs = if (!fs.countdownDone) now - fs.countdownStartUs else fs.pausedCountdownElapsedUs,
                        scrollFraction           = fs.scrollFraction,
                        playbackStartUs          = fs.playbackStartUs,
                        pausedElapsedUs          = elapsed.coerceAtLeast(0L)
                    )
                )
            }
            playerViewModel?.onIntent(PlayerIntent.SetPlaying(setPlaying))
        }

        override fun pictureInPictureControllerIsPlaybackPaused(
            pictureInPictureController: AVPictureInPictureController,
        ): Boolean = !frameViewModel.frameStateFlow.value.isPlaying

        override fun pictureInPictureControllerTimeRangeForPlayback(
            pictureInPictureController: AVPictureInPictureController,
        ): CValue<CMTimeRange> {
            val durationMs = (6000L + frameViewModel.frameStateFlow.value.totalDurationMs).coerceAtLeast(1000L)
            val durationSeconds = durationMs / 1000.0
            val durationTime = platform.CoreMedia.CMTimeMakeWithSeconds(
                durationSeconds, 
                preferredTimescale = 1000
            )
            return platform.CoreMedia.CMTimeRangeMake(
                start = platform.CoreMedia.kCMTimeZero.readValue(), 
                duration = durationTime
            )
        }

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            skipByInterval: CValue<CMTime>,
            completionHandler: () -> Unit,
        ) { completionHandler() }

        override fun pictureInPictureController(
            pictureInPictureController: AVPictureInPictureController,
            didTransitionToRenderSize: CValue<CMVideoDimensions>,
        ) {
            val (w, h) = didTransitionToRenderSize.useContents {
                Pair(width.toDouble(), height.toDouble())
            }
            displayLayer.bounds.useContents {
                println("[PiP] bounds = ${size.width} x ${size.height} (didTransitionToRenderSize: $w x $h)")
            }
            // Update the display layer bounds so the PiP overlay controls (Play/Pause)
            // remain perfectly centered within the new window dimensions.
            displayLayer.bounds = platform.CoreGraphics.CGRectMake(0.0, 0.0, w, h)
            onRenderSizeChanged(w, h)
        }
    }
}
