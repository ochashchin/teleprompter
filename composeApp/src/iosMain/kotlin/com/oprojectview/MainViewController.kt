@file:OptIn(ExperimentalForeignApi::class, kotlinx.cinterop.BetaInteropApi::class)

package com.oprojectview

import androidx.compose.ui.window.ComposeUIViewController
import androidx.compose.ui.unit.Density
import com.oprojectview.pip.IosFrameSink
import com.oprojectview.pip.IosPipController
import com.oprojectview.frame.CpuTeleprompterFrameRenderer
import com.oprojectview.frame.FrameViewModel
import com.oprojectview.frame.FrameVmIntent
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.UIKit.UIScreen
import platform.UIKit.UIViewController
import platform.UIKit.UIPanGestureRecognizer
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.Foundation.NSSelectorFromString
import platform.darwin.NSObject
import kotlinx.cinterop.ObjCAction
import com.oprojectview.navigation.ExitHandler
import com.oprojectview.navigation.ExitReason
import com.oprojectview.navigation.RootViewModel

/**
 * Scale factor applied to native screen pixels for the PiP source canvas.
 *
 * 0.5 = half resolution: correct aspect ratio at half the memory cost.
 * [AVLayerVideoGravityResizeAspect] on AVSampleBufferDisplayLayer scales
 * frames to fit the PiP viewport while preserving the source aspect ratio.
 */
private const val SOURCE_SCALE = 0.5

/**
 * iOS entry point — called from Swift's ContentView:
 * ```swift
 * MainViewController_iosKt.MainViewController()
 * ```
 *
 * ## Pipeline wired here
 * ```
 * UIScreen.nativeBounds × SOURCE_SCALE  →  SetFrameSize  (source canvas, immutable)
 *
 * FrameCaptureState  ←  Modifier.pipCapture (Compose draw pass, written per-frame)
 *        ↓
 * ComposeCapturingRenderer  (reads latestBitmap on Dispatchers.Default)
 *        ↓
 * FrameViewModel  (MVVM — drives FrameProducer loop)
 *        ↓
 * IosFrameSink  (bitmap → CVPixelBuffer → CMSampleBuffer → enqueueSampleBuffer)
 *        ↓
 * AVSampleBufferDisplayLayer
 *        ↓
 * IosPipController  (AVPictureInPictureController lifecycle)
 * ```
 *
 * ## Why PlayerViewModel is wired via onViewModelsReady, not pre-created here
 *
 * [PlayerViewModel] is created inside [App] with `remember { PlayerViewModel(...) }`
 * so it is scoped to the Compose lifecycle.  We cannot create it outside Compose —
 * instead [App] calls back via [onViewModelsReady] once it is ready, and we
 * hand it to [IosPipController] at that point.
 */
fun MainViewController(): UIViewController {

    // ── 1. Source canvas dimensions (immutable for the entire PiP lifecycle) ──
    val (nativeW, nativeH) = UIScreen.mainScreen.nativeBounds.useContents {
        Pair(size.width, size.height)
    }
    val sourceW = (nativeW * SOURCE_SCALE).toInt().coerceAtLeast(1)
    val sourceH = (nativeH * SOURCE_SCALE).toInt().coerceAtLeast(1)

    // ── 2. Create CPU-only Skia-backed frame renderer ─────────────────────────
    // We use Density(1.0f) so that the PiP text is proportionally smaller than the
    // main screen. Since the PiP canvas matches the physical pixels of the PiP window,
    // a density of 1.0f effectively shrinks the text by 3x (on a 3x scale device),
    // allowing it to fit a readable number of words per line while still reflowing smoothly!
    val density = Density(1.0f)

    // ── 3. FrameViewModel with CPU renderer ───────────────────────────────────
    val frameViewModel = FrameViewModel(
        renderer = CpuTeleprompterFrameRenderer(density),
    )
    frameViewModel.onIntent(FrameVmIntent.SetFrameSize(sourceW, sourceH))

    // ── 4. Display layer + pixel sink ─────────────────────────────────────────
    //
    // IosPipController owns the AVSampleBufferDisplayLayer; we ask for it before
    // building IosFrameSink so the sink can enqueue into the correct layer.
    // pipController is created without a PlayerViewModel here — it gets the
    // reference via onViewModelsReady below once App creates it.
    val pipController = IosPipController(frameViewModel = frameViewModel)

    val iosFrameSink = IosFrameSink(
        widthPx      = sourceW,
        heightPx     = sourceH,
        displayLayer = pipController.displayLayer,
    )
    pipController.frameSink = iosFrameSink
    frameViewModel.replaceSink(iosFrameSink)

    // ── 5. Compose UIViewController ───────────────────────────────────────────
    val rootViewModel = RootViewModel()

    val controller = ComposeUIViewController {
        App(
            rootViewModel     = rootViewModel,
            frameViewModel    = frameViewModel,
            // Called by App once PlayerViewModel is alive inside Compose.
            // We complete IosPipController's wiring here.
            onViewModelsReady = { playerVm ->
                pipController.attachPlayerViewModel(playerVm)
            },
        )
    }

    // Add native iOS edge swipe gesture recognizer to detect swipe back navigation
    val gestureHandler = EdgeSwipeGestureHandler(rootViewModel)
    rootViewModel.platformGestureHandler = gestureHandler // Retain reference!

    val selector = NSSelectorFromString("handleSwipe:")
    // Use standard UIPanGestureRecognizer, restricted to the left edge via gestureRecognizerShouldBegin
    val recognizer = UIPanGestureRecognizer(target = gestureHandler, action = selector).apply {
        delegate = gestureHandler
    }
    controller.view.addGestureRecognizer(recognizer)

    // Add the displayLayer as a tiny 1x1 sublayer to the view controller's view.
    // This places it in the active view hierarchy, which is required by iOS.
    controller.view.layer.addSublayer(pipController.displayLayer)
    val screenBounds = UIScreen.mainScreen.bounds
    pipController.displayLayer.frame = kotlinx.cinterop.cValue {
        origin.x = -2000.0
        origin.y = -2000.0
        size.width = screenBounds.useContents { size.width }
        size.height = screenBounds.useContents { size.height }
    }

    return controller
}

class EdgeSwipeGestureHandler(
    private val exitHandler: ExitHandler
) : NSObject(), platform.UIKit.UIGestureRecognizerDelegateProtocol {

    init {
        println("[EdgeSwipe] Handler initialized")
    }

    @ObjCAction
    fun handleSwipe(sender: UIPanGestureRecognizer) {
        val view = sender.view ?: return
        val state = sender.state
        val translationX = sender.translationInView(view).useContents { x }
        val velocityX = sender.velocityInView(view).useContents { x }
        
        // Log changes in state
        if (state == UIGestureRecognizerStateBegan || state == UIGestureRecognizerStateEnded) {
            println("[EdgeSwipe] handleSwipe state=$state, translationX=$translationX, velocityX=$velocityX, shouldIntercept=${exitHandler.shouldInterceptBack}")
        }

        when (state) {
            UIGestureRecognizerStateBegan -> {
                if (exitHandler.shouldInterceptBack) {
                    println("[EdgeSwipe] Intercepting exit request on Began")
                    exitHandler.requestExit(ExitReason.Back)
                    // Disable and re-enable to cancel the current gesture
                    sender.enabled = false
                    sender.enabled = true
                }
            }
            UIGestureRecognizerStateEnded -> {
                if (!exitHandler.shouldInterceptBack) {
                    val width = view.bounds.useContents { size.width }
                    println("[EdgeSwipe] Ended: translationX=$translationX, width=$width")
                    // 30% width swipe or fast positive swipe velocity
                    if (translationX > width * 0.3 || velocityX > 300.0) {
                        println("[EdgeSwipe] Ended threshold met, requesting exit")
                        exitHandler.requestExit(ExitReason.Back)
                    }
                }
            }
            else -> {}
        }
    }

    override fun gestureRecognizerShouldBegin(gestureRecognizer: platform.UIKit.UIGestureRecognizer): Boolean {
        val view = gestureRecognizer.view ?: return false
        val locationX = gestureRecognizer.locationInView(view).useContents { x }
        // Only begin if the touch is within 40 points of the left edge
        if (locationX > 40.0) {
            return false
        }
        println("[EdgeSwipe] Gesture began at edge: locationX=$locationX")
        return true
    }

    override fun gestureRecognizer(
        gestureRecognizer: platform.UIKit.UIGestureRecognizer,
        shouldRecognizeSimultaneouslyWithGestureRecognizer: platform.UIKit.UIGestureRecognizer
    ): Boolean {
        // Allow recognizing simultaneously with Compose scroll/pan gestures
        return true
    }
}
