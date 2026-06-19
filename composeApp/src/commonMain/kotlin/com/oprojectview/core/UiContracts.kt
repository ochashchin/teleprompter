package com.oprojectview.core

// ── Marker interfaces ─────────────────────────────────────────────────────────
//
// These are empty contracts that let the type system enforce the MVVM contract:
//  • States  → StateFlow<S : UiState>    — held inside BaseViewModel
//  • Events  → SharedFlow<E : UiEvent>   — one-shot signals to the UI
//  • Intents → BaseViewModel.onIntent()  — user actions flowing into the VM
//
// Every feature defines its own sealed subtypes of these three interfaces.

/** Marker for immutable UI state snapshots. Always a data class or data object. */
interface UiState

/**
 * Marker for one-shot events emitted from a ViewModel to the UI layer.
 *
 * Sealed sub-hierarchies typically group events by concern:
 *  - [NavigationEvent] — screen transitions
 *  - [ErrorEvent]      — user-visible error messages
 *  - [ActionEvent]     — transient UI actions (show snackbar, scroll-to-top …)
 */
interface UiEvent

/**
 * Marker for user intents dispatched from the UI into a ViewModel.
 *
 * Mirrors the MVI "intent" concept while keeping our base class pattern-name
 * consistent (MVVM + explicit intent channel = practical MVVM/MVI hybrid).
 */
interface UiIntent

// ── Cross-cutting event hierarchy ─────────────────────────────────────────────
//
// Navigation and error events are so universal that we define a shared sealed
// hierarchy here.  Feature-specific events extend these or add their own.

/** Navigation events that any ViewModel can emit. */
sealed interface NavigationEvent : UiEvent {
    /** Navigate forward to the given destination. */
    data class NavigateTo(val destination: AppDestination) : NavigationEvent

    /** Go back one step in the navigation stack. */
    data object NavigateBack : NavigationEvent

    /** Pop the entire back-stack and go to the root destination. */
    data object NavigateToRoot : NavigationEvent
}

/** User-visible error events. */
sealed interface ErrorEvent : UiEvent {
    /** Show a transient error message (snackbar / toast). */
    data class ShowError(val message: String) : ErrorEvent

    /** Show a blocking error state with a retry action. */
    data class ShowBlockingError(
        val message: String,
        val retryIntent: UiIntent? = null,
    ) : ErrorEvent
}

/** Generic transient UI action events. */
sealed interface ActionEvent : UiEvent {
    data class ShowSnackbar(val message: String) : ActionEvent
    data object ScrollToTop : ActionEvent
    data object DismissKeyboard : ActionEvent
}
