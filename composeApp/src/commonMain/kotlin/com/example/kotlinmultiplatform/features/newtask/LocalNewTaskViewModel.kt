package com.example.kotlinmultiplatform.features.newtask

import androidx.compose.runtime.compositionLocalOf
import com.russhwolf.settings.Settings

/**
 * Provides [NewTaskViewModel] down the composition tree.
 *
 * Created once in App.kt alongside [LocalNavViewModel] and
 * [LocalTaskListViewModel]. The default lambda is a preview-safe no-op
 * backed by an in-memory Settings stub; never reached at runtime.
 */
val LocalNewTaskViewModel = compositionLocalOf<NewTaskViewModel> {
    NewTaskViewModel(SettingsNewTaskRepository(Settings()))
}
