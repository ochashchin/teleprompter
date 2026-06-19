package com.oprojectview

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.oprojectview.features.tasklist.SettingsTaskListRepository
import com.russhwolf.settings.Settings
import com.oprojectview.features.display.DisplayViewModel
import com.oprojectview.features.display.LocalDisplayViewModel
import com.oprojectview.features.display.SettingsDisplayRepository
import com.oprojectview.features.newtask.LocalNewTaskViewModel
import com.oprojectview.features.newtask.NewTaskViewModel
import com.oprojectview.features.newtask.SettingsNewTaskRepository
import com.oprojectview.features.player.LocalPlayerViewModel
import com.oprojectview.features.player.PlayerViewModel
import com.oprojectview.features.player.SettingsPlayerRepository
import com.oprojectview.features.tasklist.LocalTaskListViewModel
import com.oprojectview.features.tasklist.TaskListViewModel
import com.oprojectview.frame.FrameViewModel
import com.oprojectview.navigation.NavigationViewModel
import com.oprojectview.navigation.RootViewModel
import com.oprojectview.theme.AppTheme

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
 *                         the [com.oprojectview.features.player.PlayerViewModel] reference — PipController needs
 *                         both [com.oprojectview.frame.FrameViewModel] and [com.oprojectview.features.player.PlayerViewModel].
 *
 * All existing call-sites (iOS MainViewController, previews) pass neither param
 * and are unaffected.
 *
 * @param rootViewModel      Optional — iOS hoists it in MainViewController.
 * @param onExitApp          Android: { finishAffinity() }. iOS: no-op.
 * @param frameViewModel     Android: injected by MainActivity. iOS: injected by MainViewController. Others: null.
 * @param onViewModelsReady  Android: used to create PipController. Others: no-op.
 */
@Composable
fun App(
    rootViewModel:      RootViewModel?              = null,
    onExitApp:          () -> Unit                  = {},
    frameViewModel:     FrameViewModel?             = null,
    onViewModelsReady:  ((PlayerViewModel) -> Unit) = {},
) {
    val settings = remember {
        Settings().also { s ->
            // Seed pre-saved tasks exactly once per install.
            //
            // WHY HERE:
            //   ensureSeeded() costs two Settings reads on every cold boot after
            //   first install: getBoolean(KEY_POPULATED) → true → return immediately.
            //   Placing it here — inside the Settings remember block — guarantees it
            //   runs BEFORE TaskListViewModel.loadAll(), so the list is never empty.
            //
            // WHY NOT in TaskListViewModel.init:
            //   Would require injecting NewTaskRepository into TaskListViewModel
            //   just for seeding, coupling two unrelated concerns.
            //
            // WHY NOT in NewTaskViewModel.init (previous behaviour):
            //   Only fires when the user opens the NewTask screen.
            //   Task list loads first on cold boot — seeds haven't run yet.
            //
            // COST after first install: one boolean Settings read, zero I/O beyond it.
            SettingsNewTaskRepository(s).ensureSeeded()
        }
    }

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
            LocalSettings provides settings,
            LocalTaskListViewModel provides taskListViewModel,
            LocalNewTaskViewModel provides newTaskViewModel,
            LocalDisplayViewModel provides displayViewModel,
            LocalPlayerViewModel provides playerViewModel,
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
