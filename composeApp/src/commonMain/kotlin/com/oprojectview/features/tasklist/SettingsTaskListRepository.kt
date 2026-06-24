package com.oprojectview.features.tasklist

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

// ── Keys (mirror the constants already in AppNavigation) ─────────────────────
private const val KEY_IDS = "tasks_ids"

private fun keyTitle(id: Int) = "task_title_$id"
private fun keyDesc (id: Int) = "task_desc_$id"
private fun keyIcon (id: Int) = "task_icon_$id"

// ── Implementation ────────────────────────────────────────────────────────────

/**
 * Settings-backed [TaskListRepository].
 *
 * Reads/writes the same keys used by AppNavigation so data is shared
 * transparently during the incremental migration.  Once Step 3 completes,
 * AppNavigation's inline Settings helpers can be deleted and this class
 * becomes the single owner of persistence.
 */
class SettingsTaskListRepository(
    private val settings: Settings,
) : TaskListRepository {

    override fun loadAll(): List<TaskListItem> {
        val ids = loadIds()
        return ids.mapNotNull { id ->
            val title   = settings.getStringOrNull(keyTitle(id)) ?: return@mapNotNull null
            val desc    = settings.getStringOrNull(keyDesc(id))  ?: ""
            val ordinal = settings.getIntOrNull(keyIcon(id))     ?: return@mapNotNull null
            TaskListItem(
                id                  = id,
                title               = title,
                description         = desc,
                leadingShapeOrdinal = ordinal,
            )
        }
    }

    override fun delete(taskId: Int) {
        settings.remove(keyTitle(taskId))
        settings.remove(keyDesc(taskId))
        settings.remove(keyIcon(taskId))
        saveIds(loadIds() - taskId)
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun loadIds(): List<Int> {
        val raw = settings.getStringOrNull(KEY_IDS) ?: return emptyList()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun saveIds(ids: List<Int>) {
        settings[KEY_IDS] = ids.joinToString(",")
    }
}
