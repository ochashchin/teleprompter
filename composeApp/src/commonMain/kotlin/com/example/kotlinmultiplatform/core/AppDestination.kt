package com.example.kotlinmultiplatform.core

/**
 * Application navigation graph.
 *
 * Replaces the old ad-hoc [Destination] sealed interface in AppNavigation.kt.
 * All destinations live in commonMain — no platform imports required.
 *
 * The [NavigationEvent] sealed hierarchy references [AppDestination], so any
 * ViewModel can emit a typed navigation event without knowing about the
 * Compose navigation host.
 *
 * ── Current screens ───────────────────────────────────────────────────────────
 *
 *   TaskList  ──►  NewDetail ──►  Detail(isPreview=true)  ──►  PlayDetail
 *       ▲               │                  │                       │
 *       └───────────────┘                  └───────────────────────┘
 *                    (back / save)               (back / close)
 *
 * ── Future PiP extension ─────────────────────────────────────────────────────
 *
 *   PlayDetail can transition to PipActive while the host screen remains.
 *   Add `data object PipActive` here and handle it in the navigation host.
 */
sealed interface AppDestination {

    /** Main task list screen. */
    data object TaskList : AppDestination

    /**
     * New-task / edit-task screen.
     *
     * @param editingTaskId  null = create mode;  non-null = edit mode for this id.
     * @param isPreviewReturn  true when navigating back from [Detail] preview.
     */
    data class NewDetail(
        val editingTaskId: Int?  = null,
        val isPreviewReturn: Boolean = false,
    ) : AppDestination

    /**
     * Display / detail screen for a saved task.
     *
     * @param taskId    The stable task identifier (not the full object, to avoid
     *                  stale references crossing navigation boundaries).
     * @param isPreview True when reached from [NewDetail] via "Next" — the back
     *                  button returns to [NewDetail] instead of [TaskList].
     */
    data class Detail(
        val taskId: Int,
        val isPreview: Boolean = false,
    ) : AppDestination

    /**
     * Teleprompter/player screen.
     *
     * @param taskId    Stable task id — the screen reads settings state itself.
     * @param isPreview Propagated from [Detail.isPreview] so back-stack is correct.
     */
    data class PlayDetail(
        val taskId: Int,
        val isPreview: Boolean = false,
    ) : AppDestination

    // ── Reserved for PiP expansion ────────────────────────────────────────────

    /**
     * Picture-in-Picture overlay is active.
     * The host remembers the [previousDestination] to restore on dismiss.
     *
     * Uncomment when the PiP rendering system is implemented:
     *
     * data class PipActive(
     *     val taskId: Int,
     *     val previousDestination: AppDestination,
     * ) : AppDestination
     */
}
