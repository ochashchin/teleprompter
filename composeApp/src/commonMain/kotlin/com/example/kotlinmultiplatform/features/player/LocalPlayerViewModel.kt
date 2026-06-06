package com.example.kotlinmultiplatform.features.player

import androidx.compose.runtime.compositionLocalOf
import com.russhwolf.settings.Settings

/**
 * Provides [PlayerViewModel] down the composition tree.
 * Default is preview-safe; never reached at runtime.
 */
val LocalPlayerViewModel = compositionLocalOf<PlayerViewModel> {
    PlayerViewModel(SettingsPlayerRepository(Settings()))
}
