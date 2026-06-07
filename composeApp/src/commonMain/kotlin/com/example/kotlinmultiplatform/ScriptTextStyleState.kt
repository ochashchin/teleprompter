package com.example.kotlinmultiplatform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
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
    val textColor:   Long    = 0L,
) {
    val hasAnyStyle: Boolean
        get() = isBold || isItalic || isUnderline || textColor != 0L

    fun toSpanStyle(): SpanStyle = SpanStyle(
        fontWeight     = if (isBold) FontWeight.Bold else null,
        fontStyle      = if (isItalic) FontStyle.Italic else null,
        textDecoration = if (isUnderline) TextDecoration.Underline else null,
        color          = if (textColor != 0L) Color(textColor.toULong()) else Color.Unspecified,
    )
}

// ── Serialisation ─────────────────────────────────────────────────────────────

internal fun List<StyleSpan>.serialise(): String =
    joinToString(";") { s ->
        "${s.start}|${s.end}|${s.isBold.b}|${s.isItalic.b}|${s.isUnderline.b}|${s.textColor}"
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
                textColor   = p[5].toLong(),
            )
        }.getOrNull()
    }
}

private val Boolean.b get() = if (this) "1" else "0"

// ── Pre-defined colours ──────────────────────────────────────────────────────

val ScriptTextColors: List<Color> = listOf(
    Color(0xFFE53935), // Red
    Color(0xFF1E88E5), // Blue
    Color(0xFF43A047), // Green
    Color(0xFFFFB300), // Amber
    Color(0xFF8E24AA), // Purple
    Color(0xFFFFFFFF), // White
    Color(0xFF000000), // Black
)

val ScriptFillColors: List<Color> = listOf(
    Color(0xFFE53935), // Red
    Color(0xFF1E88E5), // Blue
    Color(0xFF43A047), // Green
    Color(0xFFFFB300), // Amber
    Color(0xFF8E24AA), // Purple
    Color(0xFFFFFFFF), // White
    Color(0xFF000000), // Black
)

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
 * ### Unsaved-changes tracking
 *
 * [hasUnsavedChanges] becomes true as soon as any style toggle is applied
 * and is reset to false by [markSaved] / [clear].
 * The NewTaskScreen uses this flag to show the "Save changes?" dialog even
 * when the text fields themselves haven't been modified.
 */
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

    private var _fillColorValue: Long by mutableStateOf(
        settings.getLongOrDefault(scriptStyleFillKey(initialTaskId), 0L)
    )

    /** Currently active fill colour, or null when fill is off. */
    val activeFillColor: Color?
        get() = if (_fillColorValue != 0L) Color(_fillColorValue.toULong()) else null

    /** True when any fill colour is active. */
    val isFillColorActive: Boolean get() = _fillColorValue != 0L

    // ── Dirty flag ────────────────────────────────────────────────────────────

    /** True once any style change has been applied since the last [markSaved] / [clear]. */
    var hasUnsavedChanges: Boolean by mutableStateOf(false)
        private set

    // ── Task ID migration ─────────────────────────────────────────────────────

    /**
     * Called after a new task is first saved to disk.
     * Moves the style data from the temporary [NEW_TASK_ID] key to the real
     * [newTaskId] key, then updates the internal [taskId].
     * No-op if [taskId] already equals [newTaskId].
     */
    fun migrateToTaskId(newTaskId: Int) {
        if (taskId == newTaskId) {
            // Already on the right key — still flush current in-memory state to disk
            // in case styles were modified since the last loadForTaskId.
            settings[scriptStyleSpansKey(newTaskId)] = _spans.serialise()
            settings[scriptStyleFillKey(newTaskId)]  = _fillColorValue
            return
        }

        // Persist under the new key
        settings[scriptStyleSpansKey(newTaskId)] = _spans.serialise()
        settings[scriptStyleFillKey(newTaskId)]  = _fillColorValue

        // Remove the old temporary key
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
        taskId            = newTaskId
        val loaded        = settings.getStringOrNull(scriptStyleSpansKey(newTaskId))
            ?.deserialiseSpans() ?: emptyList()
        _spans            = loaded
        _fillColorValue = settings.getLongOrDefault(scriptStyleFillKey(newTaskId), 0L)
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

    fun toggleTextColor(start: Int, end: Int) {
        if (start >= end) return
        val currentColor = spansInRange(start, end).firstOrNull()?.textColor ?: 0L
        val currentIndex = ScriptTextColors.indexOfFirst { it.value.toLong() == currentColor }
        val nextIndex    = currentIndex + 1
        val nextColor    = if (nextIndex < ScriptTextColors.size)
            ScriptTextColors[nextIndex].value.toLong()
        else 0L
        applyStyle(start, end) { it.copy(textColor = nextColor) }
    }

    /** Set text colour directly from a colour picker. Pass null to clear colour. */
    fun setTextColor(start: Int, end: Int, color: Color?) {
        if (start >= end) return
        val packed = color?.value?.toLong() ?: 0L
        applyStyle(start, end) { it.copy(textColor = packed) }
    }

    /** The active text color in the given range, or null if default. */
    fun activeTextColorInRange(start: Int, end: Int): Color? {
        if (start >= end) return null
        val color = spansInRange(start, end).firstOrNull()?.textColor ?: 0L
        return if (color != 0L) Color(color.toULong()) else null
    }

    /** Set the fill colour. Pass null to clear fill. */
    fun setFillColor(color: Color?) {
        _fillColorValue = color?.value?.toLong() ?: 0L
        settings[scriptStyleFillKey(taskId)] = _fillColorValue
        hasUnsavedChanges = true
    }

    /** Convenience: toggle fill off; use setFillColor() for a specific colour. */
    fun toggleFillColor() {
        setFillColor(if (isFillColorActive) null else ScriptFillColors.firstOrNull())
    }

    // ── Selection query helpers ───────────────────────────────────────────────

    fun isRangeBold(start: Int, end: Int): Boolean =
        start < end && spansInRange(start, end).all { it.isBold }

    fun isRangeItalic(start: Int, end: Int): Boolean =
        start < end && spansInRange(start, end).all { it.isItalic }

    fun isRangeUnderline(start: Int, end: Int): Boolean =
        start < end && spansInRange(start, end).all { it.isUnderline }

    fun rangeTextColor(start: Int, end: Int): Color? = activeTextColorInRange(start, end)

    // ── Text-change cleanup ───────────────────────────────────────────────────

    fun onTextChanged(newLength: Int) {
        val cleaned = _spans
            .mapNotNull { s ->
                when {
                    s.start >= newLength -> null
                    s.end   >  newLength -> s.copy(end = newLength)
                    else                 -> s
                }
            }
            .filter { it.hasAnyStyle }
        if (cleaned != _spans) persist(cleaned)
    }

    /** Wipe all style data and reset the dirty flag (discard / new task). */
    fun clear() {
        // Reset in-memory state only — do NOT write to disk for a real task id.
        // Writing persist(emptyList()) here would overwrite the permanently saved
        // style for that task every time the user discards or navigates away.
        // Only the temporary NEW_TASK_ID key is safe to remove from disk.
        _spans = emptyList()
        _fillColorValue   = 0L
        hasUnsavedChanges = false
        // Remove the temporary draft key (NEW_TASK_ID = -1) from disk.
        settings.remove(scriptStyleSpansKey(NEW_TASK_ID))
        settings.remove(scriptStyleFillKey(NEW_TASK_ID))
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
                textColor   = if (acc.textColor != 0L) acc.textColor else s.textColor,
            )
        }
        val newSpan = transform(base)
        if (newSpan.hasAnyStyle) result.add(newSpan)
        persist(mergeSame(result.sortedWith(compareBy({ it.start }, { it.end }))))
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
                prev.textColor   == cur.textColor
            ) {
                out[out.lastIndex] = prev.copy(end = cur.end)
            } else {
                out.add(cur)
            }
        }
        return out
    }

    private fun persist(spans: List<StyleSpan>) {
        _spans = spans
        settings[scriptStyleSpansKey(taskId)] = spans.serialise()
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