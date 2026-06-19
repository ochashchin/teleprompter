package com.oprojectview.features.player


import com.oprojectview.calculatePageDurationMs
import com.oprojectview.core.BaseViewModel
import com.oprojectview.core.UiEvent
import com.oprojectview.core.UiIntent
import com.oprojectview.core.UiState
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
    val isPlaying:      Boolean     = false,
    val pipActive:      Boolean     = false,
    val isNativePip:    Boolean     = false,
    val toolbarVisible: Boolean     = true,
    /**
     * Set to true the first time the 4-second pre-roll countdown finishes.
     * Persists across PiP in/out so the countdown never replays mid-session.
     */
    val countdownDone:  Boolean     = false,
    val countdownStartUs: Long      = 0L,
    val pausedCountdownElapsedUs: Long = 0L,
    /**
     * Normalised scroll position in [0f, 1f].
     * 0f = animation not yet started, 1f = fully complete.
     * Composables use this to resume the offsetAnim from the correct position
     * after a PiP interruption.
     */
    val scrollFraction: Float       = 0f,
    val playbackStartUs: Long       = 0L,
    val pausedElapsedUs: Long       = 0L,
    val totalDurationMs: Long       = 0L,
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
    data class Load(val taskId: Int, val isPreview: Boolean, val wpm: Int) : PlayerIntent
    data object ScreenTapped     : PlayerIntent
    data object ReadingCompleted : PlayerIntent
    data object BackClicked      : PlayerIntent
    data object CloseClicked     : PlayerIntent

    // PiP — sent from platform-specific lifecycle callbacks
    data class EnterPip(val isNativePip: Boolean = false) : PlayerIntent
    data object ExitPip : PlayerIntent

    // Playback state persistence — sent by composables so the VM can survive
    // PiP in/out and pass the position back on re-entry.
    data object CountdownDone : PlayerIntent
    data object ReplayClicked : PlayerIntent
    data class  ScrollProgress(val fraction: Float) : PlayerIntent
    data class  SetPlaying(val playing: Boolean)    : PlayerIntent
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
    private var resetJob: Job? = null

    override fun onIntent(intent: UiIntent) {
        when (intent) {
            is PlayerIntent.Load          -> load(intent.taskId, intent.isPreview, intent.wpm)
            is PlayerIntent.ScreenTapped  -> onTap()
            is PlayerIntent.ReadingCompleted -> onReadingComplete()
            is PlayerIntent.BackClicked   -> onBack()
            is PlayerIntent.CloseClicked  -> { resetPlaybackState(); emitEvent(PlayerEvent.NavigateToRoot) }
            is PlayerIntent.EnterPip      -> updateState { it.copy(pipActive = true, isNativePip = intent.isNativePip) }
            is PlayerIntent.ExitPip       -> updateState { it.copy(pipActive = false, isNativePip = false) }
            is PlayerIntent.CountdownDone -> onCountdownDone()
            is PlayerIntent.ReplayClicked -> onReplayClicked()
            is PlayerIntent.ScrollProgress -> onScrollProgress(intent.fraction)
            is PlayerIntent.SetPlaying    -> onSetPlaying(intent.playing)
            else                          -> Unit
        }
    }

    override fun clear() {
        toolbarHideJob?.cancel()
        resetJob?.cancel()
        super.clear()
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun load(taskId: Int, isPreview: Boolean, wpm: Int) {
        resetJob?.cancel()
        val task = repository.loadTask(taskId)
        val durationMs = calculatePageDurationMs(task?.description ?: "", wpm)
        updateState {
            it.copy(
                task           = task,
                isPreview      = isPreview,
                isLoading      = false,
                toolbarVisible = true,
                countdownDone  = false,
                countdownStartUs = com.oprojectview.core.MonotonicClock.currentTimeUs(),
                pausedCountdownElapsedUs = 0L,
                scrollFraction = 0f,
                isPlaying      = true,
                playbackStartUs = 0L,
                pausedElapsedUs = 0L,
                totalDurationMs = durationMs
            )
        }
        scheduleToolbarHide()
    }

    private fun onCountdownDone() {
        val now = com.oprojectview.core.MonotonicClock.currentTimeUs()
        updateState {
            it.copy(
                countdownDone  = true,
                isPlaying      = true,
                playbackStartUs = now,
                pausedElapsedUs = 0L
            )
        }
    }

    private fun onReplayClicked() {
        val now = com.oprojectview.core.MonotonicClock.currentTimeUs()
        updateState {
            it.copy(
                countdownDone  = false,
                countdownStartUs = now,
                pausedCountdownElapsedUs = 0L,
                scrollFraction = 0f,
                isPlaying      = true,
                playbackStartUs = 0L,
                pausedElapsedUs = 0L
            )
        }
    }

    private fun onSetPlaying(playing: Boolean) {
        scheduleToolbarHide()
        val now = com.oprojectview.core.MonotonicClock.currentTimeUs()
        updateState { state ->
            if (state.isPlaying == playing) return@updateState state
            if (playing) {
                state.copy(
                    isPlaying = true,
                    playbackStartUs = now - state.pausedElapsedUs,
                    countdownStartUs = now - state.pausedCountdownElapsedUs
                )
            } else {
                state.copy(
                    isPlaying = false,
                    pausedElapsedUs = now - state.playbackStartUs,
                    pausedCountdownElapsedUs = now - state.countdownStartUs
                )
            }
        }
    }

    private fun onScrollProgress(fraction: Float) {
        val fractionClamped = fraction.coerceIn(0f, 1f)
        val elapsedUs = (fractionClamped * currentState.totalDurationMs * 1000L).toLong()
        val now = com.oprojectview.core.MonotonicClock.currentTimeUs()
        updateState { state ->
            if (state.isPlaying) {
                state.copy(
                    scrollFraction = fractionClamped,
                    playbackStartUs = now - elapsedUs
                )
            } else {
                state.copy(
                    scrollFraction = fractionClamped,
                    pausedElapsedUs = elapsedUs
                )
            }
        }
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
        updateState { it.copy(
            toolbarVisible = true,
            scrollFraction = 1f
        ) }
    }

    private fun resetPlaybackState() {
        resetJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(1000L)
            updateState {
                it.copy(
                    countdownDone = false,
                    scrollFraction = 0f,
                    isPlaying = false,
                    playbackStartUs = 0L,
                    pausedElapsedUs = 0L
                )
            }
        }
    }

    private fun onBack() {
        val state = currentState
        val taskId = state.task?.id ?: return
        resetPlaybackState()
        emitEvent(PlayerEvent.NavigateToDetail(taskId = taskId, isPreview = state.isPreview))
    }
}
