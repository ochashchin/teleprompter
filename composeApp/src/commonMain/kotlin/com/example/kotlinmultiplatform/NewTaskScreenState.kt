package com.example.kotlinmultiplatform

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

// ── Singleton settings instance (no-arg flavour, works on all platforms) ─────
private val settings = Settings()

// ── Keys ─────────────────────────────────────────────────────────────────────
private const val KEY_TOPIC  = "new_task_topic"
private const val KEY_SCRIPT = "new_task_script"

// ── NewTaskScreenState with persistence ──────────────────────────────────────
class NewTaskScreenState(
    val topicState:  TextFieldState,
    val scriptState: TextFieldState,
) {
    val isDirty: Boolean
        get() = topicState.text.isNotEmpty() || scriptState.text.isNotEmpty()

    /** Persist current text to Settings (call on pause / background). */
    fun save() {
        settings[KEY_TOPIC]  = topicState.text.toString()
        settings[KEY_SCRIPT] = scriptState.text.toString()
    }

    /** Restore text from Settings (call on resume / foreground). */
    fun restore() {
        topicState.edit  { replace(0, length, settings.getStringOrNull(KEY_TOPIC)  ?: "") }
        scriptState.edit { replace(0, length, settings.getStringOrNull(KEY_SCRIPT) ?: "") }
    }

    /** Clear both fields and wipe persisted draft. */
    fun clear() {
        topicState.edit  { replace(0, length, "") }
        scriptState.edit { replace(0, length, "") }
        settings.remove(KEY_TOPIC)
        settings.remove(KEY_SCRIPT)
    }
}

@Composable
fun rememberNewTaskScreenState(): NewTaskScreenState {
    val topicState  = rememberTextFieldState()
    val scriptState = rememberTextFieldState()
    return remember { NewTaskScreenState(topicState, scriptState) }
}
