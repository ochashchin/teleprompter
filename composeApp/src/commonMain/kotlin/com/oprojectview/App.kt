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
import com.oprojectview.features.newtask.SeedTask
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.*
import org.jetbrains.compose.resources.stringResource

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
    val t1_title = stringResource(Res.string.seed_task1_title)
    val t1_desc  = stringResource(Res.string.seed_task1_desc)
    val t1_spans = stringResource(Res.string.seed_task1_spans)

    val t2_title = stringResource(Res.string.seed_task2_title)
    val t2_desc  = stringResource(Res.string.seed_task2_desc)
    val t2_spans = stringResource(Res.string.seed_task2_spans)

    val t3_title = stringResource(Res.string.seed_task3_title)
    val t3_desc  = stringResource(Res.string.seed_task3_desc)
    val t3_spans = stringResource(Res.string.seed_task3_spans)

    val t4_title = stringResource(Res.string.seed_task4_title)
    val t4_desc  = stringResource(Res.string.seed_task4_desc)
    val t4_spans = stringResource(Res.string.seed_task4_spans)

    val t5_title = stringResource(Res.string.seed_task5_title)
    val t5_desc  = stringResource(Res.string.seed_task5_desc)
    val t5_spans = stringResource(Res.string.seed_task5_spans)

    val settings = remember(t1_title) {
        Settings().also { s ->
            val seedTasks = listOf(
                SeedTask(t1_title, t1_desc, shapeOrdinal = 2, spans = t1_spans, textSize = 2, orientation = 0, speed = 1, animation = 0, transition = 1, distortion = 0, mirror = 0, overlay = 0, loop = false, scriptFillColor = 3),
                SeedTask(t2_title, t2_desc, shapeOrdinal = 5, spans = t2_spans, textSize = 3, orientation = 0, speed = 0, animation = 1, transition = 0, distortion = 0, mirror = 0, overlay = 0, loop = false, scriptFillColor = 2),
                SeedTask(t3_title, t3_desc, shapeOrdinal = 25, spans = t3_spans, textSize = 0, orientation = 0, speed = 0, animation = 1, transition = 0, distortion = 0, mirror = 0, overlay = 2, loop = false, scriptFillColor = 0),
                SeedTask(t4_title, t4_desc, shapeOrdinal = 10, spans = t4_spans, textSize = 1, orientation = 1, speed = 2, animation = 0, transition = 2, distortion = 1, mirror = 0, overlay = 0, loop = false, scriptFillColor = 1),
                SeedTask(t5_title, t5_desc, shapeOrdinal = 0, spans = t5_spans, textSize = 5, orientation = 1, speed = 0, animation = 2, transition = 0, distortion = 0, mirror = 0, overlay = 0, loop = true, scriptFillColor = -1)
            )
            SettingsNewTaskRepository(s).ensureSeeded(seedTasks)
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
            PlatformSplashScreen {
                AppRoot(
                    viewModel      = vm,
                    onExitApp      = onExitApp,
                    frameViewModel = frameViewModel,
                    modifier       = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
