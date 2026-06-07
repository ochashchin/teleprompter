package com.example.kotlinmultiplatform

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
    val topicFieldState   = rememberTextFieldState()
    val scriptFieldState  = rememberTextFieldState()
    // Style state lives here so it survives screen transitions (same as field states)
    // and is backed by Settings for persistence across app restarts.
    val scriptStyleState  = rememberScriptTextStyleState()

    // Display/Player style states are lifted here (above AnimatedContent) so they are
    // never re-created on recomposition.  loadForTaskId() is called via LaunchedEffect
    // when the active task id changes, ensuring we always read from Settings AFTER
    // migrateToTaskId() has written the spans under the real key.
    val displayStyleState = rememberScriptTextStyleState()
    val playerStyleState  = rememberScriptTextStyleState()

    // Reload display style whenever the displayed task changes.
    LaunchedEffect(displayState.task?.id) {
        val id = displayState.task?.id ?: return@LaunchedEffect
        displayStyleState.loadForTaskId(id)
    }

    // Reload player style whenever the played task changes.
    LaunchedEffect(playerState.task?.id) {
        val id = playerState.task?.id ?: return@LaunchedEffect
        playerStyleState.loadForTaskId(id)
    }

    // ── Mirror style unsaved-changes into the VM so back-press shows dialog ──

    LaunchedEffect(scriptStyleState.hasUnsavedChanges) {
        if (scriptStyleState.hasUnsavedChanges) {
            newTaskVm.onIntent(NewTaskIntent.StyleChanged)
        }
    }

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

    // ── Back interceptor — routes gesture/system back through the active screen ──
    //
    // NewTaskScreen needs to show the save-changes dialog before navigation,
    // regardless of whether the back originates from the toolbar button, the
    // Android system back, or the iOS left-edge swipe gesture.
    //
    // The interceptor is registered when NewTaskScreen becomes active and
    // cleared when any other screen becomes active, so it never fires on the
    // wrong screen.

    LaunchedEffect(screen) {
        if (screen == Screen.NewTaskScreen) {
            viewModel.setBackInterceptor {
                newTaskVm.onIntent(NewTaskIntent.BackPressed)
                true // consumed — VM drives dialog and eventual navigation
            }
        } else {
            viewModel.setBackInterceptor(null)
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
                    // Load style for the task being edited (editingTaskId is already in VM state)
                    val editId = newTaskState.editingTaskId
                    if (editId != null) {
                        scriptStyleState.loadForTaskId(editId)
                    }
                }
                is NewTaskEvent.ClearFields -> {
                    topicFieldState.edit  { replace(0, length, "") }
                    scriptFieldState.edit { replace(0, length, "") }
                    scriptStyleState.clear()
                    newTaskVm.onIntent(NewTaskIntent.StyleSaved)
                }
                is NewTaskEvent.DismissKeyboard -> {
                    focusManager.clearFocus(force = true)
                    delay(500)
                }
                is NewTaskEvent.NavigateToDetail -> {
                    // Migrate style from temp key to the real taskId, then mark saved
                    scriptStyleState.migrateToTaskId(event.taskId)
                    scriptStyleState.markSaved()
                    newTaskVm.onIntent(NewTaskIntent.StyleSaved)
                    // Eagerly reload displayStyleState so spans are ready before
                    // DisplayScreen renders — LaunchedEffect on task id won't refire
                    // if the same task is previewed a second time (id unchanged).
                    displayStyleState.loadForTaskId(event.taskId)
                    taskListVm.onIntent(TaskListIntent.Load)
                    displayVm.onIntent(DisplayIntent.Load(event.taskId, event.isPreview))
                    viewModel.goToDisplay()
                }
                is NewTaskEvent.NavigateBackAfterSave -> {
                    // Migrate spans to the real task key BEFORE ClearFields arrives.
                    // ClearFields is emitted immediately after this event by the VM;
                    // it will call scriptStyleState.clear() which resets everything.
                    val id = event.savedTaskId
                    if (id != null) scriptStyleState.migrateToTaskId(id)
                    newTaskVm.onIntent(NewTaskIntent.StyleSaved)
                    taskListVm.onIntent(TaskListIntent.Load)
                    viewModel.navStack.reset()
                }
                is NewTaskEvent.NavigateBack -> {
                    // Discard path — just clear style state without migrating.
                    scriptStyleState.clear()
                    newTaskVm.onIntent(NewTaskIntent.StyleSaved)
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
                    // Eagerly reload player style here too — LaunchedEffect on task id
                    // won't refire if the same task is replayed (id unchanged).
                    playerStyleState.loadForTaskId(event.taskId)
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
        dynamicContent   = { s ->
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
                        // displayStyleState is loaded above via LaunchedEffect(displayState.task?.id)
                        // so spans are always fresh after migrateToTaskId() writes to Settings.
                        DisplayScreenBody(
                            task              = task,
                            onPlayClick       = { displayVm.onIntent(DisplayIntent.PlayClicked) },
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
                        // playerStyleState is loaded above via LaunchedEffect(playerState.task?.id)
                        PlayerScreenBody(
                            task              = task,
                            onReadingComplete = { playerVm.onIntent(PlayerIntent.ReadingCompleted) },
                            modifier          = Modifier.fillMaxSize(),
                            styleSpans        = playerStyleState.spans,
                            isFillColorActive = playerStyleState.isFillColorActive,
                            fillColor         = playerStyleState.activeFillColor,
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
    screen:           Screen,
    prevScreen:       Screen,
    playerStyleState: ScriptTextStyleState,
    staticContent:    @Composable (Screen) -> Unit,
    dynamicContent:   @Composable (Screen) -> Unit,
    modifier:         Modifier = Modifier,
) {
    val defaultBgColor   = MaterialTheme.colorScheme.surfaceContainerLow
    val playerFillColor  = playerStyleState.activeFillColor

    Box(modifier = modifier.fillMaxSize()) {

        AnimatedContent(
            targetState    = screen,
            transitionSpec = { fadeIn(tween(1000)) togetherWith fadeOut(tween(1000)) },
            label          = "staticBackground",
            modifier       = Modifier.fillMaxSize(),
        ) { targetScreen ->
            val bgColor = if (targetScreen is Screen.PlayerScreen && playerFillColor != null) {
                playerFillColor
            } else {
                defaultBgColor
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor)
            )
        }

        SafeAreaLayout {
            Box(modifier = Modifier.fillMaxSize()) {

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
}

// ── Ordinal helper ────────────────────────────────────────────────────────────

private val Screen.ordinal: Int get() = when (this) {
    is Screen.TaskScreen    -> 0
    is Screen.NewTaskScreen -> 1
    is Screen.DisplayScreen -> 2
    is Screen.PlayerScreen  -> 3
}