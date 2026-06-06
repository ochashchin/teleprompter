package com.example.kotlinmultiplatform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.kotlinmultiplatform.features.display.DisplayViewModel
import com.example.kotlinmultiplatform.features.display.LocalDisplayViewModel
import com.example.kotlinmultiplatform.features.display.SettingsDisplayRepository
import com.example.kotlinmultiplatform.features.newtask.LocalNewTaskViewModel
import com.example.kotlinmultiplatform.features.newtask.NewTaskViewModel
import com.example.kotlinmultiplatform.features.newtask.SettingsNewTaskRepository
import com.example.kotlinmultiplatform.features.player.LocalPlayerViewModel
import com.example.kotlinmultiplatform.features.player.PlayerViewModel
import com.example.kotlinmultiplatform.features.player.SettingsPlayerRepository
import com.example.kotlinmultiplatform.features.tasklist.LocalTaskListViewModel
import com.example.kotlinmultiplatform.features.tasklist.SettingsTaskListRepository
import com.example.kotlinmultiplatform.features.tasklist.TaskListViewModel
import com.example.kotlinmultiplatform.navigation.NavigationViewModel
import com.example.kotlinmultiplatform.ui.theme.AppTheme
import com.russhwolf.settings.Settings

// ── CompositionLocals ─────────────────────────────────────────────────────────
//
// LocalSettings is defined in AppSettings.kt — same package, no import needed.
// All others are defined in their respective feature packages.
//
// !! DEPLOYMENT NOTE !!
// App.kt and AppNavigation.kt import symbols from four sub-packages:
//   • features/display/
//   • features/player/
//   • features/newtask/
//   • features/tasklist/
//   • navigation/
//
// ALL of those package files must exist in the project before this file
// will compile.  If any sub-package is absent the Kotlin compiler rejects
// the entire com.example.kotlinmultiplatform package, which makes Task,
// LeadingShapeType, TaskScreenBody, NewTaskScreenBody, PlayerScreenBody and
// LocalSettings appear as "Unresolved reference" even though those symbols
// are completely unchanged.  Add every file in this zip first, then build.

val LocalNavViewModel = compositionLocalOf {
    NavigationViewModel(Destination.TaskList as Destination)
}

@Composable
fun App() {
    val settings = remember { Settings() }

    val navViewModel: NavigationViewModel<Destination> = remember { NavigationViewModel(Destination.TaskList) }
    val taskListViewModel = remember { TaskListViewModel(SettingsTaskListRepository(settings)) }
    val newTaskViewModel = remember { NewTaskViewModel(SettingsNewTaskRepository(settings)) }
    val displayViewModel = remember { DisplayViewModel(SettingsDisplayRepository(settings)) }
    val playerViewModel = remember { PlayerViewModel(SettingsPlayerRepository(settings)) }

    DisposableEffect(
        navViewModel,
        taskListViewModel,
        newTaskViewModel,
        displayViewModel,
        playerViewModel
    ) {
        onDispose {
            navViewModel.clear()
            taskListViewModel.clear()
            newTaskViewModel.clear()
            displayViewModel.clear()
            playerViewModel.clear()
        }
    }

    AppTheme {
        CompositionLocalProvider(
            // ── Infrastructure ─────────────────────────────────────────────
            LocalSettings provides settings,        // AppSettings.kt
            LocalNavViewModel provides navViewModel,    // this file

            // ── Feature ViewModels — screen-stack order ────────────────────
            LocalTaskListViewModel provides taskListViewModel,
            LocalNewTaskViewModel provides newTaskViewModel,
            LocalDisplayViewModel provides displayViewModel,
            LocalPlayerViewModel provides playerViewModel,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
            AppNavigation(modifier = Modifier.fillMaxSize())
        }
    }
}
