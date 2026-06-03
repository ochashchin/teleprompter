package com.example.kotlinmultiplatform

import androidx.compose.runtime.Composable
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

class DisplayTaskState(
    internal val taskId: Int,
    private  val settings: Settings,
) {

    /**
     * Returns the label of the persisted option for [item],
     * or null if the user has never made a selection.
     *
     * Flow example:
     *   Task(id=7) → DisplayTaskItem(id=2) → stored key index=1
     *   → reads "display_task_7_item_2_itemOpt_1" → returns item.options[1]
     */
    /**
     * Returns the persisted option index for [item],
     * or null if the user has never made a selection.
     */
    fun selectedIndex(item: DisplayTaskItem): Int? =
        item.options.indices.firstOrNull { index ->
            settings.getBooleanOrNull(settingsKey(taskId, item.id, index)) == true
        }

    fun selectedOption(item: DisplayTaskItem): String? =
        selectedIndex(item)?.let { item.options[it] }

    fun setSelection(
        item: DisplayTaskItem,
        optionIndex: Int
    ) {

        item.options.indices.forEach { index ->

            settings.remove(
                settingsKey(
                    taskId,
                    item.id,
                    index
                )
            )
        }

        settings.putBoolean(
            settingsKey(
                taskId,
                item.id,
                optionIndex
            ),
            true
        )
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