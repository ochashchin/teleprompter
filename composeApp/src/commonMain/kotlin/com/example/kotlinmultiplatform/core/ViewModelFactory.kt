package com.example.kotlinmultiplatform.core

import com.example.kotlinmultiplatform.Destination
import com.example.kotlinmultiplatform.features.display.DisplayRepository
import com.example.kotlinmultiplatform.features.display.DisplayViewModel
import com.example.kotlinmultiplatform.features.newtask.NewTaskRepository
import com.example.kotlinmultiplatform.features.newtask.NewTaskViewModel
import com.example.kotlinmultiplatform.features.player.PlayerRepository
import com.example.kotlinmultiplatform.features.player.PlayerViewModel
import com.example.kotlinmultiplatform.features.tasklist.TaskListRepository
import com.example.kotlinmultiplatform.features.tasklist.TaskListViewModel
import com.example.kotlinmultiplatform.navigation.NavigationViewModel

/**
 * Lightweight manual DI factory for ViewModels in commonMain.
 *
 * Does NOT depend on Koin / Kodein / Dagger — keeps it platform-agnostic.
 * Swap for a proper DI framework at any time: the interface stays the same.
 *
 * Lifecycle note:
 *  - ViewModels created here are NOT automatically cleared.
 *  - The platform integration layer (Android Activity / iOS ViewController)
 *    must call [AppViewModelStore.clear] at the appropriate lifecycle event.
 */
object ViewModelFactory {

    fun createNavigationViewModel(): NavigationViewModel<Destination> =
        @Suppress("UNCHECKED_CAST")
        NavigationViewModel(Destination.TaskList as Destination)

    fun createTaskListViewModel(repository: TaskListRepository): TaskListViewModel =
        TaskListViewModel(repository)

    fun createNewTaskViewModel(repository: NewTaskRepository): NewTaskViewModel =
        NewTaskViewModel(repository)

    fun createDisplayViewModel(repository: DisplayRepository): DisplayViewModel =
        DisplayViewModel(repository)

    fun createPlayerViewModel(repository: PlayerRepository): PlayerViewModel =
        PlayerViewModel(repository)
}

/**
 * Simple ViewModel store scoped to the app's lifetime.
 *
 * Usage in a Compose root:
 * ```kotlin
 * val store = remember { AppViewModelStore(...) }
 * DisposableEffect(Unit) { onDispose { store.clear() } }
 * ```
 */
class AppViewModelStore(
    taskListRepository: TaskListRepository,
    newTaskRepository:  NewTaskRepository,
    displayRepository:  DisplayRepository,
    playerRepository:   PlayerRepository,
) {
    val navigation = ViewModelFactory.createNavigationViewModel()
    val taskList   = ViewModelFactory.createTaskListViewModel(taskListRepository)
    val newTask    = ViewModelFactory.createNewTaskViewModel(newTaskRepository)
    val display    = ViewModelFactory.createDisplayViewModel(displayRepository)
    val player     = ViewModelFactory.createPlayerViewModel(playerRepository)

    /** Cancel all coroutine scopes. Call from DisposableEffect or platform lifecycle. */
    fun clear() {
        navigation.clear()
        taskList.clear()
        newTask.clear()
        display.clear()
        player.clear()
    }
}
