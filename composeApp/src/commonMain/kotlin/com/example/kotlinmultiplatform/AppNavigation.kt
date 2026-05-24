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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── destinations ──────────────────────────────────────────────────────────────

sealed interface Destination {
    data object TaskList  : Destination
    data class  Detail(val task: Task) : Destination
    data object NewDetail : Destination
}

// ── Settings keys ─────────────────────────────────────────────────────────────
//
// Layout in Settings:
//   tasks_populated          Boolean  — true after first-ever launch
//   tasks_next_id            Int      — monotonic id counter
//   tasks_ids                String   — comma-separated ordered id list, e.g. "1,2,3,4"
//   task_title_{id}          String
//   task_desc_{id}           String
//   task_icon_{id}           Int      — LeadingShapeType ordinal
//
// Swipe-dismiss removes all four task_{id} keys + removes id from tasks_ids.
// New user task writes all four keys + appends id to tasks_ids.


private const val KEY_POPULATED = "tasks_populated"
private const val KEY_NEXT_ID   = "tasks_next_id"
private const val KEY_IDS       = "tasks_ids"

private fun keyTitle(id: Int) = "task_title_$id"
private fun keyDesc (id: Int) = "task_desc_$id"
private fun keyIcon (id: Int) = "task_icon_$id"

// ── Settings read/write helpers ───────────────────────────────────────────────

/** Ordered list of currently stored task ids. */
private fun loadIds(settings: Settings): List<Int> {
    val raw = settings.getStringOrNull(KEY_IDS) ?: return emptyList()
    return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
}

private fun saveIds(settings: Settings, ids: List<Int>) {
    settings[KEY_IDS] = ids.joinToString(",")
}

private fun writeTask(settings: Settings, id: Int, title: String, desc: String, shape: LeadingShapeType) {
    settings[keyTitle(id)] = title
    settings[keyDesc(id)]  = desc
    settings[keyIcon(id)]  = shape.ordinal
}

private fun deleteTask(settings: Settings, id: Int) {
    settings.remove(keyTitle(id))
    settings.remove(keyDesc(id))
    settings.remove(keyIcon(id))
}

private fun readTask(settings: Settings, id: Int): Task? {
    val title   = settings.getStringOrNull(keyTitle(id)) ?: return null
    val desc    = settings.getStringOrNull(keyDesc(id))  ?: ""
    val ordinal = settings.getIntOrNull(keyIcon(id))     ?: return null
    val shape   = LeadingShapeType.entries.getOrNull(ordinal) ?: return null
    return Task(id, title, desc, shape)
}

/** Load all tasks from Settings in their stored order. */
private fun loadAllTasks(settings: Settings): List<Task> =
    loadIds(settings).mapNotNull { readTask(settings, it) }

// ── mock seed ─────────────────────────────────────────────────────────────────

private data class MockTask(val title: String, val desc: String, val shape: LeadingShapeType)

private val mockSeed = listOf(
    MockTask("Buy groceries", "Milk, Eggs, Bread, Coffee",              LeadingShapeType.HEART),
    MockTask("KMP Project",   "Sync repository and update dependencies", LeadingShapeType.COOKIE_6),
    MockTask("Gym session",   "Leg day workout at 6 PM",                 LeadingShapeType.SUNNY),
    MockTask("Read book",     "Read 10 pages of Atomic Habits",          LeadingShapeType.DIAMOND),
)

// ── root ──────────────────────────────────────────────────────────────────────

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val settings = LocalSettings.current
    var destination  by remember { mutableStateOf<Destination>(Destination.TaskList) }
    var searchActive by remember { mutableStateOf(false) }
    var query        by remember { mutableStateOf("") }

    // ── Synchronous startup: runs during composition, zero-frame delay ─────────
    // remember { } executes on the main thread before the first frame is drawn,
    // so the list is populated immediately — no empty-flash or loading state.
    // Settings reads on the main thread are fast (shared memory on all platforms).
    var nextId by remember {
        mutableIntStateOf(
            run {
                val populated = settings.getBoolean(KEY_POPULATED, false)
                var id = settings.getInt(KEY_NEXT_ID, 1)
                if (!populated) {
                    // First-ever launch: write mock seed to Settings once.
                    val ids = mutableListOf<Int>()
                    mockSeed.forEach { seed ->
                        writeTask(settings, id, seed.title, seed.desc, seed.shape)
                        ids.add(id++)
                    }
                    saveIds(settings, ids)
                    settings[KEY_NEXT_ID]   = id
                    settings[KEY_POPULATED] = true
                }
                id  // initial value for nextId
            }
        )
    }

    // Loaded synchronously in the same remember block — list is ready on frame 1.
    val allTasks = remember { mutableStateListOf(*loadAllTasks(settings).toTypedArray()) }


    // ── NewTask screen state ───────────────────────────────────────────────────
    val newTaskState = rememberNewTaskScreenState()

    LaunchedEffect(destination) {
        if (destination == Destination.NewDetail) newTaskState.restore()
    }

    var showSaveDialog by remember { mutableStateOf(false) }

    val scope        = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val onNewTaskBack: () -> Unit = {
        focusManager.clearFocus(force = true)
        scope.launch {
            delay(500)
            if (newTaskState.isDirty) {
                showSaveDialog = true
            } else {
                newTaskState.clear()
                destination = Destination.TaskList
            }
        }
    }

    // ── Commit new task: persist + add to in-memory list ─────────────────────
    fun commitNewTask() {
        val rawTitle = newTaskState.topicText.trim()
        val desc     = newTaskState.scriptText.trim()
        // Save if either field has content; topic defaults to "Untitled" when blank.
        if (rawTitle.isNotEmpty() || desc.isNotEmpty()) {
            val title = rawTitle.ifEmpty { "Draft" }
            val id    = nextId++
            val shape = LeadingShapeType.random()
            writeTask(settings, id, title, desc, shape)           // persist
            saveIds(settings, loadIds(settings) + id)           // append to ordered id list
            settings[KEY_NEXT_ID] = nextId              // persist next id
            allTasks.add(Task(id, title, desc, shape))  // update in-memory mirror
        }
        newTaskState.clear()
    }

    // ── Remove task: wipe from Settings + remove from in-memory list ──────────
    fun removeTask(task: Task) {
        deleteTask(settings, task.id)                             // remove all task_{id} keys
        saveIds(settings, loadIds(settings) - task.id)          // remove from ordered id list
        allTasks.remove(task)                           // update in-memory mirror
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
        destination   = destination,
        staticContent = { dest ->
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
                is Destination.NewDetail -> NewTaskScreenStatic(
                    onBack = onNewTaskBack,
                )
            }
        },
        dynamicContent = { dest ->
            when (dest) {
                is Destination.TaskList -> TaskScreenBody(
                    visibleTasks = visibleTasks,
                    onDismiss    = { removeTask(it) },
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
                    onDismissDialog = { showSaveDialog = false },
                    onSave          = {
                        showSaveDialog = false
                        commitNewTask()
                        destination    = Destination.TaskList
                    },
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
    destination   : Destination,
    staticContent : @Composable (Destination) -> Unit,
    dynamicContent: @Composable (Destination) -> Unit,
    modifier      : Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = destination,
            transitionSpec = {
                when (targetState) {
                    is Destination.Detail    ->
                        slideInHorizontally(tween(350)) { it }  togetherWith
                                slideOutHorizontally(tween(350)) { -it }
                    is Destination.TaskList  ->
                        slideInHorizontally(tween(350)) { -it } togetherWith
                                slideOutHorizontally(tween(350)) { it }
                    is Destination.NewDetail ->
                        slideInHorizontally(tween(350)) { it }  togetherWith
                                slideOutHorizontally(tween(350)) { -it }
                }
            },
            label    = "dynamicLayer",
            modifier = Modifier.fillMaxSize().padding(top = 64.dp),
        ) { dest -> dynamicContent(dest) }

        AnimatedContent(
            targetState = destination,
            transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
            label    = "staticLayer",
            modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        ) { dest -> staticContent(dest) }
    }
}
