package com.oprojectview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import org.jetbrains.compose.resources.StringResource
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings

fun settingsKey(
    taskId: Int,
    itemId: Int,
    itemOptId: Int
): String {
    return "display_task_" +
            "${taskId}_" +
            "item_${itemId}_" +
            "itemOpt_${itemOptId}"
}

/**
 * Orientation-staleness fix: all persisted indices are now backed by
 * [mutableStateOf] so Compose re-reads them on every recomposition after
 * [setSelection] is called.
 *
 * ## Root cause of the bug
 *
 * `Settings` (multiplatform-settings) is a plain key-value store with no
 * reactive / observable API in this project.  Every composable that called
 * `displayState.selectedIndex(item)` was doing an imperative `Settings.getBooleanOrNull`
 * read that only executed once inside `remember(taskId)`.  When the user
 * changed the dropdown on the DisplayScreen the setting was persisted, but
 * `PlayerScreenBody` / `PlayerScreenStatic` / `OffscreenFrameRenderer` never
 * re-read it — they held the old value forever.
 *
 * ## Fix
 *
 * Each item's selected index is stored as a `MutableState<Int?>` initialised
 * from `Settings` at construction time.  [setSelection] writes to **both**
 * `Settings` (for persistence across app restarts) and the `MutableState`
 * (for immediate recomposition of any composable that reads [selectedIndex]).
 *
 * `rememberDisplayTaskState` already uses `remember(taskId)` so the instance
 * is stable for the lifetime of a given task's screen — the `MutableState`
 * slots are the reactive layer on top of that.
 */
class DisplayTaskState(
    internal val taskId: Int,
    private  val settings: Settings,
) {
    // One MutableState<Int?> per item id.
    // null = user has never selected anything (fallback to defaultIndex applies).
    private val indexStates = mutableMapOf<Int, androidx.compose.runtime.MutableState<Int?>>()

    /**
     * Returns a **reactive** [androidx.compose.runtime.State] for the given
     * [item]'s selected index.  Composables that read [selectedIndex] will
     * recompose automatically when [setSelection] is called.
     */
    fun selectedIndex(item: DisplayTaskItem): Int? = getOrInit(item).value

    fun selectedOptionRes(item: DisplayTaskItem): StringResource? =
        selectedIndex(item)?.let { item.optionRes[it] }

    fun setSelection(
        item: DisplayTaskItem,
        optionIndex: Int,
    ) {
        // 1. Persist to Settings (survives app restart)
        item.optionRes.indices.forEach { index ->
            settings.remove(settingsKey(taskId, item.id, index))
        }
        settings.putBoolean(settingsKey(taskId, item.id, optionIndex), true)

        // Clear camera calibration keys when overlay mode is None (0) or Window (2)
        if (item.id == 8 && (optionIndex == 0 || optionIndex == 2)) {
            settings.remove("task_${taskId}_calibrated_active")
            settings.remove("task_${taskId}_calibrated_zoom")
            settings.remove("task_${taskId}_calibrated_exposure")
            settings.remove("task_${taskId}_calibrated_is_front")
            settings.remove("task_${taskId}_calibrated_width")
            settings.remove("task_${taskId}_calibrated_height")
            settings.remove("task_${taskId}_calibrated_orientation")
            settings.remove("task_${taskId}_calibrated_flash")
        }

        // 2. Update MutableState → triggers immediate recomposition in every
        //    composable that reads selectedIndex(item) for this item.
        getOrInit(item).value = optionIndex
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private fun getOrInit(item: DisplayTaskItem): androidx.compose.runtime.MutableState<Int?> =
        indexStates.getOrPut(item.id) {
            // Read the persisted value once at init time.
            val persisted = item.optionRes.indices.firstOrNull { index ->
                settings.getBooleanOrNull(settingsKey(taskId, item.id, index)) == true
            }
            mutableStateOf(persisted)
        }

    // ── Loop State ────────────────────────────────────────────────────────────

    private val _isLoopEnabled = mutableStateOf(
        settings.getBooleanOrNull("display_task_${taskId}_loop") ?: false
    )

    fun isAnimationLoopEnabled(): Boolean = _isLoopEnabled.value

    fun setAnimationLoopEnabled(enabled: Boolean) {
        settings.putBoolean("display_task_${taskId}_loop", enabled)
        _isLoopEnabled.value = enabled
    }
}

// ── Composable factory ────────────────────────────────────────────────────────

@Composable
fun rememberDisplayTaskState(taskId: Int): DisplayTaskState {
    val settings = LocalSettings.current
    return remember(taskId) {
        DisplayTaskState(
            taskId   = taskId,
            settings = settings,
        )
    }
}
