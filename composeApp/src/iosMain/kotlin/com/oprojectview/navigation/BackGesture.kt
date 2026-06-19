package com.oprojectview.navigation

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCAction
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGFloat
import platform.UIKit.UIApplication
import platform.UIKit.UIGestureRecognizerStateBegan
import platform.UIKit.UIGestureRecognizerStateCancelled
import platform.UIKit.UIGestureRecognizerStateChanged
import platform.UIKit.UIGestureRecognizerStateEnded
import platform.UIKit.UIRectEdgeLeft
import platform.UIKit.UIScreenEdgePanGestureRecognizer
import platform.UIKit.UIView
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.darwin.NSObject
import platform.objc.sel_registerName

// ── Thresholds ────────────────────────────────────────────────────────────────
// Mirror UINavigationController values so the swipe feel is native.

private const val SWIPE_MIN_TRANSLATION_X: CGFloat = 40.0   // points
private const val SWIPE_MIN_VELOCITY_X:    CGFloat = 300.0  // points/second

// ── Gesture target ────────────────────────────────────────────────────────────
// Held at module level so ARC never releases it.

@OptIn(ExperimentalForeignApi::class)
private class EdgeSwipeTarget(private val onBack: () -> Unit) : NSObject() {

    @ObjCAction
    fun handleEdgeSwipe(recognizer: UIScreenEdgePanGestureRecognizer) {
        val view = recognizer.view ?: return
        when (recognizer.state) {
            UIGestureRecognizerStateBegan,
            UIGestureRecognizerStateChanged -> Unit
            UIGestureRecognizerStateEnded -> {
                // CGPoint is a C struct — access fields via useContents { }.
                val tx = recognizer.translationInView(view).useContents { x }
                val vx = recognizer.velocityInView(view).useContents { x }
                if (tx >= SWIPE_MIN_TRANSLATION_X && vx >= SWIPE_MIN_VELOCITY_X) {
                    onBack()
                }
            }
            UIGestureRecognizerStateCancelled -> Unit
            else -> Unit
        }
    }
}

private var _edgeSwipeTarget: EdgeSwipeTarget? = null

@OptIn(ExperimentalForeignApi::class)
actual fun registerBackGesture(onBack: () -> Unit) {
    if (_edgeSwipeTarget != null) return

    val rootView: UIView = UIApplication.sharedApplication
        .connectedScenes
        .filterIsInstance<UIWindowScene>()
        .firstOrNull()
        ?.windows
        ?.filterIsInstance<UIWindow>()
        ?.firstOrNull { it.isKeyWindow() }
        ?.rootViewController
        ?.view
        ?: return

    val target = EdgeSwipeTarget(onBack).also { _edgeSwipeTarget = it }
    val recognizer = UIScreenEdgePanGestureRecognizer(
        target = target,
        action = sel_registerName("handleEdgeSwipe:"),
    )
    recognizer.edges = UIRectEdgeLeft
    rootView.addGestureRecognizer(recognizer)
}
