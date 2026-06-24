package com.oprojectview.navigation

import com.oprojectview.core.BaseViewModel
import com.oprojectview.core.UiEvent
import com.oprojectview.core.UiIntent
import com.oprojectview.core.UiState
import kotlinx.coroutines.flow.StateFlow

// ── State ─────────────────────────────────────────────────────────────────────

data class RootState(
    val currentScreen: Screen,
) : UiState

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface RootEvent : UiEvent {
    /** Emitted when the user presses back on the root screen. Platform must exit. */
    data object ExitApp : RootEvent
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface RootIntent : UiIntent {
    data object GoToNewTask : RootIntent
    data object GoToDisplay : RootIntent
    data object GoToPlayer  : RootIntent
    data object GoBack      : RootIntent
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * RootViewModel — single owner of the [NavStack].
 *
 * • Owns [NavStack]<[Screen]> — one source of truth for the back-stack.
 * • Exposes [currentScreen] as a StateFlow for Compose.
 * • Provides named actions: [goToNewTask], [goToDisplay], [goToPlayer], [goBack].
 * • [handleBack] first offers the gesture to [backInterceptor] (if set); only
 *   pops the stack when no interceptor claims it.  This lets individual screens
 *   (e.g. NewTaskScreen) show a save-changes dialog before navigation happens,
 *   whether the user taps the toolbar back button, triggers Android's system
 *   back, or performs the iOS left-edge swipe gesture.
 *
 * `with(currentState)` in [onIntent] lets intent guards read state fields
 * without the `currentState.` prefix, keeping the when-block clean.
 */
class RootViewModel : BaseViewModel<RootState, RootEvent>(
    initialState = RootState(currentScreen = Screen.TaskScreen),
) {

    // ── NavStack ──────────────────────────────────────────────────────────────

    val navStack: NavStack<Screen> = NavStack(Screen.TaskScreen)

    /**
     * Top-of-stack screen — delegates directly to [NavStack.current].
     * No state duplication; collect with `collectAsState()` in AppRoot.
     */
    val currentScreen: StateFlow<Screen> = navStack.current

    // ── Back interceptor ──────────────────────────────────────────────────────
    //
    // A screen-level guard that runs before [handleBack] pops the stack.
    // Return true to consume the event (back handled by the screen, e.g. a
    // dialog was shown); return false to fall through to the normal pop.
    //
    // Set via [setBackInterceptor]; cleared (set to null) when leaving the
    // screen that registered it.

    private var backInterceptor: (() -> Boolean)? = null

    /**
     * Register (or clear) a back-press interceptor for the currently active screen.
     *
     * Called from AppRoot's LaunchedEffect(screen) whenever the destination
     * changes. Pass null to clear.
     */
    fun setBackInterceptor(interceptor: (() -> Boolean)?) {
        backInterceptor = interceptor
    }


    // ── Intent dispatch ───────────────────────────────────────────────────────

    override fun onIntent(intent: UiIntent) {
        // `with(currentState)` — read state fields as plain names in the block.
        with(currentState) {
            when (intent) {
                is RootIntent.GoToNewTask -> goToNewTask()
                is RootIntent.GoToDisplay -> goToDisplay()
                is RootIntent.GoToPlayer  -> goToPlayer()
                is RootIntent.GoBack      -> goBack()
            }
        }
    }

    // ── Navigation actions ────────────────────────────────────────────────────

    fun goToNewTask() { navStack.push(Screen.NewTaskScreen); syncState() }
    fun goToDisplay() { navStack.push(Screen.DisplayScreen); syncState() }
    fun goToPlayer()  { navStack.push(Screen.PlayerScreen);  syncState() }
    fun goBack()      { handleBack() }

    // ── Back handling ─────────────────────────────────────────────────────────

    /**
     * Single back-navigation gate — called by:
     *   • Android  → AppRoot's PlatformBackHandler
     *   • Compose gesture → ScreenLayout's horizontal drag gesture detector
     *   • In-app   → goBack() / toolbar back buttons
     *
     * Offers the event to [backInterceptor] first. If the interceptor returns
     * true the event is consumed and the stack is not popped — the interceptor
     * is responsible for navigating away (e.g. by showing a dialog and then
     * emitting NavigateBack on confirm).
     *
     * @return true if consumed (intercepted or stack popped); false if at root.
     */
    fun handleBack(): Boolean {
        // Let the active screen intercept first (e.g. to show a save dialog).
        if (backInterceptor?.invoke() == true) return true

        val consumed = navStack.pop()
        return if (consumed) {
            syncState()
            true
        } else {
            emitEvent(RootEvent.ExitApp)
            false
        }
    }

    // ── Private ───────────────────────────────────────────────────────────────

    private fun syncState() {
        updateState { state ->
            // `with(state)` — copy() without repeating `state.` on every field.
            with(state) { copy(currentScreen = navStack.current.value) }
        }
    }
}
