package com.example.kotlinmultiplatform.core

import com.oprojectview.features.tasklist.TaskListIntent
import com.oprojectview.features.tasklist.TaskListItem
import com.oprojectview.features.tasklist.TaskListRepository
import com.oprojectview.features.tasklist.TaskListViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TaskListViewModelTest {

    private class FakeTaskListRepository(
        private val initialTasks: List<TaskListItem> = emptyList(),
    ) : TaskListRepository {
        private val tasks = initialTasks.toMutableList()
        override fun loadAll(): List<TaskListItem> = tasks.toList()
        override fun delete(taskId: Int) { tasks.removeAll { it.id == taskId } }
    }

    private val sampleTasks = listOf(
        TaskListItem(id = 1, title = "Buy groceries", description = "Milk, Eggs, Bread", leadingShapeOrdinal = 26),
        TaskListItem(id = 2, title = "KMP Project",   description = "Sync repo",         leadingShapeOrdinal = 14),
        TaskListItem(id = 3, title = "Gym session",   description = "Leg day at 6 PM",   leadingShapeOrdinal = 11),
    )

    private fun buildViewModel(tasks: List<TaskListItem> = sampleTasks) =
        TaskListViewModel(FakeTaskListRepository(tasks))

    @Test
    fun `initial state is loading`() = runTest {
        val vm = buildViewModel()
        assertTrue(vm.state.value.isLoading)
        vm.clear()
    }

    @Test
    fun `Load intent populates visibleTasks`() = runTest {
        val vm = buildViewModel()
        vm.onIntent(TaskListIntent.Load)
        advanceUntilIdle()
        val state = vm.state.value
        assertFalse(state.isLoading)
        assertEquals(3, state.visibleTasks.size)
        vm.clear()
    }

    @Test
    fun `QueryChanged filters visibleTasks`() = runTest {
        val vm = buildViewModel()
        vm.onIntent(TaskListIntent.Load)
        advanceUntilIdle()
        vm.onIntent(TaskListIntent.QueryChanged("gym"))
        val state = vm.state.value
        assertEquals(1, state.visibleTasks.size)
        assertEquals("Gym session", state.visibleTasks.first().title)
        vm.clear()
    }

    @Test
    fun `SearchClosed resets query and shows all tasks`() = runTest {
        val vm = buildViewModel()
        vm.onIntent(TaskListIntent.Load)
        advanceUntilIdle()
        vm.onIntent(TaskListIntent.QueryChanged("gym"))
        vm.onIntent(TaskListIntent.SearchClosed)
        val state = vm.state.value
        assertEquals("", state.query)
        assertFalse(state.isSearchActive)
        assertEquals(3, state.visibleTasks.size)
        vm.clear()
    }

    @Test
    fun `TaskDismissed removes task optimistically`() = runTest {
        val vm = buildViewModel()
        vm.onIntent(TaskListIntent.Load)
        advanceUntilIdle()
        vm.onIntent(TaskListIntent.TaskDismissed(sampleTasks[0]))
        val state = vm.state.value
        assertEquals(2, state.visibleTasks.size)
        assertFalse(state.visibleTasks.any { it.id == 1 })
        vm.clear()
    }

    @Test
    fun `NewTaskClicked emits NavigateToNewTask event`() = runTest {
        val vm = buildViewModel()
        vm.onIntent(TaskListIntent.NewTaskClicked)
        val event = vm.events.first()
        assertTrue(event.toString().contains("NavigateToNewTask"))
        vm.clear()
    }
}
