package com.example.kotlinmultiplatform

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import com.example.kotlinmultiplatform.core.WindowModeObserver
import com.example.kotlinmultiplatform.features.display.DisplayEvent
import com.example.kotlinmultiplatform.features.display.DisplayIntent
import com.example.kotlinmultiplatform.features.display.LocalDisplayViewModel
import com.example.kotlinmultiplatform.features.newtask.LocalNewTaskViewModel
import com.example.kotlinmultiplatform.features.newtask.NewTaskEvent
import com.example.kotlinmultiplatform.features.newtask.NewTaskIntent
import com.example.kotlinmultiplatform.features.player.LocalPlayerViewModel
import com.example.kotlinmultiplatform.features.player.PlayerEvent
import com.example.kotlinmultiplatform.features.player.PlayerIntent
import com.example.kotlinmultiplatform.features.tasklist.LocalTaskListViewModel
import com.example.kotlinmultiplatform.features.tasklist.TaskListEvent
import com.example.kotlinmultiplatform.features.tasklist.TaskListIntent
import com.example.kotlinmultiplatform.features.tasklist.TaskListItem
import com.example.kotlinmultiplatform.navigation.PlatformBackHandler
import com.example.kotlinmultiplatform.navigation.RootEvent
import com.example.kotlinmultiplatform.navigation.RootViewModel
import com.example.kotlinmultiplatform.navigation.Screen
import kotlinx.coroutines.delay
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.draft_title
import org.jetbrains.compose.resources.stringResource

/**
 * AppRoot — drop-in replacement for AppNavigation using NavStack<Screen>.
 *
 * Mirrors AppNavigation's event-handling logic exactly.
 * Only the nav calls change:
 *   nav.push(Destination.X)  →  viewModel.goToX()
 *   nav.pop()                →  viewModel.goBack()
 *   nav.popToRoot()          →  viewModel.navStack.reset()
 *
 * Key correctness rules (same as AppNavigation):
 *   • All event collectors are keyed on the ViewModel instance (stable),
 *     not on `screen`, so they are never cancelled/relaunched on navigation.
 *   • VM Load intents are dispatched from LaunchedEffect(screen) — fired once
 *     when the destination actually changes, mirroring LaunchedEffect(destination).
 *   • The two-layer ScreenLayout (static top-bar + animated body) is preserved
 *     so each screen keeps its persistent toolbar while the body animates.
 */
@Composable
fun AppRoot(
    viewModel: RootViewModel,
    onExitApp: () -> Unit = {},
    modifier:  Modifier   = Modifier,
) {
    val settings    = LocalSettings.current
    val draftTitle  = stringResource(Res.string.draft_title)
    val focusManager = LocalFocusManager.current

    // ── Current screen ────────────────────────────────────────────────────────

    val screen by viewModel.currentScreen.collectAsState()

    // Track the previous screen so ScreenLayout can infer slide direction,
    // mirroring AppNavigation's lastNavEvent direction logic.
    var prevScreen by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) {
        prevScreen = screen
    }

    // ── Feature ViewModels ────────────────────────────────────────────────────

    val taskListVm    = LocalTaskListViewModel.current
    val taskListState by taskListVm.state.collectAsState()

    val newTaskVm    = LocalNewTaskViewModel.current
    val newTaskState by newTaskVm.state.collectAsState()

    val displayVm    = LocalDisplayViewModel.current
    val displayState by displayVm.state.collectAsState()

    val playerVm    = LocalPlayerViewModel.current
    val playerState by playerVm.state.collectAsState()

    // TextFieldStates live above AnimatedContent — survive screen transitions.
    val topicFieldState  = rememberTextFieldState()
    val scriptFieldState = rememberTextFieldState()

    // ── Initial load ──────────────────────────────────────────────────────────

    LaunchedEffect(taskListVm) {
        taskListVm.onIntent(TaskListIntent.Load)
    }

    // ── Text sync to NewTaskViewModel ─────────────────────────────────────────

    LaunchedEffect(topicFieldState.text) {
        newTaskVm.onIntent(NewTaskIntent.TopicChanged(topicFieldState.text.toString()))
    }
    LaunchedEffect(scriptFieldState.text) {
        newTaskVm.onIntent(NewTaskIntent.ScriptChanged(scriptFieldState.text.toString()))
    }

    // ── Init NewTaskViewModel when its screen becomes active ──────────────────

    LaunchedEffect(screen) {
        if (screen == Screen.NewTaskScreen) {
            newTaskVm.onIntent(
                NewTaskIntent.Init(
                    editingTaskId   = newTaskState.editingTaskId,
                    isPreviewReturn = newTaskState.isPreviewMode,
                    draftTitle      = draftTitle,
                )
            )
        }
    }

    // ── Dispatch Load to Display/Player VMs when their screen becomes active ──

    LaunchedEffect(screen) {
        when (screen) {
            is Screen.DisplayScreen -> Unit // DisplayEvent.NavigateToDetail triggers Load
            is Screen.PlayerScreen  -> Unit // PlayerEvent.NavigateToDetail triggers Load
            else                    -> Unit
        }
    }

    // ── TaskList events ───────────────────────────────────────────────────────
    // Key = taskListVm (stable) — never cancelled on screen change.

    LaunchedEffect(taskListVm) {
        taskListVm.events.collect { event ->
            when (event) {
                is TaskListEvent.NavigateToNewTask -> {
                    newTaskVm.onIntent(NewTaskIntent.Init(editingTaskId = null, draftTitle = draftTitle))
                    viewModel.goToNewTask()
                }
                is TaskListEvent.NavigateToEditTask -> {
                    newTaskVm.onIntent(NewTaskIntent.Init(editingTaskId = event.taskId, draftTitle = draftTitle))
                    viewModel.goToNewTask()
                }
            }
        }
    }

    // ── NewTask events ────────────────────────────────────────────────────────
    // Key = newTaskVm (stable) — never cancelled on screen change.

    LaunchedEffect(newTaskVm) {
        newTaskVm.events.collect { event ->
            when (event) {
                is NewTaskEvent.PrefillFields -> {
                    topicFieldState.edit  { replace(0, length, event.topic)  }
                    scriptFieldState.edit { replace(0, length, event.script) }
                }
                is NewTaskEvent.ClearFields -> {
                    topicFieldState.edit  { replace(0, length, "") }
                    scriptFieldState.edit { replace(0, length, "") }
                }
                is NewTaskEvent.DismissKeyboard -> {
                    focusManager.clearFocus(force = true)
                    delay(500)
                }
                is NewTaskEvent.NavigateToDetail -> {
                    taskListVm.onIntent(TaskListIntent.Load)
                    displayVm.onIntent(DisplayIntent.Load(event.taskId, event.isPreview))
                    viewModel.goToDisplay()
                }
                is NewTaskEvent.NavigateBack -> {
                    taskListVm.onIntent(TaskListIntent.Load)
                    viewModel.navStack.reset()
                }
            }
        }
    }

    // ── Display events ────────────────────────────────────────────────────────

    LaunchedEffect(displayVm) {
        displayVm.events.collect { event ->
            when (event) {
                is DisplayEvent.NavigateToPlay -> {
                    playerVm.onIntent(PlayerIntent.Load(event.taskId, event.isPreview))
                    viewModel.goToPlayer()
                }
                is DisplayEvent.NavigateBack -> {
                    if (event.isPreview) {
                        val task = displayState.task
                        if (task != null) {
                            newTaskVm.onIntent(
                                NewTaskIntent.ReturnFromPreview(
                                    title  = task.title,
                                    script = task.description,
                                )
                            )
                        }
                        // Pop back to the NewTaskScreen already on the stack.
                        // goToNewTask() would push a duplicate, making back-swipe
                        // land on DisplayScreen instead of TaskScreen.
                        viewModel.goBack()
                    } else {
                        viewModel.goBack()
                    }
                }
            }
        }
    }

    // ── WindowMode → PlayerViewModel ─────────────────────────────────────────

    LaunchedEffect(playerVm) {
        WindowModeObserver.isMultiWindow.collect { isMultiWindow ->
            playerVm.onIntent(
                if (isMultiWindow) PlayerIntent.EnterPip else PlayerIntent.ExitPip
            )
        }
    }

    // ── Player events ─────────────────────────────────────────────────────────

    LaunchedEffect(playerVm) {
        playerVm.events.collect { event ->
            when (event) {
                is PlayerEvent.NavigateToDetail -> {
                    // Pop back to the Display that is already on the stack.
                    // Do NOT push again — goToDisplay() would add a duplicate entry
                    // making back-swipe land on Player instead of NewTaskScreen.
                    displayVm.onIntent(DisplayIntent.Load(event.taskId, event.isPreview))
                    viewModel.goBack()
                }
                is PlayerEvent.NavigateToRoot      -> viewModel.navStack.reset()
                is PlayerEvent.RequestEnterPip     -> { /* platform layer */ }
                is PlayerEvent.RequestExitPip      -> { /* platform layer */ }
            }
        }
    }

    // ── System back (Android) ─────────────────────────────────────────────────

    PlatformBackHandler(enabled = true) {
        viewModel.handleBack()
    }

    // ── RootViewModel exit event ──────────────────────────────────────────────

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is RootEvent.ExitApp -> onExitApp()
            }
        }
    }

    // ── Screen layout ─────────────────────────────────────────────────────────

    ScreenLayout(
        screen     = screen,
        prevScreen = prevScreen,
        staticContent = { s ->
            when (s) {
                is Screen.TaskScreen    -> TaskScreenStatic(
                    searchActive  = taskListState.isSearchActive,
                    query         = taskListState.query,
                    onQueryChange = { taskListVm.onIntent(TaskListIntent.QueryChanged(it)) },
                    onClear       = { taskListVm.onIntent(TaskListIntent.QueryChanged("")) },
                    onBack        = { taskListVm.onIntent(TaskListIntent.SearchClosed) },
                    onSearchOpen  = { taskListVm.onIntent(TaskListIntent.SearchOpened) },
                )
                is Screen.DisplayScreen -> DisplayScreenStatic(
                    onBack = { displayVm.onIntent(DisplayIntent.BackClicked) },
                )
                is Screen.NewTaskScreen -> NewTaskScreenStatic(
                    onBack = { newTaskVm.onIntent(NewTaskIntent.BackPressed) },
                )
                is Screen.PlayerScreen  -> PlayerScreenStatic(
                    taskId         = playerState.task?.id ?: 0,
                    toolbarVisible = playerState.toolbarVisible,
                    onToolbarTap   = { playerVm.onIntent(PlayerIntent.ScreenTapped) },
                    onBack         = { playerVm.onIntent(PlayerIntent.BackClicked) },
                    onClose        = { playerVm.onIntent(PlayerIntent.CloseClicked) },
                )
            }
        },
        dynamicContent = { s ->
            when (s) {
                is Screen.TaskScreen -> TaskScreenBody(
                    visibleTasks = taskListState.visibleTasks.map { item ->
                        Task(
                            id           = item.id,
                            title        = item.title,
                            description  = item.description,
                            leadingShape = LeadingShapeType.entries
                                .getOrElse(item.leadingShapeOrdinal) { LeadingShapeType.HEART },
                        )
                    },
                    onDismiss   = { task ->
                        taskListVm.onIntent(TaskListIntent.TaskDismissed(
                            TaskListItem(task.id, task.title, task.description, task.leadingShape.ordinal)
                        ))
                    },
                    onItemClick = { task ->
                        taskListVm.onIntent(TaskListIntent.TaskClicked(
                            TaskListItem(task.id, task.title, task.description, task.leadingShape.ordinal)
                        ))
                    },
                    onNewClick  = { taskListVm.onIntent(TaskListIntent.NewTaskClicked) },
                    modifier    = Modifier.fillMaxSize(),
                )
                is Screen.NewTaskScreen -> {
                    val screenState = remember(topicFieldState, scriptFieldState, settings) {
                        NewTaskScreenState(
                            topicState  = topicFieldState,
                            scriptState = scriptFieldState,
                            settings    = settings,
                        )
                    }
                    NewTaskScreenBody(
                        state           = screenState,
                        onBack          = { newTaskVm.onIntent(NewTaskIntent.BackPressed) },
                        onNextClick     = { newTaskVm.onIntent(NewTaskIntent.NextClicked) },
                        showSaveDialog  = newTaskState.showSaveDialog,
                        onDismissDialog = { newTaskVm.onIntent(NewTaskIntent.DialogDismissed) },
                        onSave          = { newTaskVm.onIntent(NewTaskIntent.SaveConfirmed) },
                        onDiscard       = { newTaskVm.onIntent(NewTaskIntent.DiscardConfirmed) },
                        modifier        = Modifier.fillMaxSize(),
                    )
                }
                is Screen.DisplayScreen -> {
                    val task = displayState.task?.let { dt ->
                        Task(
                            id           = dt.id,
                            title        = dt.title,
                            description  = dt.description,
                            leadingShape = LeadingShapeType.entries
                                .getOrElse(dt.shapeOrdinal) { LeadingShapeType.HEART },
                        )
                    }
                    if (task != null) {
                        DisplayScreenBody(
                            task        = task,
                            onPlayClick = { displayVm.onIntent(DisplayIntent.PlayClicked) },
                            modifier    = Modifier.fillMaxSize(),
                        )
                    }
                }
                is Screen.PlayerScreen -> {
                    val task = playerState.task?.let { pt ->
                        Task(
                            id           = pt.id,
                            title        = pt.title,
                            description  = pt.description,
                            leadingShape = LeadingShapeType.entries
                                .getOrElse(pt.shapeOrdinal) { LeadingShapeType.HEART },
                        )
                    }
                    if (task != null) {
                        PlayerScreenBody(
                            task              = task,
                            onReadingComplete = { playerVm.onIntent(PlayerIntent.ReadingCompleted) },
                            modifier          = Modifier.fillMaxSize(),
                        )
                    }
                }
            }
        },
        modifier = modifier,
    )
}

// ── ScreenLayout ──────────────────────────────────────────────────────────────
// Mirrors AppNavigation's ScreenLayout exactly.
// Static layer (top-bar) fades independently; dynamic layer (body) slides.

@Composable
private fun ScreenLayout(
    screen:         Screen,
    prevScreen:     Screen,
    staticContent:  @Composable (Screen) -> Unit,
    dynamicContent: @Composable (Screen) -> Unit,
    modifier:       Modifier = Modifier,
) {
    SafeAreaLayout {
        Box(modifier = modifier.fillMaxSize()) {

            // Static layer — top-bar fades on destination change.
            AnimatedContent(
                targetState    = screen,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label          = "staticLayer",
                modifier       = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            ) { s -> staticContent(s) }

            // Dynamic layer — body slides.
            AnimatedContent(
                targetState    = screen,
                transitionSpec = {
                    val isBack = targetState.ordinal < initialState.ordinal

                    when {
                        // Preview-back: DisplayScreen(preview) → NewTaskScreen slides right-to-left
                        initialState is Screen.DisplayScreen &&
                        targetState  is Screen.NewTaskScreen ->
                            slideInHorizontally(tween(350)) { -it } togetherWith
                            slideOutHorizontally(tween(350)) { it }

                        // Any back navigation slides right-to-left
                        isBack ->
                            slideInHorizontally(tween(350)) { -it } togetherWith
                            slideOutHorizontally(tween(350)) { it }

                        // PlayerScreen → DisplayScreen (back from player)
                        initialState is Screen.PlayerScreen &&
                        targetState  is Screen.DisplayScreen ->
                            slideInHorizontally(tween(350)) { -it } togetherWith
                            slideOutHorizontally(tween(350)) { it } using
                            SizeTransform(clip = true)

                        // Forward navigation slides left-to-right
                        else ->
                            slideInHorizontally(tween(350)) { it } togetherWith
                            slideOutHorizontally(tween(350)) { -it } using
                            SizeTransform(clip = true)
                    }
                },
                label    = "dynamicLayer",
                modifier = Modifier.fillMaxSize(),
            ) { s -> dynamicContent(s) }
        }
    }
}

// ── Ordinal helper ────────────────────────────────────────────────────────────

private val Screen.ordinal: Int get() = when (this) {
    is Screen.TaskScreen    -> 0
    is Screen.NewTaskScreen -> 1
    is Screen.DisplayScreen -> 2
    is Screen.PlayerScreen  -> 3
}
