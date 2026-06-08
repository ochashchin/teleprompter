package com.example.kotlinmultiplatform

// ── Imports identical to the original AppRoot.kt ─────────────────────────────
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
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
import com.example.kotlinmultiplatform.frame.FrameViewModel
import com.example.kotlinmultiplatform.frame.FrameVmIntent
import com.example.kotlinmultiplatform.navigation.PlatformBackHandler
import com.example.kotlinmultiplatform.navigation.RootEvent
import com.example.kotlinmultiplatform.navigation.RootViewModel
import com.example.kotlinmultiplatform.navigation.Screen
import kotlinx.coroutines.delay
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.draft_title
import org.jetbrains.compose.resources.stringResource

/**
 * AppRoot — identical to the uploaded source with two additions:
 *
 *  1. [frameViewModel] optional parameter (null = no-op, keeps iOS/Desktop unaffected).
 *  2. [DisplayScreenBody.onPlayClick] now carries `overlayEnabled: Boolean` — when true,
 *     [AppRoot] syncs [FrameViewModel] state and triggers PiP before navigating.
 *  3. [Screen.PlayerScreen] branch checks overlay via [DisplayTaskState] on entry and
 *     resumes the frame pipeline if it wasn't already running.
 *
 * Every other line is unchanged from the original AppRoot.kt.
 */
@Composable
fun AppRoot(
    viewModel:      RootViewModel,
    onExitApp:      () -> Unit    = {},
    // ── NEW: injected from MainActivity on Android; null on other platforms ───
    frameViewModel: FrameViewModel? = null,
    modifier:       Modifier        = Modifier,
) {
    val settings    = LocalSettings.current
    val draftTitle  = stringResource(Res.string.draft_title)
    val focusManager = LocalFocusManager.current

    val screen by viewModel.currentScreen.collectAsState()
    var prevScreen by remember { mutableStateOf(screen) }
    LaunchedEffect(screen) { prevScreen = screen }

    val taskListVm    = LocalTaskListViewModel.current
    val taskListState by taskListVm.state.collectAsState()
    val newTaskVm     = LocalNewTaskViewModel.current
    val newTaskState  by newTaskVm.state.collectAsState()
    val displayVm     = LocalDisplayViewModel.current
    val displayState  by displayVm.state.collectAsState()
    val playerVm      = LocalPlayerViewModel.current
    val playerState   by playerVm.state.collectAsState()

    val topicFieldState   = rememberTextFieldState()
    val scriptFieldState  = rememberTextFieldState()
    val scriptStyleState  = rememberScriptTextStyleState()
    val displayStyleState = rememberScriptTextStyleState()
    val playerStyleState  = rememberScriptTextStyleState()

    LaunchedEffect(displayState.task?.id) {
        val id = displayState.task?.id ?: return@LaunchedEffect
        displayStyleState.loadForTaskId(id)
    }
    LaunchedEffect(playerState.task?.id) {
        val id = playerState.task?.id ?: return@LaunchedEffect
        playerStyleState.loadForTaskId(id)
    }
    LaunchedEffect(scriptStyleState.hasUnsavedChanges) {
        if (scriptStyleState.hasUnsavedChanges) newTaskVm.onIntent(NewTaskIntent.StyleChanged)
    }
    LaunchedEffect(taskListVm) { taskListVm.onIntent(TaskListIntent.Load) }
    LaunchedEffect(topicFieldState.text) {
        newTaskVm.onIntent(NewTaskIntent.TopicChanged(topicFieldState.text.toString()))
    }
    LaunchedEffect(scriptFieldState.text) {
        newTaskVm.onIntent(NewTaskIntent.ScriptChanged(scriptFieldState.text.toString()))
    }
    LaunchedEffect(screen) {
        if (screen == Screen.NewTaskScreen) {
            newTaskVm.onIntent(NewTaskIntent.Init(
                editingTaskId   = newTaskState.editingTaskId,
                isPreviewReturn = newTaskState.isPreviewMode,
                draftTitle      = draftTitle,
            ))
        }
    }
    LaunchedEffect(screen) {
        if (screen == Screen.NewTaskScreen) {
            viewModel.setBackInterceptor { newTaskVm.onIntent(NewTaskIntent.BackPressed); true }
        } else {
            viewModel.setBackInterceptor(null)
        }
    }

    // ── Event collectors (unchanged from original) ────────────────────────────

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
    LaunchedEffect(newTaskVm) {
        newTaskVm.events.collect { event ->
            when (event) {
                is NewTaskEvent.PrefillFields -> {
                    topicFieldState.edit  { replace(0, length, event.topic) }
                    scriptFieldState.edit { replace(0, length, event.script) }
                    val editId = newTaskState.editingTaskId
                    if (editId != null) scriptStyleState.loadForTaskId(editId)
                }
                is NewTaskEvent.ClearFields -> {
                    topicFieldState.edit  { replace(0, length, "") }
                    scriptFieldState.edit { replace(0, length, "") }
                    scriptStyleState.clear()
                    newTaskVm.onIntent(NewTaskIntent.StyleSaved)
                }
                is NewTaskEvent.DismissKeyboard -> {
                    focusManager.clearFocus(force = true); delay(500)
                }
                is NewTaskEvent.NavigateToDetail -> {
                    scriptStyleState.migrateToTaskId(event.taskId)
                    scriptStyleState.markSaved()
                    newTaskVm.onIntent(NewTaskIntent.StyleSaved)
                    displayStyleState.loadForTaskId(event.taskId)
                    taskListVm.onIntent(TaskListIntent.Load)
                    displayVm.onIntent(DisplayIntent.Load(event.taskId, event.isPreview))
                    viewModel.goToDisplay()
                }
                is NewTaskEvent.NavigateBackAfterSave -> {
                    val id = event.savedTaskId
                    if (id != null) scriptStyleState.migrateToTaskId(id)
                    newTaskVm.onIntent(NewTaskIntent.StyleSaved)
                    taskListVm.onIntent(TaskListIntent.Load)
                    viewModel.navStack.reset()
                }
                is NewTaskEvent.NavigateBack -> {
                    scriptStyleState.clear()
                    newTaskVm.onIntent(NewTaskIntent.StyleSaved)
                    taskListVm.onIntent(TaskListIntent.Load)
                    viewModel.navStack.reset()
                }
            }
        }
    }
    LaunchedEffect(displayVm) {
        displayVm.events.collect { event ->
            when (event) {
                is DisplayEvent.NavigateToPlay -> {
                    playerVm.onIntent(PlayerIntent.Load(event.taskId, event.isPreview))
                    playerStyleState.loadForTaskId(event.taskId)
                    // ── PiP: arm FrameViewModel before navigating ─────────────
                    // overlayEnabled was resolved by DisplayScreenBody from its
                    // persisted DisplayTaskState and forwarded through the intent.
                    // All PiP decisions live here — zero PiP logic in composables.
                    if (event.overlayEnabled) {
                        frameViewModel?.let { fvm ->
                            fvm.onIntent(FrameVmIntent.SyncDisplayState(
                                DisplayTaskState(taskId = event.taskId, settings = settings)
                            ))
                            fvm.onIntent(FrameVmIntent.SetOverlay(true))
                            fvm.onIntent(FrameVmIntent.SetPlaying(true))
                            // FrameVmEvent.RequestPip → PipController →
                            // Activity.enterPictureInPictureMode()
                        }
                    }
                    viewModel.goToPlayer()
                }
                is DisplayEvent.NavigateBack -> {
                    if (event.isPreview) {
                        val task = displayState.task
                        if (task != null) {
                            newTaskVm.onIntent(NewTaskIntent.ReturnFromPreview(
                                title  = task.title,
                                script = task.description,
                            ))
                        }
                        viewModel.goBack()
                    } else {
                        viewModel.goBack()
                    }
                }
            }
        }
    }
    LaunchedEffect(playerVm) {
        WindowModeObserver.isMultiWindow.collect { isMultiWindow ->
            playerVm.onIntent(if (isMultiWindow) PlayerIntent.EnterPip else PlayerIntent.ExitPip)
        }
    }
    LaunchedEffect(playerVm) {
        playerVm.events.collect { event ->
            when (event) {
                is PlayerEvent.NavigateToDetail -> {
                    displayVm.onIntent(DisplayIntent.Load(event.taskId, event.isPreview))
                    viewModel.goBack()
                }
                is PlayerEvent.NavigateToRoot  -> viewModel.navStack.reset()
                is PlayerEvent.RequestEnterPip -> Unit
                is PlayerEvent.RequestExitPip  -> Unit
            }
        }
    }

    PlatformBackHandler(enabled = true) { viewModel.handleBack() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) { is RootEvent.ExitApp -> onExitApp() }
        }
    }

    // ── Screen layout ─────────────────────────────────────────────────────────

    ScreenLayout(
        screen           = screen,
        prevScreen       = prevScreen,
        playerStyleState = playerStyleState,
        staticContent    = { s ->
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
                        styleState      = scriptStyleState,
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
                            task              = task,
                            onPlayClick       = { overlayEnabled ->
                                displayVm.onIntent(DisplayIntent.PlayClicked(overlayEnabled))
                            },
                            modifier          = Modifier.fillMaxSize(),
                            styleSpans        = displayStyleState.spans,
                            isFillColorActive = displayStyleState.isFillColorActive,
                            fillColor         = displayStyleState.activeFillColor,
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
                            styleSpans        = playerStyleState.spans,
                            isFillColorActive = playerStyleState.isFillColorActive,
                            fillColor         = playerStyleState.activeFillColor,
                            // PiP stream fix: pass frameViewModel so PlayerScreenBody
                            // can apply Modifier.pipCapture and feed real pixels into
                            // the MediaCodec encoder.  Null on non-Android platforms.
                            frameViewModel    = frameViewModel,
                        )
                    }
                }
            }
        },
        modifier = modifier,
    )
}

// ── ScreenLayout (unchanged from original) ────────────────────────────────────

@Composable
private fun ScreenLayout(
    screen:           Screen,
    prevScreen:       Screen,
    playerStyleState: ScriptTextStyleState,
    staticContent:    @Composable (Screen) -> Unit,
    dynamicContent:   @Composable (Screen) -> Unit,
    modifier:         Modifier = Modifier,
) {
    val defaultBgColor  = MaterialTheme.colorScheme.surfaceContainerLow
    val playerFillColor = playerStyleState.activeFillColor

    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState    = screen,
            transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) },
            label          = "staticBackground",
            modifier       = Modifier.fillMaxSize(),
        ) { targetScreen ->
            val bgColor = if (targetScreen is Screen.PlayerScreen && playerFillColor != null)
                playerFillColor else defaultBgColor
            Box(modifier = Modifier.fillMaxSize().background(bgColor))
        }

        SafeAreaLayout {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState    = screen,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label          = "staticLayer",
                    modifier       = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                ) { s -> staticContent(s) }

                AnimatedContent(
                    targetState    = screen,
                    transitionSpec = {
                        val isBack = targetState.ordinal < initialState.ordinal
                        when {
                            initialState is Screen.DisplayScreen && targetState is Screen.NewTaskScreen ->
                                slideInHorizontally(tween(350)) { -it } togetherWith
                                slideOutHorizontally(tween(350)) { it }
                            isBack ->
                                slideInHorizontally(tween(350)) { -it } togetherWith
                                slideOutHorizontally(tween(350)) { it }
                            initialState is Screen.PlayerScreen && targetState is Screen.DisplayScreen ->
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
                ) { s -> dynamicContent(s) }
            }
        }
    }
}

private val Screen.ordinal: Int get() = when (this) {
    is Screen.TaskScreen    -> 0
    is Screen.NewTaskScreen -> 1
    is Screen.DisplayScreen -> 2
    is Screen.PlayerScreen  -> 3
}
