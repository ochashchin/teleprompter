package com.example.kotlinmultiplatform.features.newtask

import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

// ── Keys ─────────────────────────────────────────────────────────────────────
//
// These are the same keys used by AppNavigation since day one.
// Keeping them identical means zero data migration — existing installs
// continue reading their saved tasks without any changes.

private const val KEY_POPULATED = "tasks_populated"
private const val KEY_NEXT_ID   = "tasks_next_id"
private const val KEY_IDS       = "tasks_ids"
private const val KEY_DRAFT_TOPIC  = "new_task_draft_topic"   // same as NewTaskScreenState
private const val KEY_DRAFT_SCRIPT = "new_task_draft_script"

private fun keyTitle(id: Int) = "task_title_$id"
private fun keyDesc (id: Int) = "task_desc_$id"
private fun keyIcon (id: Int) = "task_icon_$id"

// ── Seed data (moved from AppNavigation) ─────────────────────────────────────

private data class SeedTask(val title: String, val desc: String, val shapeOrdinal: Int)

private val seed = listOf(
    SeedTask("Buy groceries", "Milk, Eggs, Bread, Coffee",               26),  // HEART ordinal
    SeedTask("KMP Project",   "Sync repository and update dependencies",  14), // COOKIE_6 ordinal
    SeedTask("Gym session",   "Leg day workout at 6 PM",                  11), // SUNNY ordinal
    SeedTask("Read book",     "Read 10 pages of Atomic Habits",           7),  // DIAMOND ordinal
)

// ── Implementation ────────────────────────────────────────────────────────────

/**
 * Settings-backed [NewTaskRepository].
 *
 * Owns all task creation, update, and draft persistence.
 * After Step 3 is complete the inline helpers in AppNavigation are deleted
 * and this class is the single writer of the task-related keys.
 */
class SettingsNewTaskRepository(
    private val settings: Settings,
) : NewTaskRepository {

    // ── Seeding ───────────────────────────────────────────────────────────────

    override fun ensureSeeded() {
        if (settings.getBoolean(KEY_POPULATED, false)) return
        var id  = settings.getInt(KEY_NEXT_ID, 1)
        val ids = mutableListOf<Int>()
        seed.forEach { s ->
            writeTaskKeys(id, s.title, s.desc, s.shapeOrdinal)
            ids.add(id++)
        }
        saveIds(ids)
        settings[KEY_NEXT_ID]   = id
        settings[KEY_POPULATED] = true
    }

    // ── Id counter ────────────────────────────────────────────────────────────

    override fun nextId(): Int = settings.getInt(KEY_NEXT_ID, 1)

    override fun consumeNextId(): Int {
        val id = settings.getInt(KEY_NEXT_ID, 1)
        settings[KEY_NEXT_ID] = id + 1
        return id
    }

    // ── Task CRUD ─────────────────────────────────────────────────────────────

    override fun saveTask(title: String, desc: String, shapeOrdinal: Int): SavedTask {
        val id  = consumeNextId()
        val ids = loadIds() + id
        writeTaskKeys(id, title, desc, shapeOrdinal)
        saveIds(ids)
        return SavedTask(id, title, desc, shapeOrdinal)
    }

    override fun updateTask(id: Int, title: String, desc: String) {
        // Only update if the id still exists in the list; if the id was
        // invalidated by a concurrent delete, create a fresh entry instead.
        val ids = loadIds()
        if (id in ids) {
            settings[keyTitle(id)] = title
            settings[keyDesc(id)]  = desc
            // shape is preserved — updateTask never changes the icon
        } else {
            // Id no longer valid — recreate with a new id
            val newId  = consumeNextId()
            val ordinal = settings.getIntOrNull(keyIcon(id)) ?: 0
            writeTaskKeys(newId, title, desc, ordinal)
            saveIds(ids + newId)
        }
    }

    override fun loadTask(id: Int): SavedTask? {
        val title   = settings.getStringOrNull(keyTitle(id)) ?: return null
        val desc    = settings.getStringOrNull(keyDesc(id))  ?: ""
        val ordinal = settings.getIntOrNull(keyIcon(id))     ?: 0
        return SavedTask(id, title, desc, ordinal)
    }

    // ── Draft ─────────────────────────────────────────────────────────────────

    override fun saveDraft(topic: String, script: String) {
        settings[KEY_DRAFT_TOPIC]  = topic
        settings[KEY_DRAFT_SCRIPT] = script
    }

    override fun loadDraft(): DraftData? {
        val topic  = settings.getStringOrNull(KEY_DRAFT_TOPIC)
        val script = settings.getStringOrNull(KEY_DRAFT_SCRIPT)
        return if (topic != null || script != null)
            DraftData(topic ?: "", script ?: "")
        else null
    }

    override fun clearDraft() {
        settings.remove(KEY_DRAFT_TOPIC)
        settings.remove(KEY_DRAFT_SCRIPT)
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun loadIds(): List<Int> {
        val raw = settings.getStringOrNull(KEY_IDS) ?: return emptyList()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    private fun saveIds(ids: List<Int>) {
        settings[KEY_IDS] = ids.joinToString(",")
    }

    private fun writeTaskKeys(id: Int, title: String, desc: String, shapeOrdinal: Int) {
        settings[keyTitle(id)] = title
        settings[keyDesc(id)]  = desc
        settings[keyIcon(id)]  = shapeOrdinal
    }
}
