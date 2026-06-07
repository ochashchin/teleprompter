package com.example.kotlinmultiplatform.features.tasklist

import com.example.kotlinmultiplatform.core.BaseViewModel
import com.example.kotlinmultiplatform.core.UiEvent
import com.example.kotlinmultiplatform.core.UiIntent
import com.example.kotlinmultiplatform.core.UiState

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable state for the task list screen.
 *
 * [allTasks] is the ground-truth list loaded from settings.
 * [visibleTasks] is always derived from [allTasks] + [query] inside the VM —
 * the composable never filters directly.
 *
 * [isSearchActive] drives the top-bar swap between ToolBar and SearchBar.
 */
data class TaskListState(
    val allTasks:      List<TaskListItem> = emptyList(),
    val visibleTasks:  List<TaskListItem> = emptyList(),
    val query:         String             = "",
    val isSearchActive: Boolean           = false,
    val isLoading:     Boolean            = false,
) : UiState

/**
 * Lightweight list-row model — only what TaskScreen needs to render a row.
 *
 * Carries [leadingShapeOrdinal] as Int so this stays in commonMain without
 * importing the Android-only @ExperimentalMaterial3ExpressiveApi [LeadingShapeType].
 * The composable maps ordinal → shape itself.
 */
data class TaskListItem(
    val id:                 Int,
    val title:              String,
    val description:        String,
    val leadingShapeOrdinal: Int,
)

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface TaskListEvent : UiEvent {
    /** Tell AppNavigation to open NewDetail in create mode. */
    data object NavigateToNewTask : TaskListEvent

    /** Tell AppNavigation to open NewDetail in edit mode for this task id. */
    data class NavigateToEditTask(
        val taskId:    Int,
        val title:     String,
        val description: String,
    ) : TaskListEvent
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface TaskListIntent : UiIntent {
    /** Load (or reload) all tasks from the repository. */
    data object Load : TaskListIntent

    /** Notify the VM that a task was added or changed by another screen. */
    data class TasksUpdated(val tasks: List<TaskListItem>) : TaskListIntent

    data class  QueryChanged(val query: String) : TaskListIntent
    data object SearchOpened                    : TaskListIntent
    data object SearchClosed                    : TaskListIntent

    data class TaskClicked(val item: TaskListItem)   : TaskListIntent
    data class TaskDismissed(val item: TaskListItem) : TaskListIntent
    data object NewTaskClicked                       : TaskListIntent
}

// ── Repository interface ──────────────────────────────────────────────────────

/**
 * Persistence contract for the task list.
 *
 * The interface lives in commonMain; the implementation (backed by
 * multiplatform-settings) lives in the same source set and is injected
 * via [TaskListViewModelFactory].
 */
interface TaskListRepository {
    fun loadAll(): List<TaskListItem>
    fun delete(taskId: Int)
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class TaskListViewModel(
    private val repository: TaskListRepository,
) : BaseViewModel<TaskListState, TaskListEvent>(
    initialState = repository.loadAll().let { tasks ->
        TaskListState(allTasks = tasks, visibleTasks = tasks)
    },
) {
    override fun onIntent(intent: UiIntent) {
        when (intent) {
            is TaskListIntent.Load          -> load()
            is TaskListIntent.TasksUpdated  -> onTasksUpdated(intent.tasks)
            is TaskListIntent.QueryChanged  -> onQueryChanged(intent.query)
            is TaskListIntent.SearchOpened  -> updateState { it.copy(isSearchActive = true) }
            is TaskListIntent.SearchClosed  -> closeSearch()
            is TaskListIntent.TaskClicked   -> onTaskClicked(intent.item)
            is TaskListIntent.TaskDismissed -> onTaskDismissed(intent.item)
            is TaskListIntent.NewTaskClicked -> emitEvent(TaskListEvent.NavigateToNewTask)
            else -> Unit
        }
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun load() {
        val tasks = repository.loadAll()
        updateState { state ->
            state.copy(
                allTasks     = tasks,
                visibleTasks = tasks.applyQuery(state.query),
                isLoading    = false,
            )
        }
    }

    /**
     * Called by AppNavigation after NewTask or Display makes a change, so
     * the list stays in sync without a full reload round-trip.
     */
    private fun onTasksUpdated(tasks: List<TaskListItem>) {
        updateState { state ->
            state.copy(
                allTasks     = tasks,
                visibleTasks = tasks.applyQuery(state.query),
                isLoading    = false,
            )
        }
    }

    private fun onQueryChanged(query: String) {
        updateState { state ->
            state.copy(
                query        = query,
                visibleTasks = state.allTasks.applyQuery(query),
            )
        }
    }

    private fun closeSearch() {
        updateState { it.copy(isSearchActive = false, query = "", visibleTasks = it.allTasks) }
    }

    private fun onTaskClicked(item: TaskListItem) {
        emitEvent(
            TaskListEvent.NavigateToEditTask(
                taskId      = item.id,
                title       = item.title,
                description = item.description,
            )
        )
    }

    private fun onTaskDismissed(item: TaskListItem) {
        // Optimistic removal — list updates immediately before disk write
        updateState { state ->
            val updated = state.allTasks.filterNot { it.id == item.id }
            state.copy(
                allTasks     = updated,
                visibleTasks = updated.applyQuery(state.query),
            )
        }
        repository.delete(item.id)
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private fun List<TaskListItem>.applyQuery(query: String) =
        if (query.isBlank()) this
        else filter {
            it.title.contains(query, ignoreCase = true) ||
                    it.description.contains(query, ignoreCase = true)
        }
}
