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
import com.example.kotlinmultiplatform.navigation.RootViewModel
import com.example.kotlinmultiplatform.ui.theme.AppTheme
import com.russhwolf.settings.Settings

// Keep LocalNavViewModel so any other file that references it still compiles.
val LocalNavViewModel = compositionLocalOf {
    NavigationViewModel(Destination.TaskList as Destination)
}

/**
 * @param rootViewModel  Optional — iOS hoists it in MainViewController so the
 *                       same instance is used for gesture registration.
 *                       Android passes null and App() creates it internally.
 * @param onExitApp      Android: { finishAffinity() }. iOS: no-op default.
 */
@Composable
fun App(
    rootViewModel: RootViewModel? = null,
    onExitApp: () -> Unit = {},
) {
    val settings = remember { Settings() }

    val vm                = rootViewModel ?: remember { RootViewModel() }
    val taskListViewModel = remember { TaskListViewModel(SettingsTaskListRepository(settings)) }
    val newTaskViewModel  = remember { NewTaskViewModel(SettingsNewTaskRepository(settings)) }
    val displayViewModel  = remember { DisplayViewModel(SettingsDisplayRepository(settings)) }
    val playerViewModel   = remember { PlayerViewModel(SettingsPlayerRepository(settings)) }

    DisposableEffect(vm, taskListViewModel, newTaskViewModel, displayViewModel, playerViewModel) {
        onDispose {
            // Only clear the VM if App() owns it (rootViewModel param was null).
            if (rootViewModel == null) vm.clear()
            taskListViewModel.clear()
            newTaskViewModel.clear()
            displayViewModel.clear()
            playerViewModel.clear()
        }
    }

    AppTheme {
        CompositionLocalProvider(
            LocalSettings          provides settings,
            LocalTaskListViewModel provides taskListViewModel,
            LocalNewTaskViewModel  provides newTaskViewModel,
            LocalDisplayViewModel  provides displayViewModel,
            LocalPlayerViewModel   provides playerViewModel,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            )
            AppRoot(
                viewModel = vm,
                onExitApp = onExitApp,
                modifier  = Modifier.fillMaxSize(),
            )
        }
    }
}
