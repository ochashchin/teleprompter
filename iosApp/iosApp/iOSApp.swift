import SwiftUI

// NOTE: AppDelegate.swift handles app lifecycle via @UIApplicationMain.
// SwiftUI @main is intentionally removed — mixing @UIApplicationMain
// and @main in the same target causes a duplicate entry-point linker error.
//
// The SwiftUI scene graph is bootstrapped from AppDelegate via the window
// property set in application(_:didFinishLaunchingWithOptions:).
// ContentView.swift is loaded from the Xcode storyboard / window setup
// that AppDelegate controls.
//
// If you prefer a pure SwiftUI lifecycle, replace AppDelegate.swift with
// the AVAudioSession activation inside App.init() and restore @main here:
//
//   @main
//   struct iOSApp: App {
//       init() { AVAudioSessionManager.activate() }
//       var body: some Scene { WindowGroup { ContentView() } }
//   }
