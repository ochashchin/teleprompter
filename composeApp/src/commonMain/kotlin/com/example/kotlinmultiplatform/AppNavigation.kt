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
import com.example.kotlinmultiplatform.core.WindowModeObserver
import com.example.kotlinmultiplatform.frame.FrameViewModel
import com.example.kotlinmultiplatform.frame.FrameVmIntent
import com.example.kotlinmultiplatform.navigation.NavigationUiEvent
import kotlinx.coroutines.delay
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.draft_title
import org.jetbrains.compose.resources.stringResource

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

// ── AppNavigation ─────────────────────────────────────────────────────────────
//
// ┌─────────────────────────────────────────────────────────────────────────┐
// │  Step 4 — what was REMOVED                                              │
// │    • readTask() / keyTitle/keyDesc/keyIcon / KEY_IDS                    │
// │    • Destination.Detail(task: Task) → now Detail(taskId: Int)           │
// │    • Destination.PlayDetail(task: Task) → now PlayDetail(taskId: Int)   │
// │    • var playerToolbarVisible / playerHideDeadlineMs                    │
// │    • LaunchedEffect toolbar timer (2× LaunchedEffects)                  │
// │    • val onToolbarTap lambda                                             │
// │    • Task.toListItem() projection                                        │
// │                                                                         │
// │  Step 4 — what was ADDED                                                │
// │    • displayVm / playerVm from CompositionLocals                        │
// │    • LaunchedEffect(destination) dispatches Load to displayVm/playerVm  │
// │    • Event collectors for DisplayEvent and PlayerEvent                   │
// │    • DisplayScreenBody receives a Task built from displayVm.state       │
// │    • PlayerScreenBody receives a Task built from playerVm.state         │
// └─────────────────────────────────────────────────────────────────────────┘

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
        when (val dest = destination) {
            is Destination.Detail    ->
                displayVm.onIntent(DisplayIntent.Load(dest.taskId, dest.isPreview))
            is Destination.PlayDetail ->
                playerVm.onIntent(PlayerIntent.Load(dest.taskId, dest.isPreview))
            else -> Unit
        }
    }

    // Collect TaskList events
    LaunchedEffect(taskListVm) {
        taskListVm.events.collect { event ->
            when (event) {
                is TaskListEvent.NavigateToNewTask -> {
                    newTaskVm.onIntent(NewTaskIntent.Init(editingTaskId = null, draftTitle = draftTitle))
                    nav.push(Destination.NewDetail)
                }
                is TaskListEvent.NavigateToEditTask -> {
                    newTaskVm.onIntent(NewTaskIntent.Init(editingTaskId = event.taskId, draftTitle = draftTitle))
                    nav.push(Destination.NewDetail)
                }
            }
        }
    }

    // Collect NewTask events
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
                    // ← STEP 4: push id-only destination — no Task object
                    nav.push(Destination.Detail(taskId = event.taskId, isPreview = event.isPreview))
                }
                is NewTaskEvent.NavigateBack -> {
                    taskListVm.onIntent(TaskListIntent.Load)
                    nav.popToRoot()
                }
                is NewTaskEvent.NavigateBackAfterSave -> {
                    // Style migration is handled by AppRoot; AppNavigation only needs to pop.
                    taskListVm.onIntent(TaskListIntent.Load)
                    nav.popToRoot()
                }
            }
        }
    }

    // Collect Display events                                           ← STEP 4
    LaunchedEffect(displayVm) {
        displayVm.events.collect { event ->
            when (event) {
                is DisplayEvent.NavigateToPlay -> {
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
                }
                is DisplayEvent.NavigateBack ->
                    if (event.isPreview) {
                        // Preview back: restore fields then return to NewDetail
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
                if (isMultiWindow) PlayerIntent.EnterPip else PlayerIntent.ExitPip
            )
        }
    }

    // Collect Player events                                            ← STEP 4
    LaunchedEffect(playerVm) {
        playerVm.events.collect { event ->
            when (event) {
                is PlayerEvent.NavigateToDetail ->
                    nav.push(Destination.Detail(taskId = event.taskId, isPreview = event.isPreview))
                is PlayerEvent.NavigateToRoot ->
                    nav.popToRoot()
                is PlayerEvent.RequestEnterPip -> { /* platform layer handles */ }
                is PlayerEvent.RequestExitPip  -> { /* platform layer handles */ }
            }
        }
    }

    // ── Screen layout ──────────────────────────────────────────────────────

    ScreenLayout(
        destination  = destination,
        lastNavEvent = lastNavEvent,
        staticContent = { dest ->
            when (dest) {
                is Destination.TaskList -> TaskScreenStatic(
                    searchActive  = taskListState.isSearchActive,
                    query         = taskListState.query,
                    onQueryChange = { taskListVm.onIntent(TaskListIntent.QueryChanged(it)) },
                    onClear       = { taskListVm.onIntent(TaskListIntent.QueryChanged("")) },
                    onBack        = { taskListVm.onIntent(TaskListIntent.SearchClosed) },
                    onSearchOpen  = { taskListVm.onIntent(TaskListIntent.SearchOpened) },
                )
                is Destination.Detail -> DisplayScreenStatic(
                    // ← STEP 4: back handled via intent, not inline lambda
                    onBack = { displayVm.onIntent(DisplayIntent.BackClicked) },
                )
                is Destination.NewDetail -> NewTaskScreenStatic(
                    onBack = { newTaskVm.onIntent(NewTaskIntent.BackPressed) },
                )
                is Destination.PlayDetail -> PlayerScreenStatic(
                    // ← STEP 4: taskId from destination, toolbar from playerState
                    taskId         = dest.taskId,
                    toolbarVisible = playerState.toolbarVisible,
                    onToolbarTap   = { playerVm.onIntent(PlayerIntent.ScreenTapped) },
                    onBack         = { playerVm.onIntent(PlayerIntent.BackClicked) },
                    onClose        = { playerVm.onIntent(PlayerIntent.CloseClicked) },
                )
            }
        },
        dynamicContent = { dest ->
            when (dest) {
                is Destination.TaskList -> TaskScreenBody(
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
                is Destination.Detail -> {
                    // ← STEP 4: build Task from displayState for DisplayScreenBody.
                    // DisplayScreenBody signature is unchanged — no edits to DisplayScreen.kt.
                    // While task is loading the body renders nothing (handled by isLoading).
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
                }
                is Destination.PlayDetail -> {
                    // ← STEP 4: build Task from playerState for PlayerScreenBody.
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
                is Destination.NewDetail -> {
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
                    when {
                        initialState is Destination.Detail &&
                        (initialState as Destination.Detail).isPreview &&
                        targetState is Destination.NewDetail ->
                            slideInHorizontally(tween(350)) { -it } togetherWith
                            slideOutHorizontally(tween(350)) { it }

                        isBack || targetState is Destination.TaskList ->
                            slideInHorizontally(tween(350)) { -it } togetherWith
                            slideOutHorizontally(tween(350)) { it }

                        initialState is Destination.PlayDetail &&
                        targetState is Destination.Detail ->
                            slideInHorizontally(tween(350)) { -it } togetherWith
                            slideOutHorizontally(tween(350)) { it } using
                            SizeTransform(clip = true)

                        else ->
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
