package com.example.kotlinmultiplatform.features.display

import com.example.kotlinmultiplatform.core.BaseViewModel
import com.example.kotlinmultiplatform.core.UiEvent
import com.example.kotlinmultiplatform.core.UiIntent
import com.example.kotlinmultiplatform.core.UiState

// ── Domain model ──────────────────────────────────────────────────────────────

data class DisplayTask(
    val id:           Int,
    val title:        String,
    val description:  String,
    val shapeOrdinal: Int,
)

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable state for the Detail / Display screen.
 *
 * [task] is null while loading — the composable shows nothing until it arrives.
 * [isPreview] drives back-navigation: preview back returns to NewDetail,
 * non-preview back pops to TaskList.
 *
 * Note: display option selections (text size, speed, animation…) are NOT
 * held here. They are persisted directly by [DisplayTaskState], which is a
 * composable-owned settings bridge — exactly the same pattern as
 * [NewTaskScreenState] for text fields. The VM does not need to own them
 * because they are both read and written inside [DisplayScreenBody] with no
 * business logic in between.
 */
data class DisplayState(
    val task:      DisplayTask? = null,
    val isPreview: Boolean      = false,
    val isLoading: Boolean      = true,
) : UiState

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface DisplayEvent : UiEvent {
    /** Navigate to the Player screen for this task. */
    data class NavigateToPlay(
        val taskId:    Int,
        val isPreview: Boolean,
    ) : DisplayEvent

    /** Navigate back (pop or return to NewDetail depending on [isPreview]). */
    data class NavigateBack(val isPreview: Boolean) : DisplayEvent
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface DisplayIntent : UiIntent {
    /**
     * Load task data for [taskId].
     * [isPreview] is true when reached via "Next" from NewDetail.
     */
    data class Load(val taskId: Int, val isPreview: Boolean) : DisplayIntent

    data object PlayClicked  : DisplayIntent
    data object BackClicked  : DisplayIntent
}

// ── Repository interface ──────────────────────────────────────────────────────

interface DisplayRepository {
    fun loadTask(taskId: Int): DisplayTask?
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class DisplayViewModel(
    private val repository: DisplayRepository,
) : BaseViewModel<DisplayState, DisplayEvent>(
    initialState = DisplayState(),
) {
    override fun onIntent(intent: UiIntent) {
        when (intent) {
            is DisplayIntent.Load        -> load(intent.taskId, intent.isPreview)
            is DisplayIntent.PlayClicked -> onPlay()
            is DisplayIntent.BackClicked -> onBack()
            else                         -> Unit
        }
    }

    private fun load(taskId: Int, isPreview: Boolean) {
        updateState { it.copy(isLoading = true) }
        val task = repository.loadTask(taskId)
        updateState {
            it.copy(
                task      = task,
                isPreview = isPreview,
                isLoading = false,
            )
        }
    }

    private fun onPlay() {
        val state = currentState
        val taskId = state.task?.id ?: return
        emitEvent(DisplayEvent.NavigateToPlay(taskId = taskId, isPreview = state.isPreview))
    }

    private fun onBack() {
        emitEvent(DisplayEvent.NavigateBack(isPreview = currentState.isPreview))
    }
}
