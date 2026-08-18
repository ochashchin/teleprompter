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
    
    // UpNext fields
    val allTasks: List<PlayerTask>  = emptyList(),
    val upNextTasks: List<PlayerTask> = emptyList(),
    val upNextSelectedIndex: Int    = 0,
    val hasManuallySelectedUpNext: Boolean = false,
    val isShowingUpNext: Boolean    = false,
    val wasAutoSwitched: Boolean    = false,
    val isTransitioningToNextTask: Boolean = false,
    val isUpNextCountdownActive: Boolean = true,
) : UiState

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface PlayerEvent : UiEvent {
    /** Navigate back to Detail for this task. */
    data class NavigateToDetail(
        val taskId: Int,
        val isPreview: Boolean,
        val wasAutoSwitched: Boolean = false
    ) : PlayerEvent

    /** Preload styles for the next task during transition. */
    data class PreloadNextTaskStyles(val taskId: Int) : PlayerEvent

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
    data class  ReplayClicked(val isManual: Boolean = false, val skipDelay: Boolean = true) : PlayerIntent
    data class  ScrollProgress(val fraction: Float) : PlayerIntent
    data class  SetPlaying(val playing: Boolean)    : PlayerIntent
    data class  SetTotalDurationMs(val durationMs: Long) : PlayerIntent
    
    // UpNext Intents
    data class OnUpNextItemSelected(val index: Int) : PlayerIntent
    data object UpNextCountdownDone : PlayerIntent
    data object StopUpNextCountdown : PlayerIntent
}

// ── Repository interface ──────────────────────────────────────────────────────

interface PlayerRepository {
    fun loadTask(taskId: Int): PlayerTask?
    fun loadAllTasks(): List<PlayerTask>
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class PlayerViewModel(
    private val repository: PlayerRepository,
) : BaseViewModel<PlayerState, PlayerEvent>(
    initialState = PlayerState(),
) {
    private var resetJob: Job? = null
    private var countdownJob: Job? = null
    private var upNextJob: Job? = null

    private fun checkCountdown() {
        countdownJob?.cancel()
        val state = currentState
        if (state.isPlaying && !state.countdownDone) {
            val now = com.oprojectview.core.MonotonicClock.currentTimeUs()
            val elapsedUs = now - state.countdownStartUs
            val elapsedMs = elapsedUs / 1000L
            val totalCountdownMs = 6000L // 5000ms countdown + 1000ms extra delay
            val remainingMs = totalCountdownMs - elapsedMs
            if (remainingMs > 0) {
                countdownJob = viewModelScope.launch {
                    delay(remainingMs)
                    onCountdownDone()
                }
            } else {
                onCountdownDone()
            }
        }
    }

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
            is PlayerIntent.ReplayClicked -> onReplayClicked(intent.isManual, intent.skipDelay)
            is PlayerIntent.ScrollProgress -> onScrollProgress(intent.fraction)
            is PlayerIntent.SetPlaying    -> onSetPlaying(intent.playing)
            is PlayerIntent.OnUpNextItemSelected -> onUpNextItemSelected(intent.index)
            is PlayerIntent.UpNextCountdownDone -> onUpNextCountdownDone()
            is PlayerIntent.StopUpNextCountdown -> stopUpNextCountdown()
            is PlayerIntent.SetTotalDurationMs  -> updateState { it.copy(totalDurationMs = intent.durationMs) }
            else                          -> Unit
        }
    }

    override fun clear() {
        resetJob?.cancel()
        countdownJob?.cancel()
        upNextJob?.cancel()
        super.clear()
    }

    // ── Handlers ──────────────────────────────────────────────────────────────

    private fun load(taskId: Int, isPreview: Boolean, wpm: Int, skipSpinner: Boolean = false) {
        resetJob?.cancel()
        upNextJob?.cancel()
        val now = com.oprojectview.core.MonotonicClock.currentTimeUs()
        val task = repository.loadTask(taskId)
        val durationMs = calculatePageDurationMs(task?.description ?: "", wpm)
        val allTasks = repository.loadAllTasks()
        val currentTaskIndex = allTasks.indexOfFirst { it.id == taskId }
        
        val upNextList = if (currentTaskIndex != -1 && currentTaskIndex + 1 < allTasks.size) {
            allTasks.subList(currentTaskIndex + 1, allTasks.size)
        } else {
            allTasks // Show the entire list when reaching the end
        }
        
        val upNextIndex = 0 // default to the first item in the upNext list
        
        updateState {
            it.copy(
                task           = task,
                isPreview      = isPreview,
                isLoading      = false,
                toolbarVisible = false,
                countdownDone  = skipSpinner,
                countdownStartUs = now,
                pausedCountdownElapsedUs = 0L,
                scrollFraction = 0f,
                isPlaying      = true,
                playbackStartUs = if (skipSpinner) now - 1_000_000L else 0L,
                pausedElapsedUs = 0L,
                totalDurationMs = durationMs,
                allTasks       = allTasks,
                upNextTasks    = upNextList,
                upNextSelectedIndex = upNextIndex,
                hasManuallySelectedUpNext = false,
                isShowingUpNext = false,
                wasAutoSwitched = false,
                isTransitioningToNextTask = false,
                isUpNextCountdownActive = true
            )
        }
        checkCountdown()
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

    private fun onReplayClicked(isManual: Boolean, skipDelay: Boolean) {
        upNextJob?.cancel()
        val now = com.oprojectview.core.MonotonicClock.currentTimeUs()
        if (isManual) {
            updateState {
                it.copy(
                    countdownDone  = false,
                    countdownStartUs = now,
                    pausedCountdownElapsedUs = 0L,
                    scrollFraction = 0f,
                    isPlaying      = true,
                    playbackStartUs = 0L,
                    pausedElapsedUs = 0L,
                    toolbarVisible = false,
                    isShowingUpNext = false
                )
            }
        } else {
            updateState {
                it.copy(
                    countdownDone  = true,
                    countdownStartUs = now,
                    pausedCountdownElapsedUs = 0L,
                    scrollFraction = 0f,
                    isPlaying      = true,
                    playbackStartUs = if (skipDelay) now - 1_000_000L else now,
                    pausedElapsedUs = 0L,
                    toolbarVisible = false,
                    isShowingUpNext = false
                )
            }
        }
        checkCountdown()
    }

    private fun onSetPlaying(playing: Boolean) {
        val now = com.oprojectview.core.MonotonicClock.currentTimeUs()
        updateState { state ->
            if (state.isPlaying == playing) return@updateState state
            if (playing) {
                state.copy(
                    isPlaying = true,
                    playbackStartUs = if (state.countdownDone) now - state.pausedElapsedUs else state.playbackStartUs,
                    countdownStartUs = if (!state.countdownDone) now - state.pausedCountdownElapsedUs else state.countdownStartUs,
                    toolbarVisible = false
                )
            } else {
                state.copy(
                    isPlaying = false,
                    pausedElapsedUs = if (state.countdownDone) now - state.playbackStartUs else state.pausedElapsedUs,
                    pausedCountdownElapsedUs = if (!state.countdownDone) now - state.countdownStartUs else state.pausedCountdownElapsedUs,
                    toolbarVisible = true
                )
            }
        }
        checkCountdown()
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
     * Single tap: toggles play/pause state and UI visibility.
     * If the player is finished, a tap triggers a replay.
     */
    private fun onTap() {
        if (currentState.isShowingUpNext) {
            stopUpNextCountdown()
            return
        }
        if (currentState.scrollFraction >= 1f) {
            updateState { it.copy(toolbarVisible = !it.toolbarVisible) }
            return
        }
        onSetPlaying(!currentState.isPlaying)
    }

    private fun stopUpNextCountdown() {
        upNextJob?.cancel()
        updateState { it.copy(isUpNextCountdownActive = false) }
    }

    private fun onReadingComplete() {
        updateState { it.copy(
            toolbarVisible = true,
            scrollFraction = 1f,
            isShowingUpNext = true
        ) }
        checkUpNextCountdown()
    }
    
    private fun checkUpNextCountdown() {
        upNextJob?.cancel()
        val state = currentState
        if (!state.isUpNextCountdownActive) return
        val isLastTask = state.task?.id == state.allTasks.lastOrNull()?.id
        val shouldCountdown = !isLastTask || state.hasManuallySelectedUpNext
        if (shouldCountdown && state.upNextTasks.isNotEmpty()) {
            upNextJob = viewModelScope.launch {
                delay(5000L)
                onUpNextCountdownDone()
            }
        }
    }
    
    private fun onUpNextItemSelected(index: Int) {
        val state = currentState
        if (!state.isUpNextCountdownActive) {
            updateState { it.copy(upNextSelectedIndex = index) }
            viewModelScope.launch {
                delay(150) // Allow Material ripple/wave effect to play and be visible
                onUpNextCountdownDone()
            }
            return
        }

        if (index == state.upNextSelectedIndex && state.hasManuallySelectedUpNext) return
        updateState { it.copy(upNextSelectedIndex = index, hasManuallySelectedUpNext = true) }
        checkUpNextCountdown()
    }
    
    private fun onUpNextCountdownDone() {
        val state = currentState
        if (state.upNextTasks.isNotEmpty() && state.upNextSelectedIndex in state.upNextTasks.indices) {
            val nextTask = state.upNextTasks[state.upNextSelectedIndex]
            val wpm = if (state.totalDurationMs > 0 && state.task != null) {
                val prevTaskLength = state.task.description.split("\\s+".toRegex()).size
                val mins = state.totalDurationMs / 60000.0
                if (mins > 0) (prevTaskLength / mins).toInt().coerceAtLeast(1) else 150
            } else {
                150
            }
            
            updateState { it.copy(isShowingUpNext = false, toolbarVisible = false, wasAutoSwitched = true, isTransitioningToNextTask = true) }
            emitEvent(PlayerEvent.PreloadNextTaskStyles(nextTask.id))
            
            viewModelScope.launch {
                kotlinx.coroutines.delay(1000L)
                load(nextTask.id, false, wpm, skipSpinner = true)
            }
        }
    }

    private fun resetPlaybackState() {
        resetJob?.cancel()
        upNextJob?.cancel()
        resetJob = viewModelScope.launch {
            delay(1000L)
            updateState {
                it.copy(
                    countdownDone = false,
                    scrollFraction = 0f,
                    isPlaying = false,
                    playbackStartUs = 0L,
                    pausedElapsedUs = 0L,
                    isTransitioningToNextTask = false
                )
            }
        }
    }

    private fun onBack() {
        val state = currentState
        val taskId = state.task?.id ?: return
        resetPlaybackState()
        // Always pass wasAutoSwitched = false when the user manually presses Back.
        // wasAutoSwitched = true is only meaningful for the automatic UpNext transition
        // (handled in onUpNextCountdownDone) and must not affect user-initiated navigation.
        emitEvent(PlayerEvent.NavigateToDetail(
            taskId = taskId,
            isPreview = state.isPreview,
            wasAutoSwitched = false
        ))
    }
}
