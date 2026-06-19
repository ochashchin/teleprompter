package com.oprojectview

import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.oprojectview.core.WindowModeObserver
import com.oprojectview.frame.FrameViewModel
import com.oprojectview.frame.LogFrameSink
import com.oprojectview.frame.OffscreenFrameRenderer
import com.oprojectview.pip.PipController

/**
 * Entry point for Android.
 *
 * ## PiP strategy
 *
 * Android PiP shows the Activity's window contents shrunk into a floating
 * rectangle — it does NOT require a separate video Surface or encode/decode
 * pipeline.  The previous approach (SurfaceView + MediaCodec + SurfaceViewRoute)
 * was adding encode→decode round-trip complexity with zero benefit on the same
 * device; the SurfaceView was behind the Compose surface so the PiP window
 * showed the Compose layout anyway.
 *
 * Correct approach:
 *   1. Navigate to PlayerScreen before or during PiP entry — the Activity
 *      window already shows the teleprompter content.
 *   2. Call Activity.enterPictureInPictureMode() — Android shrinks that window
 *      into the PiP rect.  The PlayerScreen Compose UI is what appears.
 *   3. [PipController] handles lifecycle, autoEnterEnabled (Bug 3 fix), and
 *      the aspect-ratio hint so the PiP rect matches the player layout.
 *
 * FrameViewModel is still constructed (LogFrameSink) so the frame pipeline
 * infrastructure compiles and can be extended later without platform changes.
 */
class MainActivity : ComponentActivity() {

    private val frameViewModel: FrameViewModel by lazy {
        FrameViewModel(
            sink = LogFrameSink(),
            renderer = OffscreenFrameRenderer(),
        )
    }

    private var pipController: PipController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowModeObserver.setMultiWindow(isInMultiWindowMode)

        setContent {
            App(
                onExitApp         = { finishAffinity() },
                frameViewModel    = frameViewModel,
                onViewModelsReady = { playerVm ->
                    pipController = PipController(
                        activity = this,
                        frameViewModel = frameViewModel,
                        playerViewModel = playerVm,
                        lifecycleOwner = this,
                    )
                },
            )
        }
    }

    override fun onMultiWindowModeChanged(
        isInMultiWindowMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        WindowModeObserver.setMultiWindow(isInMultiWindowMode)
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration,
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
    }

    override fun onDestroy() {
        pipController?.release()
        frameViewModel.clear()
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
