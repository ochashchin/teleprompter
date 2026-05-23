package com.example.kotlinmultiplatform

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── destinations ──────────────────────────────────────────────────────────────

sealed interface Destination {
    data object TaskList  : Destination
    data class  Detail(val task: Task) : Destination
    data object NewDetail : Destination
}

// ── root ──────────────────────────────────────────────────────────────────────

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    var destination  by remember { mutableStateOf<Destination>(Destination.TaskList) }
    var searchActive by remember { mutableStateOf(false) }
    var query        by remember { mutableStateOf("") }

    // ── NewTask screen state — hoisted here so Static toolbar and Body fields
    //    share the same TextFieldState instances across recompositions.
    //    Recreated only when the composable enters composition for the first time.
    val newTaskState = rememberNewTaskScreenState()

    // Restore draft when navigating TO NewDetail
    LaunchedEffect(destination) {
        if (destination == Destination.NewDetail) newTaskState.restore()
    }

    // ── Dialog visibility — lives in AppNavigation so the toolbar back button
    //    (in staticContent) can trigger it even though the dialog renders inside
    //    dynamicContent (NewTaskScreenBody).
    var showSaveDialog by remember { mutableStateOf(false) }

    // ── Helper: navigate back from NewDetail, guarded by dirty state ──────────
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val onNewTaskBack: () -> Unit = {
        focusManager.clearFocus(force = true)

        scope.launch {
            delay(500)
            if (newTaskState.isDirty) {
                showSaveDialog = true
            } else {

                destination = Destination.TaskList
            }
        }
    }

    val allTasks = remember {
        mutableStateListOf(
            Task(1, "Buy groceries",  "Milk, Eggs, Bread, Coffee",               LeadingShapeType.random()),
            Task(2, "KMP Project",    "Sync repository and update dependencies",  LeadingShapeType.random()),
            Task(3, "Gym session",    "Leg day workout at 6 PM",                  LeadingShapeType.random()),
            Task(4, "Read book",      "Read 10 pages of Atomic Habits",           LeadingShapeType.random()),
        )
    }

    val visibleTasks by remember {
        derivedStateOf {
            if (query.isBlank()) allTasks.toList()
            else allTasks.filter {
                it.title.contains(query, true) ||
                        it.description.contains(query, true)
            }
        }
    }

    ScreenLayout(
        destination    = destination,
        staticContent  = { dest ->
            when (dest) {
                is Destination.TaskList -> TaskScreenStatic(
                    searchActive  = searchActive,
                    query         = query,
                    onQueryChange = { query = it },
                    onClear       = { query = "" },
                    onBack        = { searchActive = false; query = "" },
                    onSearchOpen  = { searchActive = true },
                )

                is Destination.Detail -> DisplayScreenStatic(
                    task   = dest.task,
                    onBack = { destination = Destination.TaskList },
                )

                // Toolbar back is guarded — shows dialog if fields are dirty
                is Destination.NewDetail -> NewTaskScreenStatic(
                    onBack = onNewTaskBack,
                )
            }
        },
        dynamicContent = { dest ->
            when (dest) {
                is Destination.TaskList -> TaskScreenBody(
                    visibleTasks = visibleTasks,
                    onDismiss    = { allTasks.remove(it) },
                    onItemClick  = { destination = Destination.Detail(it) },
                    onNewClick   = { destination = Destination.NewDetail },
                    modifier     = Modifier.fillMaxSize(),
                )

                is Destination.Detail -> DisplayScreenBody(
                    task     = dest.task,
                    modifier = Modifier.fillMaxSize(),
                )

                is Destination.NewDetail -> NewTaskScreenBody(
                    state           = newTaskState,
                    onBack          = onNewTaskBack,
                    showSaveDialog  = showSaveDialog,
                    // Scrim / back press inside the dialog: just hide the dialog,
                    // stay on NewDetail so the user can keep editing.
                    onDismissDialog = { showSaveDialog = false },
                    // Save: hide dialog, navigate to TaskList
                    onSave          = {
                        showSaveDialog = false
                        newTaskState.clear()
                        destination    = Destination.TaskList
                    },
                    // Discard: hide dialog, navigate to TaskList
                    onDiscard       = {
                        showSaveDialog = false
                        newTaskState.clear()
                        destination    = Destination.TaskList
                    },
                    modifier        = Modifier.fillMaxSize(),
                )
            }
        },
        modifier = modifier,
    )
}

// ── layout ────────────────────────────────────────────────────────────────────

@Composable
private fun ScreenLayout(
    destination    : Destination,
    staticContent  : @Composable (Destination) -> Unit,
    dynamicContent : @Composable (Destination) -> Unit,
    modifier       : Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {

        // ── DYNAMIC — slides left/right ───────────────────────────────────
        AnimatedContent(
            targetState  = destination,
            transitionSpec = {
                when (targetState) {
                    is Destination.Detail ->
                        slideInHorizontally(tween(350)) { it }  togetherWith
                                slideOutHorizontally(tween(350)) { -it }

                    is Destination.TaskList ->
                        slideInHorizontally(tween(350)) { -it } togetherWith
                                slideOutHorizontally(tween(350)) { it }

                    is Destination.NewDetail ->
                        slideInHorizontally(tween(350)) { it }  togetherWith
                                slideOutHorizontally(tween(350)) { -it }
                }
            },
            label    = "dynamicLayer",
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 64.dp),
        ) { dest -> dynamicContent(dest) }

        // ── STATIC — fades, always on top ────────────────────────────────
        AnimatedContent(
            targetState  = destination,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label    = "staticLayer",
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
        ) { dest -> staticContent(dest) }
    }
}
