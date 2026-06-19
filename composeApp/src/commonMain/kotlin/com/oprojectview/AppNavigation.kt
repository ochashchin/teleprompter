package com.oprojectview

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
import kotlinx.coroutines.delay
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.draft_title
import org.jetbrains.compose.resources.stringResource
import com.oprojectview.core.WindowModeObserver
import com.oprojectview.features.display.DisplayEvent
import com.oprojectview.features.display.DisplayIntent
import com.oprojectview.features.display.LocalDisplayViewModel
import com.oprojectview.features.newtask.LocalNewTaskViewModel
import com.oprojectview.features.newtask.NewTaskEvent
import com.oprojectview.features.newtask.NewTaskIntent
import com.oprojectview.features.player.LocalPlayerViewModel
import com.oprojectview.features.player.PlayerEvent
import com.oprojectview.features.player.PlayerIntent
import com.oprojectview.features.tasklist.LocalTaskListViewModel
import com.oprojectview.features.tasklist.TaskListEvent
import com.oprojectview.features.tasklist.TaskListIntent
import com.oprojectview.features.tasklist.TaskListItem
import com.oprojectview.frame.FrameViewModel
import com.oprojectview.frame.FrameVmIntent
import com.oprojectview.navigation.NavigationUiEvent

// ── Destination ───────────────────────────────────────────────────────────────
//
// Step 4: Detail and PlayDetail carry only an id — no Task object crosses
// navigation boundaries. Each screen's ViewModel loads the full task from
// Settings when it receives a Load intent.

sealed interface Destination {
    data object TaskList  : Destination
    data object NewDetail : Destination
    data class  Detail(val taskId: Int, val isPreview: Boolean = false)     : Destination
    data class  PlayDetail(val taskId: Int, val isPreview: Boolean = false) : Destination
}


@Composable
fun AppNavigation(
    modifier:       Modifier        = Modifier,
    frameViewModel: FrameViewModel? = null,
) {
    val settings   = LocalSettings.current
    val draftTitle = stringResource(Res.string.draft_title)

    // ── Navigation VM (Step 1) ─────────────────────────────────────────────

    val nav         = LocalNavViewModel.current
    val navState    by nav.state.collectAsState()
    val destination  = navState.current

    var lastNavEvent by remember { mutableStateOf<NavigationUiEvent<Destination>?>(null) }
    LaunchedEffect(nav) {
        nav.events.collect { event -> lastNavEvent = event }
    }

    // ── TaskList VM (Step 2) ───────────────────────────────────────────────

    val taskListVm    = LocalTaskListViewModel.current
    val taskListState by taskListVm.state.collectAsState()

    LaunchedEffect(taskListVm) {
        taskListVm.onIntent(TaskListIntent.Load)
    }

    // ── NewTask VM (Step 3) ────────────────────────────────────────────────

    val newTaskVm    = LocalNewTaskViewModel.current
    val newTaskState by newTaskVm.state.collectAsState()
    val focusManager = LocalFocusManager.current

    // TextFieldState objects created above AnimatedContent — survive transitions
    val topicFieldState  = rememberTextFieldState()
    val scriptFieldState = rememberTextFieldState()

    LaunchedEffect(topicFieldState.text) {
        newTaskVm.onIntent(NewTaskIntent.TopicChanged(topicFieldState.text.toString()))
    }
    LaunchedEffect(scriptFieldState.text) {
        newTaskVm.onIntent(NewTaskIntent.ScriptChanged(scriptFieldState.text.toString()))
    }

    LaunchedEffect(destination) {
        if (destination == Destination.NewDetail) {
            newTaskVm.onIntent(
                NewTaskIntent.Init(
                    editingTaskId   = newTaskState.editingTaskId,
                    isPreviewReturn = newTaskState.isPreviewMode,
                    draftTitle      = draftTitle,
                )
            )
        }
    }

    // ── Display VM (Step 4) ────────────────────────────────────────────────

    val displayVm    = LocalDisplayViewModel.current                // ← STEP 4
    val displayState by displayVm.state.collectAsState()            // ← STEP 4

    // ── Player VM (Step 4) ─────────────────────────────────────────────────

    val playerVm    = LocalPlayerViewModel.current                  // ← STEP 4
    val playerState by playerVm.state.collectAsState()              // ← STEP 4

    // Dispatch Load to the right VM when destination changes.          ← STEP 4
    LaunchedEffect(destination) {
        val dest = destination
        if (dest is Destination.Detail) {
            displayVm.onIntent(DisplayIntent.Load(dest.taskId, dest.isPreview))
        } else if (dest is Destination.PlayDetail) {
            val displayTaskState = DisplayTaskState(taskId = dest.taskId, settings = settings)
            val speedItem = DisplayTaskList.first { it.id == 3 }
            val selectedSpeedIndex = displayTaskState.selectedIndex(speedItem) ?: speedItem.defaultIndex
            val wpm = speedIndexToWpm(selectedSpeedIndex)
            playerVm.onIntent(PlayerIntent.Load(dest.taskId, dest.isPreview, wpm))
        }
    }

    // Collect TaskList events
    LaunchedEffect(taskListVm) {
        taskListVm.events.collect { event ->
            if (event is TaskListEvent.NavigateToNewTask) {
                newTaskVm.onIntent(NewTaskIntent.Init(editingTaskId = null, draftTitle = draftTitle))
                nav.push(Destination.NewDetail)
            } else if (event is TaskListEvent.NavigateToEditTask) {
                newTaskVm.onIntent(NewTaskIntent.Init(editingTaskId = event.taskId, draftTitle = draftTitle))
                nav.push(Destination.NewDetail)
            }
        }
    }

    // Collect NewTask events
    LaunchedEffect(newTaskVm) {
        newTaskVm.events.collect { event ->
            if (event is NewTaskEvent.PrefillFields) {
                topicFieldState.edit  { replace(0, length, event.topic)  }
                scriptFieldState.edit { replace(0, length, event.script) }
            } else if (event is NewTaskEvent.ClearFields) {
                topicFieldState.edit  { replace(0, length, "") }
                scriptFieldState.edit { replace(0, length, "") }
            } else if (event is NewTaskEvent.DismissKeyboard) {
                focusManager.clearFocus(force = true)
                delay(500)
            } else if (event is NewTaskEvent.NavigateToDetail) {
                taskListVm.onIntent(TaskListIntent.Load)
                nav.push(Destination.Detail(taskId = event.taskId, isPreview = event.isPreview))
            } else if (event is NewTaskEvent.NavigateBack) {
                taskListVm.onIntent(TaskListIntent.Load)
                nav.popToRoot()
            } else if (event is NewTaskEvent.NavigateBackAfterSave) {
                taskListVm.onIntent(TaskListIntent.Load)
                nav.popToRoot()
            }
        }
    }

    // Collect Display events                                           ← STEP 4
    LaunchedEffect(displayVm) {
        displayVm.events.collect { event ->
            if (event is DisplayEvent.NavigateToPlay) {
                if (event.overlayEnabled) {
                    frameViewModel?.let { fvm ->
                        fvm.onIntent(FrameVmIntent.SyncDisplayState(
                            DisplayTaskState(taskId = event.taskId, settings = settings)
                        ))
                        fvm.onIntent(FrameVmIntent.SetOverlay(true))
                        fvm.onIntent(FrameVmIntent.SetPlaying(true))
                    }
                }
                nav.push(Destination.PlayDetail(taskId = event.taskId, isPreview = event.isPreview))
            } else if (event is DisplayEvent.NavigateBack) {
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
                    nav.push(Destination.NewDetail)
                } else {
                    nav.pop()
                }
            }
        }
    }

    // Observe split-screen / multi-window mode → PlayerViewModel
    LaunchedEffect(playerVm) {
        WindowModeObserver.isMultiWindow.collect { isMultiWindow ->
            playerVm.onIntent(
                if (isMultiWindow) PlayerIntent.EnterPip() else PlayerIntent.ExitPip
            )
        }
    }

    // Collect Player events                                            ← STEP 4
    LaunchedEffect(playerVm) {
        playerVm.events.collect { event ->
            if (event is PlayerEvent.NavigateToDetail) {
                nav.push(Destination.Detail(taskId = event.taskId, isPreview = event.isPreview))
            } else if (event is PlayerEvent.NavigateToRoot) {
                nav.popToRoot()
            } else if (event is PlayerEvent.RequestEnterPip) {
                /* platform layer handles */
            } else if (event is PlayerEvent.RequestExitPip) {
                /* platform layer handles */
            }
        }
    }

    // ── Screen layout ──────────────────────────────────────────────────────

    ScreenLayout(
        destination  = destination,
        lastNavEvent = lastNavEvent,
        staticContent = { dest ->
            if (dest is Destination.TaskList) {
                TaskScreenStatic(
                    searchActive  = taskListState.isSearchActive,
                    query         = taskListState.query,
                    onQueryChange = { taskListVm.onIntent(TaskListIntent.QueryChanged(it)) },
                    onClear       = { taskListVm.onIntent(TaskListIntent.QueryChanged("")) },
                    onBack        = { taskListVm.onIntent(TaskListIntent.SearchClosed) },
                    onSearchOpen  = { taskListVm.onIntent(TaskListIntent.SearchOpened) },
                )
            } else if (dest is Destination.Detail) {
                DisplayScreenStatic(
                    onBack = { displayVm.onIntent(DisplayIntent.BackClicked) },
                )
            } else if (dest is Destination.NewDetail) {
                NewTaskScreenStatic(
                    onBack = { newTaskVm.onIntent(NewTaskIntent.BackPressed) },
                )
            } else if (dest is Destination.PlayDetail) {
                PlayerScreenStatic(
                    taskId         = dest.taskId,
                    toolbarVisible = playerState.toolbarVisible,
                    isPlaying      = playerState.isPlaying,
                    isFinished     = playerState.scrollFraction >= 1f,
                    onToolbarTap   = { playerVm.onIntent(PlayerIntent.ScreenTapped) },
                    onBack         = { playerVm.onIntent(PlayerIntent.BackClicked) },
                    onClose        = { playerVm.onIntent(PlayerIntent.CloseClicked) },
                    onPlayPauseClick = { playerVm.onIntent(PlayerIntent.SetPlaying(!playerState.isPlaying)) },
                    onReplayClick  = { playerVm.onIntent(PlayerIntent.ReplayClicked) },
                )
            }
        },
        dynamicContent = { dest ->
            if (dest is Destination.TaskList) {
                TaskScreenBody(
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
            } else if (dest is Destination.Detail) {
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
                    val displayTaskState = remember(task.id, settings) {
                        DisplayTaskState(taskId = task.id, settings = settings)
                    }
                    val overlayItem    = remember { DisplayTaskList.first { it.id == 8 } }
                    val overlayEnabled = remember(task.id) {
                        (displayTaskState.selectedIndex(overlayItem)
                            ?: overlayItem.defaultIndex) == 1
                    }
                    DisplayScreenBody(
                        task        = task,
                        onPlayClick = {
                            displayVm.onIntent(DisplayIntent.PlayClicked(overlayEnabled))
                        },
                        modifier    = Modifier.fillMaxSize(),
                    )
                }
            } else if (dest is Destination.PlayDetail) {
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
            } else if (dest is Destination.NewDetail) {
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
        },
        modifier = modifier,
    )
}

// ── ScreenLayout (unchanged from Step 3) ─────────────────────────────────────

@Composable
private fun ScreenLayout(
    destination:    Destination,
    lastNavEvent:   NavigationUiEvent<Destination>?,
    staticContent:  @Composable (Destination) -> Unit,
    dynamicContent: @Composable (Destination) -> Unit,
    modifier:       Modifier = Modifier,
) {
    SafeAreaLayout {
        Box(modifier = modifier.fillMaxSize()) {

            AnimatedContent(
                targetState    = destination,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label          = "staticLayer",
                modifier       = Modifier.fillMaxWidth().align(Alignment.TopCenter),
            ) { dest -> staticContent(dest) }

            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    val isBack = lastNavEvent is NavigationUiEvent.TransitionBack ||
                            lastNavEvent is NavigationUiEvent.PopToRoot

                    if (initialState is Destination.Detail &&
                        (initialState as Destination.Detail).isPreview &&
                        targetState is Destination.NewDetail) {
                        slideInHorizontally(tween(350)) { -it } togetherWith
                                slideOutHorizontally(tween(350)) { it }
                    } else if (isBack || targetState is Destination.TaskList) {
                        slideInHorizontally(tween(350)) { -it } togetherWith
                                slideOutHorizontally(tween(350)) { it }
                    } else if (initialState is Destination.PlayDetail &&
                        targetState is Destination.Detail) {
                        slideInHorizontally(tween(350)) { -it } togetherWith
                                slideOutHorizontally(tween(350)) { it } using
                                SizeTransform(clip = true)
                    } else {
                        slideInHorizontally(tween(350)) { it } togetherWith
                                slideOutHorizontally(tween(350)) { -it } using
                                SizeTransform(clip = true)
                    }
                },
                label    = "dynamicLayer",
                modifier = Modifier.fillMaxSize(),
            ) { dest -> dynamicContent(dest) }
        }
    }
}