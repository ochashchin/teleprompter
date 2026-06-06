package com.example.kotlinmultiplatform.navigation

import com.example.kotlinmultiplatform.core.BaseViewModel
import com.example.kotlinmultiplatform.core.UiEvent
import com.example.kotlinmultiplatform.core.UiIntent
import com.example.kotlinmultiplatform.core.UiState

// ── State ─────────────────────────────────────────────────────────────────────

/**
 * Immutable back-stack snapshot.
 *
 * Generic over [D] so Step 1 can use the existing [Destination] type and
 * Steps 2-4 can migrate to [AppDestination] screen-by-screen without a
 * flag-day rewrite.
 *
 * Invariant: [backStack] is never empty — root entry is always present.
 */
data class NavigationState<D : Any>(
    val backStack: List<D>,
) : UiState {
    val current: D      get() = backStack.last()
    val canGoBack: Boolean get() = backStack.size > 1
}

// ── Events ────────────────────────────────────────────────────────────────────

sealed interface NavigationUiEvent<out D : Any> : UiEvent {
    /** Slide in from the right (forward). */
    data class TransitionForward<D : Any>(val to: D) : NavigationUiEvent<D>

    /** Slide in from the left (back). */
    data class TransitionBack<D : Any>(val to: D) : NavigationUiEvent<D>

    /** Pop entire stack to root — no directional animation. */
    data object PopToRoot : NavigationUiEvent<Nothing>
}

// ── Intents ───────────────────────────────────────────────────────────────────

sealed interface NavigationIntent<out D : Any> : UiIntent {
    data class Push<D : Any>(val destination: D)    : NavigationIntent<D>
    data class Replace<D : Any>(val destination: D) : NavigationIntent<D>
    data object Pop                                 : NavigationIntent<Nothing>
    data object PopToRoot                           : NavigationIntent<Nothing>
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

/**
 * Generic navigation back-stack ViewModel.
 *
 * ### Step 1 usage (current — Destination still carries Task objects)
 * ```kotlin
 * val navViewModel = NavigationViewModel(Destination.TaskList)
 * navViewModel.push(Destination.NewDetail)
 * navViewModel.push(Destination.Detail(task = someTask, isPreview = true))
 * ```
 *
 * ### Step 2-4 usage (after each screen migrates to id-based AppDestination)
 * ```kotlin
 * val navViewModel = NavigationViewModel(AppDestination.TaskList)
 * navViewModel.push(AppDestination.Detail(taskId = 3))
 * ```
 *
 * The type parameter is the only thing that changes between the two phases.
 *
 * @param root  The root destination. Always kept as the bottom of the stack;
 *              [popToRoot] resets to exactly `listOf(root)`.
 */
class NavigationViewModel<D : Any>(
    private val root: D,
) : BaseViewModel<NavigationState<D>, NavigationUiEvent<D>>(
    initialState = NavigationState(backStack = listOf(root)),
) {
    // ── Public intent entry-point ─────────────────────────────────────────────

    @Suppress("UNCHECKED_CAST")
    override fun onIntent(intent: UiIntent) {
        when (intent) {
            is NavigationIntent.Push<*>    -> push(intent.destination as D)
            is NavigationIntent.Replace<*> -> replace(intent.destination as D)
            is NavigationIntent.Pop        -> pop()
            is NavigationIntent.PopToRoot  -> popToRoot()
        }
    }

    // ── Convenience API (callable directly from composables) ─────────────────

    fun push(destination: D) {
        updateState { it.copy(backStack = it.backStack + destination) }
        emitEvent(NavigationUiEvent.TransitionForward(destination))
    }

    fun pop() {
        val stack = currentState.backStack
        if (stack.size <= 1) return
        val newStack = stack.dropLast(1)
        updateState { it.copy(backStack = newStack) }
        emitEvent(NavigationUiEvent.TransitionBack(newStack.last()))
    }

    fun popToRoot() {
        updateState { it.copy(backStack = listOf(root)) }
        emitEvent(NavigationUiEvent.PopToRoot)
    }

    fun replace(destination: D) {
        val stack    = currentState.backStack
        val newStack = if (stack.isEmpty()) listOf(destination)
                       else stack.dropLast(1) + destination
        updateState { it.copy(backStack = newStack) }
        emitEvent(NavigationUiEvent.TransitionForward(destination))
    }
}
