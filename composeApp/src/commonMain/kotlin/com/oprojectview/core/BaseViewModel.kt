package com.oprojectview.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Platform-agnostic base ViewModel for Kotlin Multiplatform.
 *
 * No Android ViewModel / lifecycle dependency — the coroutine scope is
 * owned here and cancelled via [clear], which each platform calls at the
 * appropriate lifecycle moment.
 *
 * @param S  UI state type — must be an immutable data class / sealed class.
 * @param E  UI event type — sealed interface dispatched back to the UI layer.
 */
abstract class BaseViewModel<S : UiState, E : UiEvent>(
    initialState: S,
) {
    // ── Coroutine scope ───────────────────────────────────────────────────────
    //
    // SupervisorJob: a child coroutine failure doesn't cancel siblings or the
    // scope itself — important for concurrent fetch + timer patterns.
    protected val viewModelScope: CoroutineScope =
        CoroutineScope(SupervisorJob() + KmpDispatchers.Main)

    // ── State ─────────────────────────────────────────────────────────────────

    private val _state = MutableStateFlow(initialState)

    /** Read-only state exposed to the UI layer. Collected as a StateFlow. */
    val state: StateFlow<S> = _state.asStateFlow()

    /** Convenience accessor inside the ViewModel. */
    protected val currentState: S get() = _state.value

    /**
     * Atomically update state via a pure transform.
     * Always called on [viewModelScope]; safe to call from any coroutine.
     *
     * Usage:
     * ```
     * updateState { it.copy(isLoading = true) }
     * ```
     */
    protected fun updateState(transform: (S) -> S) {
        _state.value = transform(_state.value)
    }

    // ── Events ────────────────────────────────────────────────────────────────
    //
    // SharedFlow with replay=0: each event is consumed exactly once.
    // Buffer of 64 avoids dropping events under back-pressure.

    private val _events = MutableSharedFlow<E>(
        replay      = 0,
        extraBufferCapacity = 64,
    )

    /** One-shot events (navigation, errors, toasts) emitted to the UI. */
    val events: SharedFlow<E> = _events.asSharedFlow()

    /**
     * Emit a one-shot event to the UI.
     *
     * Usage:
     * ```
     * emitEvent(UiEvent.Navigate(Destination.TaskList))
     * ```
     */
    protected fun emitEvent(event: E) {
        viewModelScope.launch { _events.emit(event) }
    }

    // ── Intent dispatch ───────────────────────────────────────────────────────

    /**
     * Entry point for all user intents (button taps, text input, swipes …).
     *
     * Implementations should be exhaustive `when` expressions over a sealed
     * UiIntent type so the compiler enforces completeness.
     */
    abstract fun onIntent(intent: UiIntent)

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /**
     * Call when the ViewModel is no longer needed (screen removed from back
     * stack, window closed …).  Cancels [viewModelScope] and all running
     * coroutines.
     */
    open fun clear() {
        viewModelScope.cancel()
    }
}
