import UIKit
import SwiftUI
import AVFoundation
import AVKit
import ComposeApp

// MARK: - ComposeView

/// Wraps the KMP Kotlin `MainViewController` in a SwiftUI-compatible
/// `UIViewControllerRepresentable`.
///
/// ## What happens inside MainViewController (Kotlin side)
///
/// ```
/// UIScreen.nativeBounds × 0.5 (SOURCE_SCALE)
///         ↓
/// FrameCaptureState  ←  Modifier.pipCapture (Compose draw pass)
///         ↓
/// ComposeCapturingRenderer  (reads latestBitmap on Dispatchers.Default)
///         ↓
/// FrameViewModel  (MVVM — drives FrameProducer loop)
///         ↓
/// IosFrameSink  (bitmap → CVPixelBuffer → CMSampleBuffer)
///         ↓
/// AVSampleBufferDisplayLayer
///         ↓
/// IosPipController  (AVPictureInPictureController lifecycle)
/// ```
///
/// Swift's only job here is to host the UIViewController and keep the
/// audio session alive. All PiP logic lives in Kotlin.
struct ComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        // MainViewController wires the full pipeline internally.
        // No additional Swift setup is required.
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {
        // No updates needed — state is managed by the Kotlin MVVM layer.
    }
}

// MARK: - ContentView

struct ContentView: View {
    var body: some View {
        ComposeView()
            // ignoresSafeArea: Compose handles safe-area insets internally
            // via SafeArea.kt. Without this the KMP view is clipped by iOS.
            .ignoresSafeArea(.all)
    }
}
