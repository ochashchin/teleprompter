package com.example.kotlinmultiplatform.features.display

import androidx.compose.runtime.compositionLocalOf
import com.russhwolf.settings.Settings

/**
 * Provides [DisplayViewModel] down the composition tree.
 * Default is preview-safe; never reached at runtime.
 */
val LocalDisplayViewModel = compositionLocalOf<DisplayViewModel> {
    DisplayViewModel(SettingsDisplayRepository(Settings()))
}
