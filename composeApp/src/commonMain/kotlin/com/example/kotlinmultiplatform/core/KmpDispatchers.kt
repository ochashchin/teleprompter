package com.example.kotlinmultiplatform.core

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Platform-agnostic coroutine dispatcher provider.
 *
 * The `expect` declaration here is satisfied by `actual` implementations in
 * androidMain / iosMain / jvmMain / jsMain.  commonMain code only depends on
 * this object — never on [Dispatchers.Main] or [Dispatchers.IO] directly,
 * because those are not available on all KMP targets.
 *
 * Recommended actuals:
 *
 *  androidMain → Dispatchers.Main  /  Dispatchers.IO
 *  iosMain     → Dispatchers.Main  /  Dispatchers.Default
 *  jvmMain     → Dispatchers.Main  /  Dispatchers.IO
 *  jsMain      → Dispatchers.Main  /  Dispatchers.Default   (JS has no IO)
 */
expect object KmpDispatchers {
    /** Main/UI thread dispatcher. */
    val Main: CoroutineDispatcher

    /** Background work dispatcher (IO on Android/JVM, Default elsewhere). */
    val IO: CoroutineDispatcher

    /** CPU-bound computation dispatcher. */
    val Default: CoroutineDispatcher
}

// ── androidMain actual (place in androidMain source set) ──────────────────────
//
// actual object KmpDispatchers {
//     actual val Main:    CoroutineDispatcher = Dispatchers.Main
//     actual val IO:      CoroutineDispatcher = Dispatchers.IO
//     actual val Default: CoroutineDispatcher = Dispatchers.Default
// }

// ── iosMain / jvmMain / jsMain actuals (same shape, swap IO → Default on JS) ──
//
// actual object KmpDispatchers {
//     actual val Main:    CoroutineDispatcher = Dispatchers.Main
//     actual val IO:      CoroutineDispatcher = Dispatchers.Default
//     actual val Default: CoroutineDispatcher = Dispatchers.Default
// }
