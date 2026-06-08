package com.example.kotlinmultiplatform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
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
import com.example.kotlinmultiplatform.frame.FrameViewModel
import com.example.kotlinmultiplatform.navigation.NavigationViewModel
import com.example.kotlinmultiplatform.navigation.RootViewModel
import com.example.kotlinmultiplatform.ui.theme.AppTheme
import com.russhwolf.settings.Settings

// Keep LocalNavViewModel so any other file that references it still compiles.
val LocalNavViewModel = compositionLocalOf {
    NavigationViewModel(Destination.TaskList as Destination)
}

/**
 * Application root composable.
 *
 * ### New optional parameters (Android PiP integration)
 *
 * - [frameViewModel]:     Injected from [MainActivity] with the platform-specific
 *                         [AndroidFrameSink] already wired.  Defaults to null so
 *                         iOS / Desktop continue to compile without changes.
 *
 * - [onViewModelsReady]:  Called once, after all feature ViewModels are created,
 *                         so [MainActivity] can construct [PipController] with
 *                         the [PlayerViewModel] reference — PipController needs
 *                         both [FrameViewModel] and [PlayerViewModel].
 *
 * All existing call-sites (iOS MainViewController, previews) pass neither param
 * and are unaffected.
 *
 * @param rootViewModel      Optional — iOS hoists it in MainViewController.
 * @param onExitApp          Android: { finishAffinity() }. iOS: no-op.
 * @param frameViewModel     Android: injected by MainActivity. Others: null.
 * @param onViewModelsReady  Android: used to create PipController. Others: no-op.
 */
@Composable
fun App(
    rootViewModel:      RootViewModel?          = null,
    onExitApp:          () -> Unit              = {},
    frameViewModel:     FrameViewModel?         = null,
    onViewModelsReady:  ((PlayerViewModel) -> Unit) = {},
) {
    val settings = remember { Settings() }

    val vm                = rootViewModel ?: remember { RootViewModel() }
    val taskListViewModel = remember { TaskListViewModel(SettingsTaskListRepository(settings)) }
    val newTaskViewModel  = remember { NewTaskViewModel(SettingsNewTaskRepository(settings)) }
    val displayViewModel  = remember { DisplayViewModel(SettingsDisplayRepository(settings)) }
    val playerViewModel   = remember { PlayerViewModel(SettingsPlayerRepository(settings)) }

    // Notify platform code (MainActivity → PipController) that ViewModels are ready.
    // SideEffect runs after every successful composition; the lambda is guarded to
    // be idempotent in practice because PipController checks for null before creating.
    SideEffect {
        onViewModelsReady(playerViewModel)
    }

    DisposableEffect(vm, taskListViewModel, newTaskViewModel, displayViewModel, playerViewModel) {
        onDispose {
            if (rootViewModel == null) vm.clear()
            taskListViewModel.clear()
            newTaskViewModel.clear()
            displayViewModel.clear()
            playerViewModel.clear()
            // frameViewModel is cleared by MainActivity.onDestroy; not here.
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
            AppRoot(
                viewModel      = vm,
                onExitApp      = onExitApp,
                frameViewModel = frameViewModel,
                modifier       = Modifier.fillMaxSize(),
            )
        }
    }
}
