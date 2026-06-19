package com.oprojectview.features.display

import com.oprojectview.core.BaseViewModel
import com.oprojectview.core.UiEvent
import com.oprojectview.core.UiIntent
import com.oprojectview.core.UiState

// ── Domain model ──────────────────────────────────────────────────────────────

data class DisplayTask(
    val id:           Int,
    val title:        String,
    val description:  String,
    val shapeOrdinal: Int,
)

// ── State ─────────────────────────────────────────────────────────────────────

data class DisplayState(
    val task:      DisplayTask? = null,
    val isPreview: Boolean      = false,
    val isLoading: Boolean      = false,
) : com.oprojectview.core.UiState

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface DisplayEvent : com.oprojectview.core.UiEvent {
    /**
     * Navigate to the Player screen for this task.
     *
     * [overlayEnabled] is true when the user has the Overlay setting turned on.
     * AppRoot reads this flag and arms the PiP pipeline (FrameViewModel) before
     * pushing the PlayerScreen — keeping all PiP logic out of the composable layer.
     */
    data class NavigateToPlay(
        val taskId:         Int,
        val isPreview:      Boolean,
        val overlayEnabled: Boolean = false,
    ) : DisplayEvent

    data class NavigateBack(val isPreview: Boolean) : DisplayEvent
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface DisplayIntent : com.oprojectview.core.UiIntent {
    data class Load(val taskId: Int, val isPreview: Boolean) : DisplayIntent

    /**
     * Play button pressed.
     *
     * [overlayEnabled] is resolved inside [DisplayScreenBody] from the persisted
     * [DisplayTaskState] (item id=8) and forwarded here so the VM can carry it
     * through to [DisplayEvent.NavigateToPlay].  The composable itself does not
     * act on it — it just reads the setting it already holds and passes it up.
     */
    data class PlayClicked(val overlayEnabled: Boolean) : DisplayIntent

    data object BackClicked : DisplayIntent
}

// ── Repository ────────────────────────────────────────────────────────────────

interface DisplayRepository {
    fun loadTask(taskId: Int): DisplayTask?
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class DisplayViewModel(
    private val repository: DisplayRepository,
) : com.oprojectview.core.BaseViewModel<DisplayState, DisplayEvent>(
    initialState = DisplayState(),
) {
    override fun onIntent(intent: com.oprojectview.core.UiIntent) {
        when (intent) {
            is DisplayIntent.Load        -> load(intent.taskId, intent.isPreview)
            is DisplayIntent.PlayClicked -> onPlay(intent.overlayEnabled)
            is DisplayIntent.BackClicked -> onBack()
            else                         -> Unit
        }
    }

    private fun load(taskId: Int, isPreview: Boolean) {
        val task = repository.loadTask(taskId)
        updateState { it.copy(task = task, isPreview = isPreview, isLoading = false) }
    }

    private fun onPlay(overlayEnabled: Boolean) {
        val state  = currentState
        val taskId = state.task?.id ?: return
        emitEvent(
            DisplayEvent.NavigateToPlay(
                taskId         = taskId,
                isPreview      = state.isPreview,
                overlayEnabled = overlayEnabled,
            )
        )
    }

    private fun onBack() {
        emitEvent(DisplayEvent.NavigateBack(isPreview = currentState.isPreview))
    }
}
