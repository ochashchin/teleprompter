package com.oprojectview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

// ── Settings key helpers ──────────────────────────────────────────────────────
//
// Mirror the DisplayTaskState pattern:
//   "script_style_${taskId}_spans"
//   "script_style_${taskId}_fill"
//
// taskId == NEW_TASK_ID (-1) is used while the task hasn't been saved yet.
// Once saved, the caller migrates the keys via migrateToTaskId().

const val NEW_TASK_ID = -1

fun scriptStyleSpansKey(taskId: Int) = "script_style_${taskId}_spans"
fun scriptStyleFillKey(taskId: Int)  = "script_style_${taskId}_fill"

/**
 * Read a Long from Settings, returning [default] if the key holds a legacy
 * Boolean (written before the fill-color migration) or is absent.
 * Removes the corrupt key so the next write stores the correct type.
 */
private fun Settings.getLongOrDefault(key: String, default: Long): Long = try {
    getLong(key, default)
} catch (_: Exception) {
    remove(key)   // wipe legacy Boolean so next setFillColor writes a clean Long
    default
}

// ── Span model ────────────────────────────────────────────────────────────────

/**
 * A single styled range within the script text.
 *
 * @param start      inclusive character index
 * @param end        exclusive character index
 * @param isBold
 * @param isItalic
 * @param isUnderline
 * @param textColor  packed ARGB Long (ULong bits stored as Long); 0L = use default theme colour
 */
data class StyleSpan(
    val start:       Int,
    val end:         Int,
    val isBold:      Boolean = false,
    val isItalic:    Boolean = false,
    val isUnderline: Boolean = false,
    val textColorIndex: Int = -1,
) {
    val hasAnyStyle: Boolean
        get() = isBold || isItalic || isUnderline || textColorIndex != -1

    fun toSpanStyle(palette: List<Color?>): SpanStyle = SpanStyle(
        fontWeight     = if (isBold) FontWeight.Bold else null,
        fontStyle      = if (isItalic) FontStyle.Italic else null,
        textDecoration = if (isUnderline) TextDecoration.Underline else null,
        color          = if (textColorIndex in palette.indices) palette[textColorIndex] ?: Color.Unspecified else Color.Unspecified,
    )
}

// ── Serialisation ─────────────────────────────────────────────────────────────

internal fun List<StyleSpan>.serialise(): String =
    joinToString(";") { s ->
        "${s.start}|${s.end}|${s.isBold.b}|${s.isItalic.b}|${s.isUnderline.b}|${s.textColorIndex}"
    }

internal fun String.deserialiseSpans(): List<StyleSpan> {
    if (isBlank()) return emptyList()
    return split(";").mapNotNull { entry ->
        val p = entry.split("|")
        if (p.size != 6) return@mapNotNull null
        runCatching {
            StyleSpan(
                start       = p[0].toInt(),
                end         = p[1].toInt(),
                isBold      = p[2] == "1",
                isItalic    = p[3] == "1",
                isUnderline = p[4] == "1",
                textColorIndex = run {
                    val raw = p[5].toLong()
                    if (raw > 10L || raw < -1L) raw.toScriptTextColorIndex() else raw.toInt()
                },
            )
        }.getOrNull()
    }
}

private val Boolean.b get() = if (this) "1" else "0"

// ── Pre-defined colours ──────────────────────────────────────────────────────

fun getScriptTextColors(): List<Color?> {
    return listOf(
        Color(0xFFE53935), // Red
        Color(0xFF1E88E5), // Blue
        Color(0xFF43A047), // Green
        Color(0xFFFFB300), // Amber
        Color(0xFF8E24AA), // Purple
        null // Default state rendered as onSurfaceVariant
    )
}

@Composable
fun getScriptFillColors(): List<Color?> {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return listOf(
        Color(if (isDark) 0xFF421C1C else 0xFFFADBD8),
        Color(if (isDark) 0xFF1A2F4C else 0xFFD6E4F0),
        Color(if (isDark) 0xFF1C3322 else 0xFFD5E8D4),
        Color(if (isDark) 0xFF3A321A else 0xFFFCF3CF),
        Color(if (isDark) 0xFF2D1B36 else 0xFFEBDEF0),
        null // Default state rendered as surfaceContainerLow
    )
}

// ── ScriptTextStyleState ──────────────────────────────────────────────────────

/**
 * Holds rich-text style state for [ScriptTextField].
 *
 * ### Key design
 *
 * Keys are scoped by [taskId]:
 *   - `script_style_${taskId}_spans`  — serialised span list
 *   - `script_style_${taskId}_fill`   — fill-colour toggle
 *
 * While a task has not been saved yet, [taskId] == [NEW_TASK_ID] (-1).
 * After the task is first saved, call [migrateToTaskId] to move the data
 * to the permanent key; the in-memory state is preserved unchanged.
 *
 * ### Write strategy
 *
 * All style mutations are **memory-only**. Settings are written to disk
 * only when the user confirms saving, via [migrateToTaskId] (new task /
 * edit-then-save) or [flushToDisk] (called internally by those paths).
 * This means Discard never leaves stale data on disk for either new or
 * existing tasks — [clear] simply resets in-memory state and removes the
 * temporary NEW_TASK_ID draft keys.
 *
 * ### Unsaved-changes tracking
 *
 * [hasUnsavedChanges] becomes true as soon as any style toggle is applied
 * and is reset to false by [markSaved] / [clear].
 * The NewTaskScreen uses this flag to show the "Save changes?" dialog even
 * when the text fields themselves haven't been modified.
 */

fun Long.toScriptTextColorIndex(): Int {
    if (this == 0L) return -1
    return when (this) {
        0xFFE53935 -> 0
        0xFF1E88E5 -> 1
        0xFF43A047 -> 2
        0xFFFFB300 -> 3
        0xFF8E24AA -> 4
        else -> -1
    }
}

fun Long.toScriptFillColorIndex(): Int {
    if (this == 0L) return -1
    return when (this) {
        0xFFFADBD8, 0xFF421C1C -> 0
        0xFFD6E4F0, 0xFF1A2F4C -> 1
        0xFFD5E8D4, 0xFF1C3322 -> 2
        0xFFFCF3CF, 0xFF3A321A -> 3
        0xFFEBDEF0, 0xFF2D1B36 -> 4
        else -> -1
    }
}

class ScriptTextStyleState(
    private val settings: Settings,
    initialTaskId: Int = NEW_TASK_ID,
) {
    // ── Task identity ─────────────────────────────────────────────────────────

    private var taskId: Int = initialTaskId

    // ── Span list ─────────────────────────────────────────────────────────────

    private var _spans by mutableStateOf(
        settings.getStringOrNull(scriptStyleSpansKey(initialTaskId))?.deserialiseSpans()
            ?: emptyList()
    )

    val spans: List<StyleSpan> get() = _spans

    // ── Fill colour ───────────────────────────────────────────────────────────
    // Stored as a packed ARGB Long (0L = no fill active).

    private var _fillColorIndex: Int by mutableStateOf(
        run {
            val rawValue = settings.getLongOrDefault(scriptStyleFillKey(initialTaskId), 0L)
            if (rawValue > 10L || rawValue < -1L) rawValue.toScriptFillColorIndex() else rawValue.toInt()
        }
    )

    val activeFillColorIndex: Int get() = _fillColorIndex

    val isFillColorActive: Boolean get() = _fillColorIndex != -1

    // ── Dirty flag ────────────────────────────────────────────────────────────

    /** True once any style change has been applied since the last [markSaved] / [clear]. */
    var hasUnsavedChanges: Boolean by mutableStateOf(false)
        private set

    // ── Task ID migration ─────────────────────────────────────────────────────

    /**
     * Called after a new task is first saved to disk.
     * Flushes the current in-memory style state to Settings under [newTaskId],
     * removes the temporary [NEW_TASK_ID] draft keys if applicable, and
     * updates the internal [taskId].
     * No-op (flush only) if [taskId] already equals [newTaskId].
     */
    fun migrateToTaskId(newTaskId: Int) {
        if (taskId == newTaskId) {
            // Already on the right key — flush current in-memory state to disk
            // in case styles were modified since the last loadForTaskId.
            flushToDisk(newTaskId)
            return
        }

        // Flush under the new key
        flushToDisk(newTaskId)

        // Remove the old temporary draft key (never remove a real task key here)
        if (taskId == NEW_TASK_ID) {
            settings.remove(scriptStyleSpansKey(NEW_TASK_ID))
            settings.remove(scriptStyleFillKey(NEW_TASK_ID))
        }

        taskId = newTaskId
    }

    /**
     * Switch to an existing task's style data (called when entering edit mode).
     * Loads from disk and resets the dirty flag.
     */
    fun loadForTaskId(newTaskId: Int) {
        taskId          = newTaskId
        _spans          = settings.getStringOrNull(scriptStyleSpansKey(newTaskId))
            ?.deserialiseSpans() ?: emptyList()
        _fillColorIndex = run {
            val raw = settings.getLongOrDefault(scriptStyleFillKey(newTaskId), 0L)
            if (raw > 10L || raw < -1L) raw.toScriptFillColorIndex() else raw.toInt()
        }
        hasUnsavedChanges = false
    }

    /** Mark the current state as saved (clears the dirty flag without wiping data). */
    fun markSaved() {
        hasUnsavedChanges = false
    }

    // ── Public toggle API ─────────────────────────────────────────────────────

    fun toggleBold(start: Int, end: Int) {
        if (start >= end) return
        val allBold = spansInRange(start, end).all { it.isBold }
        applyStyle(start, end) { it.copy(isBold = !allBold) }
    }

    fun toggleItalic(start: Int, end: Int) {
        if (start >= end) return
        val allItalic = spansInRange(start, end).all { it.isItalic }
        applyStyle(start, end) { it.copy(isItalic = !allItalic) }
    }

    fun toggleUnderline(start: Int, end: Int) {
        if (start >= end) return
        val allUnderline = spansInRange(start, end).all { it.isUnderline }
        applyStyle(start, end) { it.copy(isUnderline = !allUnderline) }
    }

    fun toggleTextColor(index: Int, start: Int, end: Int) {
        if (start >= end) return
        val currentIdx = spansInRange(start, end).firstOrNull()?.textColorIndex ?: -1
        val nextIdx = if (currentIdx == index) -1 else index
        applyStyle(start, end) { it.copy(textColorIndex = nextIdx) }
    }

    /** The active text color index in the given range, or -1 if default. */
    fun rangeTextColorIndex(start: Int, end: Int): Int {
        if (start >= end) return -1
        return spansInRange(start, end).firstOrNull()?.textColorIndex ?: -1
    }

    /**
     * Set the fill colour index. Pass -1 to clear fill.
     *
     * Memory-only — disk write is deferred to [migrateToTaskId] on save.
     */
    fun toggleFillColor(index: Int) {
        _fillColorIndex = index
        hasUnsavedChanges = true
    }

    // ── Selection query helpers ───────────────────────────────────────────────

    fun isRangeBold(start: Int, end: Int): Boolean =
        start < end && spansInRange(start, end).all { it.isBold }

    fun isRangeItalic(start: Int, end: Int): Boolean =
        start < end && spansInRange(start, end).all { it.isItalic }

    fun isRangeUnderline(start: Int, end: Int): Boolean =
        start < end && spansInRange(start, end).all { it.isUnderline }

    

    // ── Text-change cleanup ───────────────────────────────────────────────────

    fun onTextChanged(newLength: Int) {
        val cleaned = _spans
            .mapNotNull { s ->
                val result: StyleSpan? = if (s.start >= newLength) {
                    null
                } else if (s.end > newLength) {
                    s.copy(end = newLength)
                } else {
                    s
                }
                result
            }
            .filter { it.hasAnyStyle }
        if (cleaned != _spans) _spans = cleaned
    }

    /**
     * Wipe all style data and reset the dirty flag (discard / new task).
     *
     * For new tasks ([taskId] == [NEW_TASK_ID]): removes the temporary draft
     * keys from disk.
     * For existing tasks: reloads the last-saved state from disk, so any
     * in-progress (unsaved) style changes are rolled back cleanly.
     */
    fun clear() {
        if (taskId == NEW_TASK_ID) {
            // Remove the temporary draft keys — nothing permanent to restore.
            settings.remove(scriptStyleSpansKey(NEW_TASK_ID))
            settings.remove(scriptStyleFillKey(NEW_TASK_ID))
            _spans          = emptyList()
            _fillColorIndex = -1
        } else {
            // Existing task: reload the last persisted state so the on-disk
            // data is unchanged and in-memory reflects what was actually saved.
            _spans          = settings.getStringOrNull(scriptStyleSpansKey(taskId))
                ?.deserialiseSpans() ?: emptyList()
            _fillColorIndex = run {
                val raw = settings.getLongOrDefault(scriptStyleFillKey(taskId), 0L)
                if (raw > 10L || raw < -1L) raw.toScriptFillColorIndex() else raw.toInt()
            }
        }
        hasUnsavedChanges = false
        taskId = NEW_TASK_ID
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun spansInRange(start: Int, end: Int): List<StyleSpan> {
        val overlapping = _spans.filter { it.start < end && it.end > start }
        if (overlapping.isEmpty()) return listOf(StyleSpan(start, end))
        return buildList {
            var cur = start
            while (cur < end) {
                val span = overlapping.firstOrNull { it.start <= cur && it.end > cur }
                if (span != null) {
                    add(span); cur = span.end
                } else {
                    val nextSpanStart = overlapping.filter { it.start > cur }
                        .minOfOrNull { it.start } ?: end
                    add(StyleSpan(cur, minOf(nextSpanStart, end)))
                    cur = minOf(nextSpanStart, end)
                }
            }
        }
    }

    private fun applyStyle(start: Int, end: Int, transform: (StyleSpan) -> StyleSpan) {
        val result = mutableListOf<StyleSpan>()
        for (existing in _spans) {
            when {
                existing.end <= start -> result.add(existing)
                existing.start >= end -> result.add(existing)
                else -> {
                    if (existing.start < start) result.add(existing.copy(end = start))
                    if (existing.end   > end)   result.add(existing.copy(start = end))
                }
            }
        }
        val existingInRange = _spans.filter { it.start < end && it.end > start }
        val base = existingInRange.fold(StyleSpan(start, end)) { acc, s ->
            acc.copy(
                isBold      = acc.isBold      || s.isBold,
                isItalic    = acc.isItalic    || s.isItalic,
                isUnderline = acc.isUnderline || s.isUnderline,
                textColorIndex = if (acc.textColorIndex != -1) acc.textColorIndex else s.textColorIndex,
            )
        }
        val newSpan = transform(base)
        if (newSpan.hasAnyStyle) result.add(newSpan)
        // Memory-only: disk write is deferred to migrateToTaskId on save.
        _spans = mergeSame(result.sortedWith(compareBy({ it.start }, { it.end })))
        hasUnsavedChanges = true
    }

    private fun mergeSame(sorted: List<StyleSpan>): List<StyleSpan> {
        if (sorted.isEmpty()) return emptyList()
        val out = mutableListOf(sorted[0])
        for (i in 1 until sorted.size) {
            val prev = out.last(); val cur = sorted[i]
            if (prev.end == cur.start &&
                prev.isBold      == cur.isBold      &&
                prev.isItalic    == cur.isItalic     &&
                prev.isUnderline == cur.isUnderline  &&
                prev.textColorIndex == cur.textColorIndex
            ) {
                out[out.lastIndex] = prev.copy(end = cur.end)
            } else {
                out.add(cur)
            }
        }
        return out
    }

    /** Write current in-memory state to Settings under [id]. */
    private fun flushToDisk(id: Int) {
        settings[scriptStyleSpansKey(id)] = _spans.serialise()
        settings[scriptStyleFillKey(id)]  = _fillColorIndex
    }
}

// ── Composable factory ────────────────────────────────────────────────────────

@Composable
fun rememberScriptTextStyleState(taskId: Int = NEW_TASK_ID): ScriptTextStyleState {
    val settings = LocalSettings.current
    return remember(settings, taskId) {
        ScriptTextStyleState(settings, taskId)
    }
}

@Composable
fun ScriptTextStyleState.resolveActiveFillColor(): Color? {
    val idx = activeFillColorIndex
    if (idx < 0) return null
    val colors = getScriptFillColors()
    return if (idx < colors.size) colors[idx] else null
}
