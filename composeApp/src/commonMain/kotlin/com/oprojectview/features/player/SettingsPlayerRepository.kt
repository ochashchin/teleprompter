package com.oprojectview.features.player

import com.russhwolf.settings.Settings

private fun keyTitle(id: Int) = "task_title_$id"
private fun keyDesc (id: Int) = "task_desc_$id"
private fun keyIcon (id: Int) = "task_icon_$id"

/**
 * Settings-backed [PlayerRepository].
 * Read-only: the Player screen never mutates task content.
 */
class SettingsPlayerRepository(
    private val settings: Settings,
) : PlayerRepository {

    override fun loadTask(taskId: Int): PlayerTask? {
        val title   = settings.getStringOrNull(keyTitle(taskId)) ?: return null
        val desc    = settings.getStringOrNull(keyDesc(taskId))  ?: ""
        val ordinal = settings.getIntOrNull(keyIcon(taskId))     ?: return null
        return PlayerTask(
            id           = taskId,
            title        = title,
            description  = desc,
            shapeOrdinal = ordinal,
        )
    }
}
