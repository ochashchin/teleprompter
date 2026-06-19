import UIKit
import SwiftUI
import AVFoundation

/// Entry point for the iOS app.
///
/// ## Responsibilities
///
/// 1. Activate `AVAudioSession` with `.playback` category — **required** for
///    `AVPictureInPictureController` to be granted by the system.
/// 2. Bootstrap the SwiftUI window hierarchy (`ContentView` → `ComposeView`
///    → KMP `MainViewController`).
///
/// ## Why AppDelegate instead of SwiftUI @main
///
/// SwiftUI's `@main` + `App.init()` runs after the first render pass, which
/// is too late — `AVAudioSession` must be active before the user triggers PiP.
/// `AppDelegate.application(_:didFinishLaunchingWithOptions:)` runs at cold
/// start, before any UI is shown, guaranteeing the session is ready.
@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate {

    var window: UIWindow?

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {

        // ── 1. AVAudioSession ─────────────────────────────────────────────────
        //
        // PiP requires an active AVAudioSession with a non-ambient category.
        // We use .playback + .mixWithOthers so the teleprompter does NOT
        // interrupt background music the user may already be playing.
        //
        // Without this call on a real device:
        //   AVPictureInPictureController.isPictureInPictureSupported() → false
        //   startPictureInPicture() → silently fails
        activateAudioSession()

        // ── 2. SwiftUI window ─────────────────────────────────────────────────
        let window = UIWindow(frame: UIScreen.main.bounds)
        window.rootViewController = UIHostingController(
            rootView: ContentView()
        )
        window.makeKeyAndVisible()
        self.window = window

        return true
    }

    // MARK: - Private

    private func activateAudioSession() {
        do {
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(
                .playback,
                mode: .moviePlayback,
                options: [.mixWithOthers]
            )
            try session.setActive(true)
        } catch {
            // Non-fatal: PiP will be unavailable but the app still functions.
            print("[AppDelegate] AVAudioSession activation failed: \(error)")
        }
    }
}
