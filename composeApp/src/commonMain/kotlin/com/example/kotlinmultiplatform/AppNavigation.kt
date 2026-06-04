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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import kotlin.time.Clock

// ── destinations ──────────────────────────────────────────────────────────────

sealed interface Destination {
    data object TaskList  : Destination
    data object NewDetail : Destination
    data class PlayDetail(val task: Task, val isPreview: Boolean = false) : Destination
    /** isPreview = true  → reached from NewTask via "Next" (transient preview)
     *  isPreview = false → reached by tapping a task in the list              */
    data class Detail(val task: Task, val isPreview: Boolean = false) : Destination
}

// ── Settings keys ─────────────────────────────────────────────────────────────
private const val KEY_POPULATED = "tasks_populated"
private const val KEY_NEXT_ID   = "tasks_next_id"
private const val KEY_IDS       = "tasks_ids"

private fun keyTitle(id: Int) = "task_title_$id"
private fun keyDesc (id: Int) = "task_desc_$id"
private fun keyIcon (id: Int) = "task_icon_$id"

// ── Settings read/write helpers ───────────────────────────────────────────────

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

// ── toolbar visibility constants ──────────────────────────────────────────────

private const val TOOLBAR_VISIBLE_MS   = 3_000L

// ── root ──────────────────────────────────────────────────────────────────────

@Composable
fun AppNavigation(modifier: Modifier = Modifier) {
    val settings = LocalSettings.current
    var destination  by remember { mutableStateOf<Destination>(Destination.TaskList) }
    var searchActive by remember { mutableStateOf(false) }
    var query        by remember { mutableStateOf("") }

    var nextId by remember {
        mutableIntStateOf(
            run {
                val populated = settings.getBoolean(KEY_POPULATED, false)
                var id = settings.getInt(KEY_NEXT_ID, 1)
                if (!populated) {
                    val ids = mutableListOf<Int>()
                    mockSeed.forEach { seed ->
                        writeTask(settings, id, seed.title, seed.desc, seed.shape)
                        ids.add(id++)
                    }
                    saveIds(settings, ids)
                    settings[KEY_NEXT_ID]   = id
                    settings[KEY_POPULATED] = true
                }
                id
            }
        )
    }

    val allTasks = remember { mutableStateListOf(*loadAllTasks(settings).toTypedArray()) }

    // ── NewTask screen state ───────────────────────────────────────────────────
    val newTaskState = rememberNewTaskScreenState()

    var editingTaskId by remember { mutableStateOf<Int?>(null) }
    var isPreviewMode by remember { mutableStateOf(false) }

    LaunchedEffect(destination) {
        if (destination == Destination.NewDetail && !isPreviewMode && editingTaskId == null) {
            newTaskState.restore()
        }
    }

    var showSaveDialog by remember { mutableStateOf(false) }

    val scope        = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    // ── Toolbar visibility state (player screen only) ─────────────────────────
    //
    // Owned here so both PlayerScreenStatic (toolbar layer) and PlayerScreenBody
    // (tap layer) share the same state without any cross-composable state holder.
    // Reset to hidden whenever we leave PlayDetail.

    var playerToolbarVisible by remember { mutableStateOf(true) }
    var playerHideDeadlineMs by remember { mutableLongStateOf(Clock.System.now().toEpochMilliseconds() + TOOLBAR_VISIBLE_MS) }

    LaunchedEffect(destination) {
        if (destination is Destination.PlayDetail) {
            playerToolbarVisible = true
            playerHideDeadlineMs = Clock.System.now().toEpochMilliseconds() + TOOLBAR_VISIBLE_MS
        } else {
            playerToolbarVisible = false
            playerHideDeadlineMs = 0L
        }
    }

    // Countdown coroutine: re-launched on every deadline change.
    LaunchedEffect(playerHideDeadlineMs) {
        if (playerHideDeadlineMs <= 0L) return@LaunchedEffect
        val remaining = playerHideDeadlineMs - Clock.System.now().toEpochMilliseconds()
        if (remaining > 0) delay(remaining)
        playerToolbarVisible = false
    }

    // Called by PlayerScreenBody on each tap.
    val onToolbarTap: () -> Unit = {
        val now = Clock.System.now().toEpochMilliseconds()
        playerHideDeadlineMs = if (playerToolbarVisible) {
            minOf(playerHideDeadlineMs + TOOLBAR_VISIBLE_MS, now + 3_000L)
        } else {
            playerToolbarVisible = true
            now + TOOLBAR_VISIBLE_MS
        }
    }

    // ── Back from Detail(isPreview) → return to NewTaskScreen ────────────────
    val onPreviewBack: (title: String, script: String) -> Unit = { title, script ->
        newTaskState.prefill(topic = title, script = script)
        isPreviewMode = true
        destination   = Destination.NewDetail
    }

    // ── Update an existing task in-place by id ────────────────────────────────
    fun updateOrRecreateTask(id: Int, newTitle: String, newDesc: String) {
        val ids      = loadIds(settings)
        val memIndex = allTasks.indexOfFirst { it.id == id }

        if (id in ids) {
            val ordinal = settings.getIntOrNull(keyIcon(id)) ?: 0
            val shape   = LeadingShapeType.entries.getOrNull(ordinal) ?: LeadingShapeType.random()
            settings[keyTitle(id)] = newTitle
            settings[keyDesc(id)]  = newDesc
            val updated = Task(id, newTitle, newDesc, shape)
            if (memIndex >= 0) allTasks[memIndex] = updated else allTasks.add(updated)
        } else {
            deleteTask(settings, id)
            val newId    = nextId++
            val newShape = LeadingShapeType.random()
            val position = if (memIndex >= 0) memIndex else allTasks.size
            writeTask(settings, newId, newTitle, newDesc, newShape)
            val mutableIds = ids.toMutableList()
            mutableIds.add(position.coerceAtMost(mutableIds.size), newId)
            saveIds(settings, mutableIds)
            settings[KEY_NEXT_ID] = nextId
            val newTask = Task(newId, newTitle, newDesc, newShape)
            if (memIndex >= 0) allTasks[memIndex] = newTask else allTasks.add(newTask)
        }
    }

    // ── Handle "Next" on NewTaskScreen ────────────────────────────────────────
    val onNextClick: () -> Unit = {
        val rawTitle = newTaskState.topicText.trim()
        val script   = newTaskState.scriptText.trim()

        if (script.isNotEmpty()) {
            val displayTitle = rawTitle.ifBlank { "Draft" }
            val id = editingTaskId

            if (id != null) {
                val existing      = allTasks.firstOrNull { it.id == id }
                val titleChanged  = existing == null || displayTitle != existing.title
                val scriptChanged = existing == null || script != existing.description
                if (titleChanged || scriptChanged) updateOrRecreateTask(id, displayTitle, script)
            }

            isPreviewMode = false

            val previewTask =
                if (editingTaskId != null) {
                    allTasks.first { it.id == editingTaskId }
                } else {
                    val newId = nextId++
                    val shape = LeadingShapeType.random()
                    writeTask(settings, newId, displayTitle, script, shape)
                    saveIds(settings, loadIds(settings) + newId)
                    settings[KEY_NEXT_ID] = nextId
                    editingTaskId = newId
                    val task = Task(id = newId, title = displayTitle, description = script, leadingShape = shape)
                    allTasks.add(task)
                    task
                }

            destination = Destination.Detail(task = previewTask, isPreview = true)
        }
    }

    fun commitNewTask() {
        val rawTitle = newTaskState.topicText.trim()
        val desc     = newTaskState.scriptText.trim()

        if (rawTitle.isBlank() && desc.isBlank()) {
            editingTaskId = null
            return
        }

        val finalTitle = rawTitle.ifBlank { "Draft" }
        val id         = editingTaskId

        if (id != null) {
            updateOrRecreateTask(id = id, newTitle = finalTitle, newDesc = desc)
        } else {
            val newId = nextId++
            val shape = LeadingShapeType.random()
            writeTask(settings, newId, finalTitle, desc, shape)
            saveIds(settings, loadIds(settings) + newId)
            settings[KEY_NEXT_ID] = nextId
            allTasks.add(Task(newId, finalTitle, desc, shape))
        }

        editingTaskId = null
        isPreviewMode = false
        newTaskState.clear()
    }

    // ── Back from NewTaskScreen ───────────────────────────────────────────────
    val onNewTaskBack: () -> Unit = {
        focusManager.clearFocus(force = true)

        scope.launch {
            delay(500)

            if (isPreviewMode) {
                commitNewTask()
                destination = Destination.TaskList
                return@launch
            }

            if (!newTaskState.isNotEmpty) {
                editingTaskId = null
                newTaskState.clear()
                destination = Destination.TaskList
                return@launch
            }

            val editId = editingTaskId
            if (editId != null) {
                val existing      = allTasks.firstOrNull { it.id == editId }
                val currentTitle  = newTaskState.topicText.trim().ifBlank { "Draft" }
                val currentScript = newTaskState.scriptText.trim()
                val unchanged     = existing != null &&
                        currentTitle == existing.title &&
                        currentScript == existing.description
                if (unchanged) {
                    editingTaskId = null
                    newTaskState.clear()
                    destination = Destination.TaskList
                    return@launch
                }
            }

            showSaveDialog = true
        }
    }

    // ── Remove task ───────────────────────────────────────────────────────────
    fun removeTask(task: Task) {
        deleteTask(settings, task.id)
        saveIds(settings, loadIds(settings) - task.id)
        allTasks.remove(task)
    }

    val visibleTasks by remember {
        derivedStateOf {
            if (query.isBlank()) allTasks.toList()
            else allTasks.filter {
                it.title.contains(query, true) || it.description.contains(query, true)
            }
        }
    }

    ScreenLayout(
        destination   = destination,
        staticContent = { dest ->
            when (dest) {
                is Destination.TaskList  -> TaskScreenStatic(
                    searchActive  = searchActive,
                    query         = query,
                    onQueryChange = { query = it },
                    onClear       = { query = "" },
                    onBack        = { searchActive = false; query = "" },
                    onSearchOpen  = { searchActive = true },
                )
                is Destination.Detail    -> DisplayScreenStatic(
                    onBack = if (dest.isPreview)
                        { -> onPreviewBack(dest.task.title, dest.task.description) }
                    else
                        { -> destination = Destination.TaskList },
                )
                is Destination.NewDetail -> NewTaskScreenStatic(onBack = onNewTaskBack)
                is Destination.PlayDetail -> PlayerScreenStatic(
                    taskId         = dest.task.id,
                    toolbarVisible = playerToolbarVisible,
                    onToolbarTap   = onToolbarTap,
                    onBack         = { destination = Destination.Detail(dest.task, isPreview = dest.isPreview) },
                    onClose        = { destination = Destination.TaskList },
                )
            }
        },
        dynamicContent = { dest ->
            when (dest) {
                is Destination.TaskList  -> TaskScreenBody(
                    visibleTasks = visibleTasks,
                    onDismiss    = { removeTask(it) },
                    onItemClick  = { task ->
                        editingTaskId = task.id
                        newTaskState.prefill(topic = task.title, script = task.description)
                        destination = Destination.NewDetail
                    },
                    onNewClick   = {
                        editingTaskId = null
                        newTaskState.clear()
                        destination   = Destination.NewDetail
                    },
                    modifier     = Modifier.fillMaxSize(),
                )
                is Destination.Detail    -> DisplayScreenBody(
                    task     = dest.task,
                    onPlayClick = {
                        destination = Destination.PlayDetail(dest.task, dest.isPreview)
                    },
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.PlayDetail -> PlayerScreenBody(
                    task     = dest.task,
                    modifier = Modifier.fillMaxSize(),
                )
                is Destination.NewDetail -> NewTaskScreenBody(
                    state           = newTaskState,
                    onBack          = onNewTaskBack,
                    onNextClick     = onNextClick,
                    showSaveDialog  = showSaveDialog,
                    onDismissDialog = { showSaveDialog = false },
                    onSave          = {
                        showSaveDialog = false
                        commitNewTask()
                        destination = Destination.TaskList
                    },
                    onDiscard       = {
                        showSaveDialog = false
                        editingTaskId  = null
                        isPreviewMode  = false
                        newTaskState.clear()
                        destination = Destination.TaskList
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
    destination: Destination,
    staticContent: @Composable (Destination) -> Unit,
    dynamicContent: @Composable (Destination) -> Unit,
    modifier: Modifier = Modifier,
) {
    SafeAreaLayout {
        Box(modifier = modifier.fillMaxSize()) {
            AnimatedContent(
                targetState = destination,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(300)) },
                label = "staticLayer",
                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
            ) { dest -> staticContent(dest) }

            AnimatedContent(
                targetState = destination,
                transitionSpec = {
                    when {
                        // Back from Detail(isPreview) to NewDetail: slide right-to-left (reverse)
                        initialState is Destination.Detail &&
                                (initialState as Destination.Detail).isPreview &&
                                targetState is Destination.NewDetail ->
                            slideInHorizontally(tween(350)) { -it } togetherWith
                                    slideOutHorizontally(tween(350)) { it }
                        // Back to TaskList: slide right-to-left
                        targetState is Destination.TaskList ->
                            slideInHorizontally(tween(350)) { -it } togetherWith
                                    slideOutHorizontally(tween(350)) { it }

                        // Back from PlayDetail
                        initialState is Destination.PlayDetail && targetState is Destination.Detail ->
                            slideInHorizontally(tween(350)) { -it } togetherWith
                                    slideOutHorizontally(tween(350)) { it } using
                                    SizeTransform(clip = true)

                        // Forward: slide left-to-right
                        else ->
                            slideInHorizontally(tween(350)) { it } togetherWith
                                    slideOutHorizontally(tween(350)) { -it } using
                                    SizeTransform(clip = true)
                    }
                },
                label = "dynamicLayer",
                modifier = Modifier
                    .fillMaxSize(),
            ) { dest -> dynamicContent(dest) }
        }
    }
}