package com.example.kotlinmultiplatform.features.tasklist

import androidx.compose.runtime.compositionLocalOf
import com.russhwolf.settings.Settings

// ── CompositionLocal ──────────────────────────────────────────────────────────

/**
 * Provides [TaskListViewModel] down the composition tree.
 *
 * Created once in App.kt alongside [LocalNavViewModel] and [LocalSettings].
 * The default lambda produces a preview-safe no-op instance backed by an
 * in-memory settings stub; it is never reached at runtime.
 */
val LocalTaskListViewModel = compositionLocalOf<TaskListViewModel> {
    TaskListViewModel(
        SettingsTaskListRepository(Settings())
    )
}
