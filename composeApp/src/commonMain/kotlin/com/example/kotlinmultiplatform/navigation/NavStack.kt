package com.example.kotlinmultiplatform.navigation

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * NavStack — reusable stack-based navigation library for KMP commonMain.
 *
 * No platform dependencies. Compose-friendly via [current] StateFlow.
 * All mutations are atomic: a single MutableStateFlow.value write after
 * every ArrayDeque operation.
 *
 * Invariant: [root] is always present at index 0 and can never be removed.
 *
 * @param T    Screen type — typically a sealed class / sealed interface.
 * @param root Root screen. Always the bottom of the stack.
 */
class NavStack<T : Any>(private val root: T) {

    private val _backStack: ArrayDeque<T> = ArrayDeque<T>().also { it.addLast(root) }

    private val _current: MutableStateFlow<T> = MutableStateFlow(root)

    /**
     * The currently visible screen.
     * Collect in a Composable:
     * ```kotlin
     * val screen by rootViewModel.currentScreen.collectAsState()
     * ```
     */
    val current: StateFlow<T> = _current.asStateFlow()

    /** True when there is at least one entry below the current screen. */
    val canPop: Boolean get() = _backStack.size > 1

    /** Number of entries on the stack (always ≥ 1). */
    val size: Int get() = _backStack.size

    // ── Mutation API ──────────────────────────────────────────────────────────

    /**
     * Push [destination] onto the top of the stack.
     *
     * Before: [TaskScreen]
     * push(NewTaskScreen)
     * After:  [TaskScreen, NewTaskScreen]   current = NewTaskScreen
     */
    fun push(destination: T) {
        _backStack.addLast(destination)
        commitTop()
    }

    /**
     * Pop the current screen and reveal the one beneath.
     * No-op if already at root.
     *
     * @return true if a screen was popped; false if already at root.
     *
     * Before: [TaskScreen, NewTaskScreen, DisplayScreen]
     * pop()
     * After:  [TaskScreen, NewTaskScreen]   current = NewTaskScreen
     */
    fun pop(): Boolean {
        if (_backStack.size <= 1) return false
        _backStack.removeLast()
        commitTop()
        return true
    }

    /**
     * Replace the current screen without adding an extra back-stack entry.
     *
     * Before: [TaskScreen, NewTaskScreen]
     * replace(DisplayScreen)
     * After:  [TaskScreen, DisplayScreen]   current = DisplayScreen
     */
    fun replace(destination: T) {
        if (_backStack.isNotEmpty()) _backStack.removeLast()
        _backStack.addLast(destination)
        commitTop()
    }

    /**
     * Clear the entire back-stack and return to the root screen.
     *
     * Before: [TaskScreen, NewTaskScreen, DisplayScreen, PlayerScreen]
     * reset()
     * After:  [TaskScreen]   current = TaskScreen
     */
    fun reset() {
        _backStack.clear()
        _backStack.addLast(root)
        commitTop()
    }

    /** Immutable snapshot of the full back-stack (bottom → top). */
    fun snapshot(): List<T> = _backStack.toList()

    // MutableStateFlow.value assignment is atomic in Kotlin Coroutines.
    private fun commitTop() {
        _current.value = _backStack.last()
    }
}
