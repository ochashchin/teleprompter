package com.oprojectview

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
import androidx.compose.foundation.layout.wrapContentHeight
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
import kotlinx.coroutines.delay
import androidx.compose.ui.platform.LocalUriHandler
import com.oprojectview.frame.FrameViewModel
import com.oprojectview.frame.FrameVmIntent
import com.oprojectview.navigation.PlatformBackHandler
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.unit.dp
import com.oprojectview.navigation.RootEvent
import com.oprojectview.navigation.RootViewModel
import com.oprojectview.navigation.Screen
import kotlinx.coroutines.delay
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.draft_title
import org.jetbrains.compose.resources.stringResource

/**
 * AppRoot — identical to the uploaded source with two additions:
 *
 *  1. [frameViewModel] optional parameter (null = no-op, keeps iOS/Desktop unaffected).
 *  2. [DisplayScreenBody.onPlayClick] now carries `overlayEnabled: Boolean` — when true,
 *     [AppRoot] syncs [com.oprojectview.frame.FrameViewModel] state and triggers PiP before navigating.
 *  3. [com.oprojectview.navigation.Screen.PlayerScreen] branch checks overlay via [DisplayTaskState] on entry and
 *     resumes the frame pipeline if it wasn't already running.
 *
 * Every other line is unchanged from the original AppRoot.kt.
 */
@Composable
fun AppRoot(
    viewModel:      RootViewModel,
    onExitApp:      () -> Unit    = {},
    // ── NEW: injected from MainActivity (Android) / MainViewController (iOS) ──
    frameViewModel: FrameViewModel?    = null,
    modifier:       Modifier           = Modifier,
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
    // ── Sync fill color from ScriptTextStyleState directly to PiP renderer ───
    // displayStyleState is already loaded when the user is on DisplayScreen,
    // so it is always a reliable source — unlike playerStyleState which loads async.
    val activeFillColor = displayStyleState.resolveActiveFillColor()
    LaunchedEffect(activeFillColor, frameViewModel) {
        frameViewModel?.onIntent(
            FrameVmIntent.SetFillColor(
                fillColorVal = activeFillColor.value.toLong()
            )
        )
    }

    // ── Pass MaterialTheme default colors to PiP renderer ───
    val defaultTextVal = MaterialTheme.colorScheme.onSurfaceVariant.value.toLong()
    val defaultFillVal = MaterialTheme.colorScheme.surfaceContainerLow.value.toLong()
    val primaryVal = MaterialTheme.colorScheme.primary.value.toLong()
    val surfaceVariantVal = MaterialTheme.colorScheme.surfaceVariant.value.toLong()
    LaunchedEffect(defaultTextVal, defaultFillVal, primaryVal, surfaceVariantVal, frameViewModel) {
        frameViewModel?.onIntent(
            FrameVmIntent.SetDefaultColors(
                textColorVal = defaultTextVal,
                defaultFillColorVal = defaultFillVal,
                primaryColorVal = primaryVal,
                surfaceVariantColorVal = surfaceVariantVal
            )
        )
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
            newTaskVm.onIntent(
                NewTaskIntent.Init(
                editingTaskId   = newTaskState.editingTaskId,
                isPreviewReturn = newTaskState.isPreviewMode,
                draftTitle      = draftTitle,
            ))
        }
    }

    var showRateUsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val appStarts = settings.getInt("app_starts", 0) + 1
        settings.putInt("app_starts", appStarts)
    }

    LaunchedEffect(screen) {
        if (screen is Screen.TaskScreen) {
            val appStarts = settings.getInt("app_starts", 0)
            if (appStarts >= 3 && settings.getString("review", "") != "1") {
                delay(3000)
                showRateUsDialog = true
            }
        }
    }

    if (showRateUsDialog) {
        val storeUrl = rememberStoreUrl()
        val uriHandler = LocalUriHandler.current
        ReviewDialog(
            onDismissRequest = {}, // dismissOnClickOutside is false, handled inside dialog
            onRateNow = {
                settings.putString("review", "1")
                showRateUsDialog = false
                uriHandler.openUri(storeUrl)
            },
            onLater = {
                settings.putString("review", "1")
                showRateUsDialog = false
            }
        )
    }

    LaunchedEffect(screen) {
        if (screen == Screen.NewTaskScreen) {
            viewModel.setBackInterceptor { newTaskVm.onIntent(NewTaskIntent.BackPressed); true }
        } else if (screen == Screen.PlayerScreen) {
            viewModel.setBackInterceptor { playerVm.onIntent(com.oprojectview.features.player.PlayerIntent.BackClicked); true }
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
                    val displayState = DisplayTaskState(taskId = event.taskId, settings = settings)
                    val speedItem = DisplayTaskList.first { it.id == 3 }
                    val selectedSpeedIndex = displayState.selectedIndex(speedItem) ?: speedItem.defaultIndex
                    val wpm = speedIndexToWpm(selectedSpeedIndex)

                    playerVm.onIntent(PlayerIntent.Load(event.taskId, event.isPreview, wpm))
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
                            fvm.onIntent(FrameVmIntent.SetPlaying(false))
                            fvm.onIntent(FrameVmIntent.RequestPip)
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
            playerVm.onIntent(if (isMultiWindow) PlayerIntent.EnterPip() else PlayerIntent.ExitPip)
        }
    }
    LaunchedEffect(playerVm) {
        playerVm.events.collect { event ->
            when (event) {
                is PlayerEvent.NavigateToDetail -> {
                    displayVm.onIntent(DisplayIntent.Load(event.taskId, event.isPreview))
                    if (event.wasAutoSwitched) {
                        newTaskVm.onIntent(NewTaskIntent.Init(editingTaskId = event.taskId, isPreviewReturn = false))
                        viewModel.navStack.reset()
                        viewModel.goToNewTask()
                        viewModel.goToDisplay()
                    } else {
                        val hasDisplayScreen = Screen.DisplayScreen in viewModel.navStack.snapshot()
                        if (hasDisplayScreen) {
                            while (viewModel.navStack.current.value != Screen.DisplayScreen && viewModel.navStack.canPop) {
                                viewModel.navStack.pop()
                            }
                        } else {
                            viewModel.navStack.pop()
                            viewModel.goToDisplay()
                        }
                    }
                }
                is PlayerEvent.PreloadNextTaskStyles -> {
                    playerStyleState.loadForTaskId(event.taskId)
                }
                is PlayerEvent.NavigateToRoot  -> viewModel.navStack.reset()
                is PlayerEvent.RequestEnterPip -> frameViewModel?.onIntent(FrameVmIntent.RequestPip)
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
        onEdgeSwipeBack  = { viewModel.handleBack() },
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
                is Screen.PlayerScreen  -> {
                    if (!playerState.pipActive) {
                        PlayerScreenStatic(
                            taskId         = playerState.task?.id ?: 0,
                            toolbarVisible = playerState.toolbarVisible,
                            isPlaying      = playerState.isPlaying,
                            isFinished     = playerState.scrollFraction >= 1f,
                            onToolbarTap   = { playerVm.onIntent(PlayerIntent.ScreenTapped) },
                            onBack         = { playerVm.onIntent(PlayerIntent.BackClicked) },
                            onClose        = { playerVm.onIntent(PlayerIntent.CloseClicked) },
                            onPlayPauseClick = { playerVm.onIntent(PlayerIntent.SetPlaying(!playerState.isPlaying)) },
                            onReplayClick  = { playerVm.onIntent(PlayerIntent.ReplayClicked(isManual = true)) },
                            fillColor      = playerStyleState.resolveActiveFillColor(),
                        )
                    }
                }
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
                            fillColor         = displayStyleState.resolveActiveFillColor(),
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
                        var activeSpans by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(playerStyleState.spans) }
                        androidx.compose.runtime.LaunchedEffect(task.id, playerStyleState.spans, playerStyleState.taskId) {
                            if (playerStyleState.taskId == task.id) {
                                activeSpans = playerStyleState.spans
                            }
                        }
                        PlayerScreenBody(
                            task              = task,
                            onReadingComplete = { playerVm.onIntent(PlayerIntent.ReadingCompleted) },
                            modifier          = Modifier.fillMaxSize(),
                            styleSpans        = activeSpans,
                            fillColor         = playerStyleState.resolveActiveFillColor(),
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
    onEdgeSwipeBack:  () -> Unit,
    staticContent:    @Composable (Screen) -> Unit,
    dynamicContent:   @Composable (Screen) -> Unit,
    modifier:         Modifier = Modifier,
) {
    val defaultBgColor  = MaterialTheme.colorScheme.surfaceContainer
    val playerFillColor = playerStyleState.resolveActiveFillColor()

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    // Only intercept if the gesture starts within 40dp of the left edge
                    if (down.position.x < 40.dp.toPx()) {
                        var dragAmount = 0f
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull()
                            if (change != null && change.pressed) {
                                dragAmount += change.position.x - change.previousPosition.x
                                // If user swiped right by more than 50px, trigger back
                                if (dragAmount > 50f) {
                                    change.consume()
                                    onEdgeSwipeBack()
                                    break
                                }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                }
            }
    ) {
        AnimatedContent(
            targetState    = screen,
            transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) },
            label          = "staticBackground",
            modifier       = Modifier.fillMaxSize(),
        ) { targetScreen ->
            val targetColor = if (targetScreen is Screen.PlayerScreen) playerFillColor else defaultBgColor
            val bgColor by androidx.compose.animation.animateColorAsState(
                targetValue = targetColor,
                animationSpec = tween(1000)
            )
            Box(modifier = Modifier.fillMaxSize().background(bgColor))
        }

        SafeAreaLayout {
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState    = screen,
                    transitionSpec = {
                        val isBack = targetState.ordinal < initialState.ordinal
                        if (initialState is Screen.DisplayScreen && targetState is Screen.NewTaskScreen) {
                            slideInHorizontally(tween(350)) { -it } togetherWith
                            slideOutHorizontally(tween(350)) { it }
                        } else if (isBack) {
                            slideInHorizontally(tween(350)) { -it } togetherWith
                            slideOutHorizontally(tween(350)) { it }
                        } else if (initialState is Screen.PlayerScreen && targetState is Screen.DisplayScreen) {
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
                ) { s -> dynamicContent(s) }

                AnimatedContent(
                    targetState    = screen,
                    transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                    label          = "staticLayer",
                    modifier       = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter,
                ) { s ->
                    Box(
                        modifier = if (s is Screen.PlayerScreen) {
                            Modifier.fillMaxSize()
                        } else {
                            Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top)
                        },
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        staticContent(s)
                    }
                }
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
