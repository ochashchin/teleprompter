package com.oprojectview.pip

import com.oprojectview.frame.PipSizeClass

/**
 * Infers [PipSizeClass] from the PiP layer's rendered pixel dimensions.
 *
 * `PipSizeClass` is now defined in `commonMain` (frame package) so both
 * the shared ViewModel and iOS-specific code share the same type.
 *
 * Thresholds (empirical, with hysteresis):
 *  - iPhone SE default PiP  ≈ 183×103 ≈ 18 849 px²  → Compact
 *  - iPhone 14 default PiP  ≈ 240×135 ≈ 32 400 px²  → Compact
 *  - iPad default PiP        ≈ 320×180 ≈ 57 600 px²  → Expanded
 */
class IosPipSizeHeuristics {
    private var current = PipSizeClass.Compact

    fun evaluate(widthPx: Double, heightPx: Double): PipSizeClass {
        val area = widthPx * heightPx
        current = when {
            current == PipSizeClass.Compact  && area > EXPAND_THRESHOLD  -> PipSizeClass.Expanded
            current == PipSizeClass.Expanded && area < COMPACT_THRESHOLD -> PipSizeClass.Compact
            else -> current
        }
        return current
    }

    companion object {
        private const val EXPAND_THRESHOLD  = 50_000.0
        private const val COMPACT_THRESHOLD = 40_000.0
    }
}

/** Render FPS target for each size class. Lives here (iOS-only concern). */
val PipSizeClass.targetFps: Int get() = when (this) {
    PipSizeClass.Compact  -> 32
    PipSizeClass.Expanded -> 64
}
