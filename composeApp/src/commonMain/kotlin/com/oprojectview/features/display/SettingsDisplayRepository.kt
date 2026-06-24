package com.oprojectview.features.display

import com.russhwolf.settings.Settings

// Keys mirror those in SettingsNewTaskRepository — same store, different reader.
private fun keyTitle(id: Int) = "task_title_$id"
private fun keyDesc (id: Int) = "task_desc_$id"
private fun keyIcon (id: Int) = "task_icon_$id"

/**
 * Settings-backed [DisplayRepository].
 *
 * Read-only: Display never writes task content, only reads it.
 * Display option selections are written by [DisplayTaskState] directly.
 */
class SettingsDisplayRepository(
    private val settings: Settings,
) : DisplayRepository {

    override fun loadTask(taskId: Int): DisplayTask? {
        val title   = settings.getStringOrNull(keyTitle(taskId)) ?: return null
        val desc    = (settings.getStringOrNull(keyDesc(taskId)) ?: "").replace("\\'", "'")
        val ordinal = settings.getIntOrNull(keyIcon(taskId))     ?: return null
        return DisplayTask(
            id           = taskId,
            title        = title,
            description  = desc,
            shapeOrdinal = ordinal,
        )
    }
}
