package com.oprojectview

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.russhwolf.settings.Settings
import com.russhwolf.settings.set

// ── Settings key helpers ──────────────────────────────────────────────────────

const val NEW_TASK_ID = -1

fun scriptStyleSpansKey(taskId: Int) = "script_style_${taskId}_spans"
fun scriptStyleFillKey(taskId: Int)  = "script_style_${taskId}_fill"

// ── Span model ────────────────────────────────────────────────────────────────

/**
 * A single styled range within the script text.
 *
 * @param start      inclusive character index
 * @param end        exclusive character index
 * @param isBold
 * @param isItalic
 * @param isUnderline
 * @param textColorIndex  index of the active text colour, or -1 for default
 */
data class StyleSpan(
    val start:          Int,
    val end:            Int,
    val isBold:         Boolean = false,
    val isItalic:       Boolean = false,
    val isUnderline:    Boolean = false,
    val textColorIndex: Int     = -1,
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

internal fun List<StyleSpan>.serialize(): String =
    joinToString(";") { s ->
        "${s.start}|${s.end}|${s.isBold.b}|${s.isItalic.b}|${s.isUnderline.b}|${s.textColorIndex}"
    }

internal fun String.deserializeSpans(): List<StyleSpan> {
    if (isBlank()) return emptyList()
    return split(";").mapNotNull { entry ->
        val p = entry.split("|")
        if (p.size != 6) return@mapNotNull null
        runCatching {
            StyleSpan(
                start          = p[0].toInt(),
                end            = p[1].toInt(),
                isBold         = p[2] == "1",
                isItalic       = p[3] == "1",
                isUnderline    = p[4] == "1",
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
        Color(0xFF3F9943), // Greenish
        Color(0xFFE50D9A), // Pink
        Color(0xFFCA00FF), // Purple
        Color(0xFF0FD821), // Bright Green
        null               // Default state rendered as onSurfaceVariant
    )
}

@Composable
fun getScriptFillColors(): List<Color?> {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return listOf(
        Color(if (isDark) 0xFF421C1C else 0xFFFADBD8),
        Color(if (isDark) 0xFF1C2D20 else 0xFFC4E4C8),
        Color(if (isDark) 0xFF2E1C29 else 0xFFE3CCDB),
        Color(if (isDark) 0xFF3A1C34 else 0xFFFACFEB),
        Color(if (isDark) 0xFF17331F else 0xFFCFF7D3),
        MaterialTheme.colorScheme.surface
    )
}

fun Long.toScriptTextColorIndex(): Int {
    if (this == 0L) return -1
    return when (this) {
        0xFF1E88E5 -> 1
        0xFF43A047 -> 2
        0xFFFFB300 -> 3
        0xFF8E24AA -> 4
        0xFF077F12 -> 1
        0xFFE53935 -> 0
        0xFF3F9943 -> 1
        0xFFE50D9A -> 2
        0xFFCA00FF -> 3
        0xFF0FD821 -> 4
        else       -> -1
    }
}

// ── Centralized Text Edit & Range Transformations ────────────────────────────

/**
 * Synchronizes character-level style spans when text is edited (inserted, deleted, or replaced).
 */
fun applyTextChange(oldText: String, newText: String, spans: List<StyleSpan>): List<StyleSpan> {
    if (oldText == newText) return spans
    if (spans.isEmpty()) return emptyList()

    val oldLen = oldText.length
    val newLen = newText.length

    // Common prefix
    var prefix = 0
    while (prefix < oldLen && prefix < newLen && oldText[prefix] == newText[prefix]) {
        prefix++
    }

    // Common suffix
    var suffix = 0
    while (suffix < (oldLen - prefix) && suffix < (newLen - prefix) &&
        oldText[oldLen - 1 - suffix] == newText[newLen - 1 - suffix]
    ) {
        suffix++
    }

    val editStart = prefix
    val deletedLen = oldLen - prefix - suffix
    val insertedLen = newLen - prefix - suffix

    var updated = spans

    // 1. Process deletion
    if (deletedLen > 0) {
        val editEnd = editStart + deletedLen
        val afterDeletion = mutableListOf<StyleSpan>()
        for (span in updated) {
            when {
                // Completely before deletion
                span.end <= editStart -> {
                    afterDeletion.add(span)
                }
                // Completely after deletion -> shift backward
                span.start >= editEnd -> {
                    afterDeletion.add(span.copy(
                        start = span.start - deletedLen,
                        end   = span.end - deletedLen
                    ))
                }
                // Completely inside deleted range -> remove
                span.start >= editStart && span.end <= editEnd -> {}
                // Deletion is strictly inside span -> shrink
                span.start < editStart && span.end > editEnd -> {
                    afterDeletion.add(span.copy(
                        end = span.end - deletedLen
                    ))
                }
                // Overlaps end of span -> clip end
                span.start < editStart && span.end > editStart -> {
                    afterDeletion.add(span.copy(
                        end = editStart
                    ))
                }
                // Overlaps start of span -> clip start
                span.start >= editStart && span.start < editEnd && span.end > editEnd -> {
                    afterDeletion.add(span.copy(
                        start = editStart,
                        end   = span.end - deletedLen
                    ))
                }
            }
        }
        updated = afterDeletion
    }

    // 2. Process insertion
    if (insertedLen > 0) {
        val afterInsertion = mutableListOf<StyleSpan>()
        for (span in updated) {
            when {
                // Completely before insertion point
                span.end <= editStart -> {
                    afterInsertion.add(span)
                }
                // Completely after insertion point -> shift forward
                span.start >= editStart -> {
                    afterInsertion.add(span.copy(
                        start = span.start + insertedLen,
                        end   = span.end + insertedLen
                    ))
                }
                // Insertion point strictly inside span -> expand
                else -> {
                    afterInsertion.add(span.copy(
                        end = span.end + insertedLen
                    ))
                }
            }
        }
        updated = afterInsertion
    }

    // 3. Clamp and sanitize
    return mergeAndSanitize(updated, newLen)
}

/**
 * Splits and transforms style spans on a sub-range [start..end] without corrupting surrounding text.
 */
fun applyStyleToRange(
    start: Int,
    end: Int,
    textLength: Int,
    currentSpans: List<StyleSpan>,
    transform: (StyleSpan) -> StyleSpan
): List<StyleSpan> {
    val s = start.coerceIn(0, textLength)
    val e = end.coerceIn(s, textLength)
    if (s >= e) return currentSpans

    val boundaries = sortedSetOf(s, e)
    for (span in currentSpans) {
        if (span.start in s..e) boundaries.add(span.start)
        if (span.end in s..e) boundaries.add(span.end)
    }

    val result = mutableListOf<StyleSpan>()
    for (span in currentSpans) {
        when {
            span.end <= s   -> result.add(span)
            span.start >= e -> result.add(span)
            else -> {
                if (span.start < s) result.add(span.copy(end = s))
                if (span.end > e)   result.add(span.copy(start = e))
            }
        }
    }

    val boundaryList = boundaries.toList()
    for (i in 0 until boundaryList.size - 1) {
        val segStart = boundaryList[i]
        val segEnd = boundaryList[i + 1]
        if (segStart >= segEnd) continue

        val existing = currentSpans.firstOrNull { it.start <= segStart && it.end >= segEnd }
        val base = existing?.copy(start = segStart, end = segEnd) ?: StyleSpan(segStart, segEnd)
        val transformed = transform(base).copy(start = segStart, end = segEnd)
        if (transformed.hasAnyStyle) {
            result.add(transformed)
        }
    }

    return mergeAndSanitize(result, textLength)
}

fun mergeAndSanitize(spans: List<StyleSpan>, textLength: Int): List<StyleSpan> {
    val valid = spans.mapNotNull { span ->
        val s = span.start.coerceIn(0, textLength)
        val e = span.end.coerceIn(s, textLength)
        if (s < e && span.hasAnyStyle) span.copy(start = s, end = e) else null
    }.sortedWith(compareBy({ it.start }, { it.end }))

    if (valid.isEmpty()) return emptyList()

    val merged = mutableListOf(valid[0])
    for (i in 1 until valid.size) {
        val prev = merged.last()
        val cur = valid[i]
        if (prev.end == cur.start &&
            prev.isBold         == cur.isBold         &&
            prev.isItalic       == cur.isItalic       &&
            prev.isUnderline    == cur.isUnderline    &&
            prev.textColorIndex == cur.textColorIndex
        ) {
            merged[merged.lastIndex] = prev.copy(end = cur.end)
        } else {
            merged.add(cur)
        }
    }
    return merged
}

// ── ScriptTextStyleState ──────────────────────────────────────────────────────

/**
 * Holds rich-text style state for [ScriptTextField].
 */
class ScriptTextStyleState(
    private val settings: Settings,
    initialTaskId: Int = NEW_TASK_ID,
) {
    var taskId: Int = initialTaskId
        private set

    private var _spans by mutableStateOf(
        settings.getStringOrNull(scriptStyleSpansKey(initialTaskId))?.deserializeSpans()
            ?: emptyList()
    )

    val spans: List<StyleSpan> get() = _spans

    var lastObservedText: String = ""
        private set

    private var _fillColorIndex: Int by mutableStateOf(
        settings.getInt(scriptStyleFillKey(initialTaskId), -1)
    )

    val activeFillColorIndex: Int get() = _fillColorIndex

    var hasUnsavedChanges: Boolean by mutableStateOf(false)
        private set

    fun initText(text: String) {
        lastObservedText = text
        _spans = mergeAndSanitize(_spans, text.length)
    }

    fun onTextChanged(newText: String) {
        if (lastObservedText == newText) return
        val prevText = lastObservedText
        lastObservedText = newText
        _spans = applyTextChange(oldText = prevText, newText = newText, spans = _spans)
    }

    fun migrateToTaskId(newTaskId: Int) {
        if (taskId == newTaskId) {
            flushToDisk(newTaskId)
            return
        }

        flushToDisk(newTaskId)

        if (taskId == NEW_TASK_ID) {
            settings.remove(scriptStyleSpansKey(NEW_TASK_ID))
            settings.remove(scriptStyleFillKey(NEW_TASK_ID))
        }

        taskId = newTaskId
    }

    fun loadForTaskId(newTaskId: Int) {
        taskId          = newTaskId
        _spans          = settings.getStringOrNull(scriptStyleSpansKey(newTaskId))
            ?.deserializeSpans() ?: emptyList()
        _fillColorIndex = settings.getInt(scriptStyleFillKey(newTaskId), -1)
        hasUnsavedChanges = false
    }

    fun markSaved() {
        hasUnsavedChanges = false
    }

    // ── Public toggle API ─────────────────────────────────────────────────────

    fun toggleBold(start: Int, end: Int) {
        if (start >= end) return
        val allBold = isRangeBold(start, end)
        val textLen = lastObservedText.length.coerceAtLeast(end)
        _spans = applyStyleToRange(start, end, textLen, _spans) {
            it.copy(isBold = !allBold)
        }
        hasUnsavedChanges = true
    }

    fun toggleItalic(start: Int, end: Int) {
        if (start >= end) return
        val allItalic = isRangeItalic(start, end)
        val textLen = lastObservedText.length.coerceAtLeast(end)
        _spans = applyStyleToRange(start, end, textLen, _spans) {
            it.copy(isItalic = !allItalic)
        }
        hasUnsavedChanges = true
    }

    fun toggleUnderline(start: Int, end: Int) {
        if (start >= end) return
        val allUnderline = isRangeUnderline(start, end)
        val textLen = lastObservedText.length.coerceAtLeast(end)
        _spans = applyStyleToRange(start, end, textLen, _spans) {
            it.copy(isUnderline = !allUnderline)
        }
        hasUnsavedChanges = true
    }

    fun toggleTextColor(index: Int, start: Int, end: Int) {
        if (start >= end) return
        val currentColor = rangeTextColorIndex(start, end)
        val nextColor = if (currentColor == index) -1 else index
        val textLen = lastObservedText.length.coerceAtLeast(end)
        _spans = applyStyleToRange(start, end, textLen, _spans) {
            it.copy(textColorIndex = nextColor)
        }
        hasUnsavedChanges = true
    }

    fun toggleFillColor(index: Int) {
        _fillColorIndex = index
        hasUnsavedChanges = true
    }

    // ── Selection query helpers ───────────────────────────────────────────────

    fun isRangeBold(start: Int, end: Int): Boolean {
        if (start >= end) return false
        val segs = getSegmentStyles(start, end)
        return segs.isNotEmpty() && segs.all { it.isBold }
    }

    fun isRangeItalic(start: Int, end: Int): Boolean {
        if (start >= end) return false
        val segs = getSegmentStyles(start, end)
        return segs.isNotEmpty() && segs.all { it.isItalic }
    }

    fun isRangeUnderline(start: Int, end: Int): Boolean {
        if (start >= end) return false
        val segs = getSegmentStyles(start, end)
        return segs.isNotEmpty() && segs.all { it.isUnderline }
    }

    fun rangeTextColorIndex(start: Int, end: Int): Int {
        if (start >= end) return -1
        val segs = getSegmentStyles(start, end)
        if (segs.isEmpty()) return -1
        val firstColor = segs.first().textColorIndex
        return if (firstColor != -1 && segs.all { it.textColorIndex == firstColor }) firstColor else -1
    }

    private fun getSegmentStyles(start: Int, end: Int): List<StyleSpan> {
        if (start >= end) return emptyList()
        val overlapping = _spans.filter { it.start < end && it.end > start }
        if (overlapping.isEmpty()) return listOf(StyleSpan(start, end))

        val boundaries = sortedSetOf(start, end)
        for (span in overlapping) {
            if (span.start in start..end) boundaries.add(span.start)
            if (span.end in start..end) boundaries.add(span.end)
        }

        val list = boundaries.toList()
        return (0 until list.size - 1).mapNotNull { i ->
            val s = list[i]
            val e = list[i + 1]
            if (s >= e) null
            else {
                val span = overlapping.firstOrNull { it.start <= s && it.end >= e }
                span?.copy(start = s, end = e) ?: StyleSpan(s, e)
            }
        }
    }

    fun clear() {
        if (taskId == NEW_TASK_ID) {
            settings.remove(scriptStyleSpansKey(NEW_TASK_ID))
            settings.remove(scriptStyleFillKey(NEW_TASK_ID))
            _spans          = emptyList()
            _fillColorIndex = -1
        } else {
            _spans          = settings.getStringOrNull(scriptStyleSpansKey(taskId))
                ?.deserializeSpans() ?: emptyList()
            _fillColorIndex = settings.getInt(scriptStyleFillKey(taskId), -1)
        }
        lastObservedText = ""
        hasUnsavedChanges = false
        taskId = NEW_TASK_ID
    }

    private fun flushToDisk(id: Int) {
        settings[scriptStyleSpansKey(id)] = _spans.serialize()
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
fun ScriptTextStyleState.resolveActiveFillColor(): Color {
    val idx = activeFillColorIndex
    val colors = getScriptFillColors()
    return if (idx in colors.indices) colors[idx] ?: MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surface
}
