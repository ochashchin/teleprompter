package com.example.kotlinmultiplatform.navigation

/**
 * Screen — the complete navigation graph for this application.
 *
 * Navigation flow:
 *
 *   Leave App  ◄──►  TaskScreen  ◄──►  NewTaskScreen  ◄──►  DisplayScreen  ◄──►  PlayerScreen
 *
 * All entries are data objects — no data crosses navigation boundaries,
 * which prevents stale-reference bugs across screen transitions.
 */
sealed class Screen {
    /** Task list — the root / home screen. Always at stack index 0. */
    data object TaskScreen    : Screen()

    /** New-task creation form. */
    data object NewTaskScreen : Screen()

    /** Task detail / display screen. */
    data object DisplayScreen : Screen()

    /** Teleprompter / player screen. */
    data object PlayerScreen  : Screen()
}
