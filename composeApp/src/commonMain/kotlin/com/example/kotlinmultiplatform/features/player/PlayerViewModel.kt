package com.example.kotlinmultiplatform.features.player

import com.example.kotlinmultiplatform.core.BaseViewModel
import com.example.kotlinmultiplatform.core.UiEvent
import com.example.kotlinmultiplatform.core.UiIntent
import com.example.kotlinmultiplatform.core.UiState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Domain model ──────────────────────────────────────────────────────────────

data class PlayerTask(
    val id:           Int,
    val title:        String,
    val description:  String,
    val shapeOrdinal: Int,
)

// ── State ─────────────────────────────────────────────────────────────────────

private const val TOOLBAR_VISIBLE_MS = 3_000L

/**
 * Immutable state for the Player / teleprompter screen.
 *
 * [toolbarVisible] is managed by a cancellable timer inside the VM —
 * the composable no longer needs a LaunchedEffect or mutableLongStateOf.
 *
 * [pipActive] is a reserved flag for the future PiP rendering system;
 * the platform layer sets it via [PlayerIntent.EnterPip].
 */
data class PlayerState(
    val task:           PlayerTask? = null,
    val isPreview:      Boolean     = false,
    val isLoading:      Boolean     = false,
    val toolbarVisible: Boolean     = true,
    val pipActive:      Boolean     = false,
) : UiState

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface PlayerEvent : UiEvent {
    /** Navigate back to Detail for this task. */
    data class NavigateToDetail(val taskId: Int, val isPreview: Boolean) : PlayerEvent

    /** Pop the entire back-stack to TaskList (Close button). */
    data object NavigateToRoot : PlayerEvent

    // Reserved for PiP platform integration
    data object RequestEnterPip : PlayerEvent
    data object RequestExitPip  : PlayerEvent
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface PlayerIntent : UiIntent {
    data class Load(val taskId: Int, val isPreview: Boolean) : PlayerIntent
    data object ScreenTapped     : PlayerIntent
    data object ReadingCompleted : PlayerIntent
    data object BackClicked      : PlayerIntent
    data object CloseClicked     : PlayerIntent

    // PiP — sent from platform-specific lifecycle callbacks
    data object EnterPip : PlayerIntent
    data object ExitPip  : PlayerIntent
}

// ── Repository interface ──────────────────────────────────────────────────────

interface PlayerRepository {
    fun loadTask(taskId: Int): PlayerTask?
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class PlayerViewModel(
    private val repository: PlayerRepository,
) : BaseViewModel<PlayerState, PlayerEvent>(
    initialState = PlayerState(),
) {
    private var toolbarHideJob: Job? = null

    override fun onIntent(intent: UiIntent) {
        when (intent) {
            is PlayerIntent.Load          -> load(intent.taskId, intent.isPreview)
            is PlayerIntent.ScreenTapped  -> onTap()
            is PlayerIntent.ReadingCompleted -> onReadingComplete()
            is PlayerIntent.BackClicked   -> onBack()
            is PlayerIntent.CloseClicked  -> emitEvent(PlayerEvent.NavigateToRoot)
            is PlayerIntent.EnterPip      -> updateState { it.copy(pipActive = true) }
            is PlayerIntent.ExitPip       -> updateState { it.copy(pipActive = false) }
            else                          -> Unit
        }
    }

    override fun clear() {
        toolbarHideJob?.cancel()
        super.clear()
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun load(taskId: Int, isPreview: Boolean) {
        val task = repository.loadTask(taskId)
        updateState {
            it.copy(
                task           = task,
                isPreview      = isPreview,
                isLoading      = false,
                toolbarVisible = true,
            )
        }
        scheduleToolbarHide()
    }

    /**
     * Single tap: if toolbar is visible extend the timer; if hidden show it
     * and start a fresh timer. Matches the original onToolbarTap behaviour
     * but lives entirely inside the VM — no mutableLongStateOf in the UI.
     */
    private fun onTap() {
        toolbarHideJob?.cancel()
        if (!currentState.toolbarVisible) {
            updateState { it.copy(toolbarVisible = true) }
        }
        scheduleToolbarHide()
    }

    private fun scheduleToolbarHide() {
        toolbarHideJob?.cancel()
        toolbarHideJob = viewModelScope.launch {
            delay(TOOLBAR_VISIBLE_MS)
            updateState { it.copy(toolbarVisible = false) }
        }
    }

    private fun onReadingComplete() {
        toolbarHideJob?.cancel()
        updateState { it.copy(toolbarVisible = true) }
    }

    private fun onBack() {
        val state = currentState
        val taskId = state.task?.id ?: return
        emitEvent(PlayerEvent.NavigateToDetail(taskId = taskId, isPreview = state.isPreview))
    }
}
