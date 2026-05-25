package com.example.kotlinmultiplatform

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

// ── Keys ─────────────────────────────────────────────────────────────────────
private const val KEY_TOPIC  = "new_task_draft_topic"
private const val KEY_SCRIPT = "new_task_draft_script"

// ── NewTaskScreenState ────────────────────────────────────────────────────────
class NewTaskScreenState(
    val topicState:  TextFieldState,
    val scriptState: TextFieldState,
    private val settings: Settings,
) {
    /** True when either field has been edited by the user. */
    val isNotEmpty: Boolean
        get() = topicState.text.isNotEmpty() || scriptState.text.isNotEmpty()

    /** True when a persisted draft exists. */
    val hasDraft: Boolean
        get() = settings.getStringOrNull(KEY_TOPIC) != null ||
                settings.getStringOrNull(KEY_SCRIPT) != null

    /** Persist the current field text as a draft. */
    fun save() {
        settings[KEY_TOPIC]  = topicState.text.toString()
        settings[KEY_SCRIPT] = scriptState.text.toString()
    }

    /**
     * Restore a previously saved draft into the fields.
     * Only writes if a draft key actually exists — leaves fields blank
     * on a fresh open when no draft was saved.
     */
    fun restore() {
        settings.getStringOrNull(KEY_TOPIC)?.let  { v ->
            topicState.edit  { replace(0, length, v) }
        }
        settings.getStringOrNull(KEY_SCRIPT)?.let { v ->
            scriptState.edit { replace(0, length, v) }
        }
    }

    /**
     * Prefill both fields with explicit values (e.g. from a tapped task-list
     * item or when returning from the Display/Preview screen via Back).
     * Clears any persisted draft so the freshly prefilled content is canonical.
     */
    fun prefill(topic: String, script: String) {
        topicState.edit  { replace(0, length, topic)  }
        scriptState.edit { replace(0, length, script) }
        // Wipe draft so restore() won't overwrite these values if the
        // destination triggers the LaunchedEffect again.
        settings.remove(KEY_TOPIC)
        settings.remove(KEY_SCRIPT)
    }

    /**
     * Clear both fields and wipe the persisted draft.
     * Call on Save (after reading values) and on Discard.
     */
    fun clear() {
        topicState.edit  { replace(0, length, "") }
        scriptState.edit { replace(0, length, "") }
        settings.remove(KEY_TOPIC)
        settings.remove(KEY_SCRIPT)
    }

    /** Snapshot values for task creation — read before calling clear(). */
    val topicText:  String get() = topicState.text.toString()
    val scriptText: String get() = scriptState.text.toString()
}

@Composable
fun rememberNewTaskScreenState(): NewTaskScreenState {
    val topicState  = rememberTextFieldState()
    val scriptState = rememberTextFieldState()
    val settings    = LocalSettings.current
    return remember(settings) { NewTaskScreenState(topicState, scriptState, settings) }
}
