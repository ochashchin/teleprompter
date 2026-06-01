package com.example.kotlinmultiplatform

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlin.math.ceil


// ─────────────────────────────────────────────
// fontSize helper  (dp → sp, 1:1)
// ─────────────────────────────────────────────

@Composable
fun fontSize1(dpSize: Dp): TextUnit =
    with(LocalDensity.current) { dpSize.toSp() }

// ─────────────────────────────────────────────
// Result — normal sizes
// ─────────────────────────────────────────────

data class TextFitResult(
    val linesPerParent: Int,
    val parentCount:    Int,
    val pagesText:      List<String>,
)

// ─────────────────────────────────────────────
// Core calculation — normal sizes
// ─────────────────────────────────────────────

// ─────────────────────────────────────────────
// calculateTextFit — measures text and returns page chunks + fit metrics
// ─────────────────────────────────────────────

fun calculateTextFit(
    textMeasurer:   TextMeasurer,
    text:           String,
    style:          TextStyle,
    parentWidthPx:  Int,
    parentHeightPx: Int,
): TextFitResult {
    if (text.isBlank() || parentWidthPx <= 0 || parentHeightPx <= 0)
        return TextFitResult(0, 0, emptyList())

    val measured = textMeasurer.measure(
        text        = text,
        style       = style,
        constraints = Constraints(maxWidth = parentWidthPx),
        overflow    = TextOverflow.Clip,
    )

    val totalLines     = measured.lineCount
    val lineHeightPx   = measured.size.height.toFloat() / totalLines.coerceAtLeast(1)
    val linesPerParent = (parentHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
    val parentCount    = ceil(totalLines.toFloat() / linesPerParent).toInt()

    val pages = mutableListOf<String>()
    var line  = 0
    while (line < totalLines) {
        val endLine = minOf(line + linesPerParent - 1, totalLines - 1)
        pages += text.substring(measured.getLineStart(line), measured.getLineEnd(endLine))
        line  += linesPerParent
    }

    return TextFitResult(linesPerParent, parentCount, pages)
}

// ─────────────────────────────────────────────
// TextFitCalculator — invisible, normal sizes
// ─────────────────────────────────────────────

@Composable
fun TextFitCalculator(
    text:         String,
    fontSize:     TextUnit,
    lineHeight:   TextUnit,
    padding:      Dp      = 10.dp,
    isHorizontal: Boolean = false,
    modifier:     Modifier = Modifier,
    onResult:     (TextFitResult) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val density      = LocalDensity.current
    val style        = TextStyle(fontSize = fontSize, lineHeight = lineHeight)

    BoxWithConstraints(modifier = modifier) {

        val contentWidthPx = with(density) {
            (maxWidth - (padding * 2)).roundToPx().coerceAtLeast(1)
        }

        val contentHeightPx = with(density) {
            (maxHeight - (padding * 2)).roundToPx().coerceAtLeast(1)
        }

        val calcWidthPx  = if (isHorizontal) contentHeightPx else contentWidthPx
        val calcHeightPx = if (isHorizontal) contentWidthPx  else contentHeightPx

        val result = remember(
            text,
            fontSize,
            lineHeight,
            contentWidthPx,
            contentHeightPx,
            isHorizontal,
        ) {
            calculateTextFit(
                textMeasurer   = textMeasurer,
                text           = text,
                style          = style,
                parentWidthPx  = calcWidthPx,
                parentHeightPx = calcHeightPx,
            )
        }

        onResult(result)
    }
}

// ─────────────────────────────────────────────
// TextFitBox — normal sizes, cycles pages internally
//
// None   — plain multiline Text, raw alpha cut between pages (0 → 1).
//
// Print  — page alpha: 0 → 1 (same raw cut as None).
//          Per-glyph: each glyph fades in (0→1) at its natural reading time,
//          then stays visible.  Glyphs near punctuation arrive later, giving
//          a natural reading-speed stagger including punctuation pauses.
//
// Fade   — page alpha: 0 → 1 (same raw cut as None).
//          Per-glyph: fade in (0→1) → visible pause → fade out (1→0), all
//          within the glyph's own time window.  The fade wave travels across
//          the page following the reading timeline (including punctuation
//          pauses), mirroring LineFadeText.
//
// Timeline model (mirrors LineFadeText exactly):
//
//   progress [0, 1] maps to [0, holdMs].
//   Each non-space glyph at text index s has a "natural arrival time":
//     charProgress[s] = calculatePageDurationMs(text[0..s], wpm) / holdMs
//   This encodes punctuation pauses as wider gaps between glyphs.
//
//   fadeBand  = fraction of progress for one glyph's ramp (fixed visual width).
//   pauseSpan = fraction of progress for the visible phase between in and out.
//   Both are sized relative to the average glyph window (1/n) so they scale
//   with text length.
//
//   Per-glyph triggers:
//     inTrigger  = charProgress[s]
//     inEnd      = inTrigger  + fadeBand         → fully visible
//     outTrigger = inEnd      + pauseSpan         → starts fading out   (Fade only)
//     outEnd     = outTrigger + fadeBand          → invisible           (Fade only)
//
// Draw strategy — drawWithContent + stable TextLayoutResult:
//   Layout measured once per page.  draw lambda reads progress.value as
//   snapshot State → redraws, never recompositions.  All per-page constants
//   bundled into FrameDrawState, updated atomically with progress.snapTo(0f).
// ─────────────────────────────────────────────

// Per-glyph timing constants, precomputed once per page.
private data class GlyphTiming(
    val inTrigger:  Float,   // progress when glyph starts fading in
    val inEnd:      Float,   // progress when glyph is fully visible
    val outTrigger: Float,   // progress when glyph starts fading out (Fade only)
    val outEnd:     Float,   // progress when glyph is invisible      (Fade only)
)

// Immutable snapshot of everything the draw lambda needs for one page.
private data class FrameDrawState(
    val text:    String,
    val layout:  androidx.compose.ui.text.TextLayoutResult,
    val timings: List<GlyphTiming?>,   // index = string index; null for spaces
)

@Composable
fun TextFitBox(
    pages:          List<String>,
    linesPerParent: Int,
    textStyle:      TextStyle,
    wpm:            Int,
    padding:        Dp             = 10.dp,
    isHorizontal:   Boolean        = false,
    transitionMode: TransitionMode = TransitionMode.None,
    modifier:       Modifier       = Modifier,
) {
    var currentPage  by remember(pages) { mutableIntStateOf(0) }
    var alpha        by remember { mutableFloatStateOf(1f) }
    val progress     = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()

    // ── LaunchedEffect ───────────────────────────────────────────────────────
    // None:          delay(hold) → alpha cut → next page
    // Print / Fade:  alpha cut → progress 0→1 over hold → next page
    //
    // For Print/Fade hold is read AFTER currentPage increments so it uses the
    // new page's duration (the one whose glyphs are being animated).
    // ────────────────────────────────────────────────────────────────────────

    LaunchedEffect(pages, wpm, transitionMode) {
        while (true) {
            when (transitionMode) {
                TransitionMode.None -> {
                    val hold = calculatePageDurationMs(pages[currentPage], wpm).coerceAtLeast(600L)
                    delay(hold)
                    alpha = 0f
                    currentPage = (currentPage + 1) % pages.size
                    alpha = 1f
                }
                TransitionMode.Print,
                TransitionMode.Fade -> {
                    alpha = 0f
                    progress.snapTo(0f)
                    currentPage = (currentPage + 1) % pages.size
                    alpha = 1f
                    val hold = calculatePageDurationMs(pages[currentPage], wpm).coerceAtLeast(600L)
                    progress.animateTo(1f, tween(hold.toInt(), easing = LinearEasing))
                }
            }
        }
    }

    val rotatedModifier = if (isHorizontal) {
        modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth  = constraints.minHeight,
                        maxWidth  = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                layout(placeable.height, placeable.width) {
                    placeable.place(
                        x = -(placeable.width  - placeable.height) / 2,
                        y = -(placeable.height - placeable.width)  / 2,
                    )
                }
            }
            .rotate(90f)
    } else {
        modifier
    }

    val pageText = pages.getOrElse(currentPage) { pages.first() }

    BoxWithConstraints(
        modifier         = rotatedModifier
            .padding(padding)
            .fillMaxSize()
            .graphicsLayer { this.alpha = alpha },
        contentAlignment = Alignment.TopStart,
    ) {
        when (transitionMode) {
            TransitionMode.None -> Text(
                text     = pageText,
                style    = textStyle,
                maxLines = linesPerParent,
                overflow = TextOverflow.Clip,
                modifier = Modifier.fillMaxSize(),
            )

            TransitionMode.Print,
            TransitionMode.Fade -> {
                val contentColor   = textStyle.color.takeOrElse { LocalContentColor.current }
                val density        = LocalDensity.current
                val contentWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }

                // ── DrawState ────────────────────────────────────────────────────
                //
                // charProgress[s]: the natural arrival time (in [0,1]) of the glyph
                // at string index s, proportional to how long it takes to read up to
                // that point (including punctuation pauses before it).
                //
                // Average glyph window = 1/n where n = non-space count.
                //
                // fadeBand  = 30% of average glyph window — the ramp width.
                // pauseSpan = 40% of average glyph window — guaranteed visible phase.
                //
                // These ratios mirror LineFadeText:
                //   staggerSpan = fadeWindow * 0.60  →  ramp + stagger fills 60%
                //   fadeBand    = staggerSpan / 2    →  ramp is half the stagger span
                //   visiblePause ≈ fadeWindow * 0.70 →  char visible for 70% of its window
                // ─────────────────────────────────────────────────────────────────

                val drawState = remember(pageText, textStyle, contentWidthPx, linesPerParent, wpm) {
                    val layout = textMeasurer.measure(
                        text        = pageText,
                        style       = textStyle,
                        constraints = Constraints(maxWidth = contentWidthPx),
                        overflow    = TextOverflow.Clip,
                        maxLines    = linesPerParent,
                    )

                    val holdMs    = calculatePageDurationMs(pageText, wpm).coerceAtLeast(1L).toFloat()
                    val msPerWord = if (wpm > 0) 60_000f / wpm else 500f

                    // ── Per-letter arrival time ───────────────────────────────────
                    //
                    // Split pageText into tokens (words + punctuation pauses).
                    // Within each word, letters are distributed evenly across the
                    // word's time slice so every letter gets its own trigger time.
                    //
                    // Word[k] occupies [wordStartMs[k], wordStartMs[k] + msPerWord).
                    // Letter j of wordLen letters within that word:
                    //   arrival_ms = wordStartMs + (j / wordLen) * msPerWord
                    //
                    // Punctuation pauses (after the char) shift wordStartMs forward
                    // exactly as calculatePageDurationMs does, keeping the total
                    // duration consistent.
                    //
                    // arrival ∈ [0, 1] = arrival_ms / holdMs
                    // ─────────────────────────────────────────────────────────────

                    // First pass: collect (stringIndex, arrival_ms) for every non-space char
                    data class CharArrival(val index: Int, val arrivalMs: Float)
                    val arrivals = ArrayList<CharArrival>(pageText.length)

                    var wordStartMs = 0f
                    var wordStartIdx = -1   // string index of first char of current word

                    fun flushWord(wordEndIdx: Int) {
                        if (wordStartIdx < 0) return
                        val wordChars = pageText.substring(wordStartIdx, wordEndIdx + 1)
                            .filter { it != ' ' }
                        val wLen = wordChars.length.coerceAtLeast(1)
                        var charPos = 0
                        for (si in wordStartIdx..wordEndIdx) {
                            val ch = pageText[si]
                            if (ch == ' ') continue
                            val ms = wordStartMs + (charPos.toFloat() / wLen) * msPerWord
                            arrivals.add(CharArrival(si, ms))
                            charPos++
                        }
                        wordStartMs += msPerWord
                        wordStartIdx = -1
                    }

                    pageText.forEachIndexed { si, ch ->
                        when {
                            ch == ' ' -> {
                                flushWord(si - 1)
                            }
                            else -> {
                                if (wordStartIdx < 0) wordStartIdx = si
                                // Punctuation pause added AFTER the word (post-char)
                                // — accumulate into wordStartMs after flush
                            }
                        }
                        // Punctuation shifts the timeline after this character's word
                        if (ch != ' ') {
                            val pause = when (ch) {
                                '.', '!', '?' -> 300f
                                ',', ';', ':'  -> 200f
                                else           -> 0f
                            }
                            if (pause > 0f) {
                                flushWord(si)
                                wordStartMs += pause
                            }
                        }
                    }
                    // Flush last word (no trailing space)
                    if (wordStartIdx >= 0) flushWord(pageText.lastIndex)

                    // ── Timing constants ─────────────────────────────────────────
                    //
                    // fadeBand  — how long one letter's ramp takes in progress units.
                    //             Sized to ~2 letters' worth of time so the wave looks
                    //             smooth: wide enough to overlap with neighbours.
                    //
                    // pauseSpan — how long a letter stays fully visible before fading
                    //             out.  Sized to ~3 letters' worth so there is a clear
                    //             visible phase between the in and out ramps.
                    //
                    // budget    — scale arrivals so the last letter's full arc ends at 1.

                    val n         = arrivals.size.coerceAtLeast(1)
                    val letterSlice = 1f / n           // average progress per letter
                    val fadeBand    = letterSlice * 10f  // ramp spans ≈ 2 letters
                    val pauseSpan   = letterSlice * 10f  // visible phase spans ≈ 3 letters
                    val budget      = 1f - fadeBand * 2f - pauseSpan

                    // Build the final timings list (index = string index, null = space)
                    val timings = arrayOfNulls<GlyphTiming>(pageText.length)
                    for (ca in arrivals) {
                        val raw     = (ca.arrivalMs / holdMs).coerceIn(0f, 1f)
                        val inTrig  = raw * budget
                        val inEnd   = inTrig  + fadeBand
                        val outTrig = inEnd   + pauseSpan
                        val outEnd  = outTrig + fadeBand
                        timings[ca.index] = GlyphTiming(inTrig, inEnd, outTrig, outEnd)
                    }

                    FrameDrawState(pageText, layout, timings.toList())
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val p  = progress.value
                            val ds = drawState

                            ds.text.forEachIndexed { stringIndex, char ->
                                val timing = ds.timings.getOrNull(stringIndex) ?: return@forEachIndexed

                                val glyphAlpha: Float = if (transitionMode == TransitionMode.Print) {
                                    // Fade in, then stay visible
                                    when {
                                        p <= timing.inTrigger -> 0f
                                        p >= timing.inEnd     -> 1f
                                        else -> (p - timing.inTrigger) /
                                                (timing.inEnd - timing.inTrigger).coerceAtLeast(0.0001f)
                                    }
                                } else {
                                    // Fade in → visible pause → fade out
                                    val inAlpha = when {
                                        p <= timing.inTrigger -> 0f
                                        p >= timing.inEnd     -> 1f
                                        else -> (p - timing.inTrigger) /
                                                (timing.inEnd - timing.inTrigger).coerceAtLeast(0.0001f)
                                    }
                                    val outAlpha = when {
                                        p <= timing.outTrigger -> 1f
                                        p >= timing.outEnd     -> 0f
                                        else -> 1f - (p - timing.outTrigger) /
                                                (timing.outEnd - timing.outTrigger).coerceAtLeast(0.0001f)
                                    }
                                    minOf(inAlpha, outAlpha)
                                }

                                val bounds = ds.layout.getBoundingBox(stringIndex)
                                drawContext.canvas.save()
                                drawContext.canvas.clipRect(bounds)
                                drawText(
                                    textLayoutResult = ds.layout,
                                    color            = contentColor.copy(alpha = glyphAlpha),
                                )
                                drawContext.canvas.restore()
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun TextVerticalScrollBox(
    totalDurationMs: Long,
    text:            String,
    textStyle:       TextStyle,
    isHorizontal:    Boolean,
    padding:         Dp             = 10.dp,
    transitionMode:  TransitionMode = TransitionMode.None,
    modifier:        Modifier       = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val containerHeightPx =
            if (isHorizontal) constraints.maxWidth.toFloat() else constraints.maxHeight.toFloat()

        var textHeightPx by remember { mutableFloatStateOf(0f) }
        val offsetAnim = remember { Animatable(0f) }
        var alpha by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(textHeightPx, totalDurationMs) {
            if (textHeightPx <= 0f) return@LaunchedEffect

            while (true) {
                val start = containerHeightPx
                val end = textHeightPx + containerHeightPx
                val totalDistance = start + end

                val startAnim = containerHeightPx + (containerHeightPx / 2f)
                val endAnim = textHeightPx + (containerHeightPx / 2f)

                offsetAnim.snapTo(start)
                alpha = 1f

                var passedStartAnim = false
                var passedEndAnim = false

                offsetAnim.animateTo(
                    targetValue = -end,
                    animationSpec = tween(
                        durationMillis = totalDurationMs.toInt(),
                        easing = LinearEasing
                    )
                ) {
                    val currentOffset = this.value

                    val progressFraction = ((start - currentOffset) / totalDistance).coerceIn(0f, 1f)
                    val playTimeMs = (progressFraction * totalDurationMs).toLong()

                    if (!passedStartAnim && currentOffset <= startAnim) {
                        passedStartAnim = true
                        println("Crossed startAnim ($startAnim px) at $playTimeMs ms")
                        // Execute your custom enter/start logic here
                    }

                    if (!passedEndAnim && currentOffset <= -endAnim) {
                        passedEndAnim = true
                        println("Crossed endAnim (-$endAnim px) at $playTimeMs ms")
                        // Execute your custom leave/end logic here
                    }
                }
            }
        }

        DisposableEffect(text, isHorizontal, totalDurationMs) {
            onDispose {
                alpha = 0f
            }
        }

        val rotatedModifier =
            if (isHorizontal) {
                Modifier
                    .layout { measurable, constraints ->
                        val placeable = measurable.measure(
                            constraints.copy(
                                minWidth = constraints.minHeight,
                                maxWidth = constraints.maxHeight,
                                minHeight = constraints.minWidth,
                                maxHeight = constraints.maxWidth,
                            )
                        )
                        layout(placeable.height, placeable.width) {
                            placeable.place(
                                x = -(placeable.width - placeable.height) / 2,
                                y = -(placeable.height - placeable.width) / 2,
                            )
                        }
                    }
                    .rotate(90f)
            } else {
                Modifier
            }

        Box(
            modifier = rotatedModifier
                .padding(padding)
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
            contentAlignment = Alignment.TopCenter
        ) {
            Text(
                text = text,
                style = textStyle,
                overflow = TextOverflow.Clip,
                modifier = Modifier
                    .wrapContentHeight(
                        unbounded = true,
                        align = Alignment.Top
                    )
                    .graphicsLayer {
                        translationY = offsetAnim.value
                    }
                    .onGloballyPositioned { coordinates ->
                        val measuredHeight = coordinates.size.height.toFloat()
                        if (measuredHeight != textHeightPx) textHeightPx = measuredHeight
                    }
            )
        }
    }
}

// ─────────────────────────────────────────────
// TextHorizontalScrollBox
// Box(clipToBounds) → Row(offset) → Spacer / page content / Spacer
// Spacers equal parent size so content scrolls fully in from right, out left.
// If parentCount >= 2, full text is rendered twice for a seamless loop.
// In isHorizontal (rotated) mode the box is rotated 90° like TextFitBox.
// ─────────────────────────────────────────────

@Composable
fun TextHorizontalScrollBox(
    totalDurationMs: Long,
    text:            String,
    textStyle:       TextStyle,
    transitionMode:  TransitionMode,
    isHorizontal:    Boolean,
    padding:         Dp       = 10.dp,
    modifier:        Modifier = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()   // must be at composable top level

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val containerWidthPx =
            if (isHorizontal) constraints.maxHeight.toFloat() else constraints.maxWidth.toFloat()

        var textWidthPx by remember { mutableFloatStateOf(0f) }
        val offsetAnim  = remember { Animatable(0f) }
        var alpha       by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(textWidthPx, totalDurationMs) {
            if (textWidthPx <= 0f) return@LaunchedEffect
            while (true) {
                val start = containerWidthPx
                val end   = textWidthPx + containerWidthPx
                offsetAnim.snapTo(start)
                alpha = 1f
                offsetAnim.animateTo(
                    targetValue   = -end,
                    animationSpec = tween(totalDurationMs.toInt(), easing = LinearEasing)
                )
            }
        }

        DisposableEffect(text, isHorizontal, totalDurationMs) { onDispose { alpha = 0f } }

        val rotatedModifier = if (isHorizontal) {
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth  = constraints.minHeight,
                            maxWidth  = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth,
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(
                            x = -(placeable.width  - placeable.height) / 2,
                            y = -(placeable.height - placeable.width)  / 2,
                        )
                    }
                }
                .rotate(90f)
        } else {
            Modifier
        }

        Box(
            modifier         = rotatedModifier
                .padding(padding)
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
            contentAlignment = Alignment.CenterStart,
        ) {
            when (transitionMode) {
                TransitionMode.Fade,
                TransitionMode.Print -> {
                    val measured = remember(text, textStyle) {
                        textMeasurer.measure(
                            text  = text,
                            style = textStyle,
                        )
                    }

                    val n = text.length.coerceAtLeast(1)

                    val measuredWidth = measured
                        .getHorizontalPosition(n, true)
                        .coerceAtLeast(1f)

                    val widthScale = textWidthPx.coerceAtLeast(1f) / measuredWidth

                    val letterOffsets: List<Pair<Float, Float>> =
                        remember(measured, containerWidthPx, textWidthPx) {

                            val fadeIns = (0 until n).map { i ->

                                val leftX = measured.getHorizontalPosition(i, true)

                                val rightX = measured.getHorizontalPosition(i + 1, true)

                                val midX = ((leftX + rightX) / 2f) * widthScale

                                containerWidthPx / 2f - midX
                            }

                            fadeIns.mapIndexed { i, fi ->
                                val next =
                                    if (i + 1 < n) fadeIns[i + 1]
                                    else fi

                                fi to next
                            }
                        }

                    // Use ACTUAL rendered width for timing
                    val totalTextWidth =
                        textWidthPx.coerceAtLeast(1f)

                    val avgCharWidth =
                        (totalTextWidth / n)
                            .coerceAtLeast(1f)

                    val fadeWindowChars = 10
                    val fadeBand =
                        avgCharWidth * fadeWindowChars

                    Row(
                        modifier = Modifier
                            .wrapContentWidth(
                                unbounded = true,
                                align = Alignment.Start
                            )
                            .graphicsLayer {
                                translationX = offsetAnim.value
                            }
                            .onGloballyPositioned { coords ->
                                val w = coords.size.width.toFloat()
                                if (w != textWidthPx)
                                    textWidthPx = w
                            }
                    ) {

                        text.forEachIndexed { index, char ->

                            val (fadeInOffset, _) =
                                letterOffsets.getOrElse(index) {
                                    Pair(0f, 0f)
                                }

                            val fadeInStart = fadeInOffset + fadeBand

                            val fadeInEnd = fadeInOffset

                            val fadeOutStart = fadeInOffset

                            val fadeOutEnd = fadeInOffset - fadeBand

                            val display =
                                if (char == ' ')
                                    "\u00A0"
                                else
                                    char.toString()

                            Text(
                                text = display,
                                style = textStyle,
                                maxLines = 1,
                                overflow = TextOverflow.Clip,
                                modifier = Modifier.graphicsLayer {

                                    if (char == ' ') {
                                        this.alpha = 0f
                                        return@graphicsLayer
                                    }

                                    val offset = offsetAnim.value

                                    val inAlpha = when {
                                        offset >= fadeInStart -> 0f
                                        offset <= fadeInEnd -> 1f
                                        else ->
                                            1f - (
                                                    (offset - fadeInEnd)
                                                            / fadeBand
                                                    )
                                    }

                                    this.alpha =
                                        if (
                                            transitionMode ==
                                            TransitionMode.Print
                                        ) {
                                            inAlpha
                                        } else {

                                            val outAlpha = when {
                                                offset >= fadeOutStart -> 1f
                                                offset <= fadeOutEnd -> 0f
                                                else ->
                                                    (
                                                            offset - fadeOutEnd
                                                            ) / fadeBand
                                            }

                                            minOf(inAlpha, outAlpha)
                                        }
                                }
                            )
                        }
                    }
                }
                TransitionMode.None -> {
                    Text(
                        text     = text,
                        maxLines = 1,
                        style    = textStyle,
                        modifier = Modifier
                            .wrapContentWidth(unbounded = true, align = Alignment.Start)
                            .graphicsLayer { translationX = offsetAnim.value }
                            .onGloballyPositioned { coords ->
                                val w = coords.size.width.toFloat()
                                if (w != textWidthPx) textWidthPx = w
                            }
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────
// ScrollLineData
//
// Geometry of one visual line within the full text block.
// All offsets are in the same coordinate space as offsetAnim:
//   offsetAnim starts at +start (text below viewport) and moves to -end (text above).
//
// Fade fires when the line's midpoint crosses containerHeightPx/2 (viewport centre).
//   fadeInOffset  = containerHeightPx/2 - lineMidPx  (entering from below)
//   nextFadeInOffset = next line's fadeInOffset       (seamless handoff)
// ─────────────────────────────────────────────

data class ScrollLineData(
    val lineText:          String,
    val lineTopPx:         Float,
    val lineBottomPx:      Float,
    val fadeInOffset:      Float,   // offsetAnim when this line starts fading in
    val fadeOutOffset:     Float,   // offsetAnim when this line finishes fading out (geometry fallback for last line)
    val nextFadeInOffset:  Float,   // fadeInOffset of the next line — fade-out of this line ends here
)

// ─────────────────────────────────────────────
// TextCentreVerticalScrollBox
//
// Single constant-velocity animateTo — identical structure to TextHorizontalScrollBox:
//   start = +containerHeightPx   (text enters from below)
//   end   = textHeightPx + containerHeightPx  (text fully exits above)
//
// transitionMode selects Fade / Print letter-alpha animation or plain None scroll.
// ─────────────────────────────────────────────

@Composable
fun TextCentreVerticalScrollBox(
    pages:          List<String>,
    wpm:            Int,
    textStyle:      TextStyle,
    isHorizontal:   Boolean,
    transitionMode: TransitionMode = TransitionMode.None,
    padding:        Dp             = 10.dp,
    modifier:       Modifier       = Modifier,
) {
    val textMeasurer = rememberTextMeasurer()

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val containerHeightPx =
            if (isHorizontal) constraints.maxWidth.toFloat()
            else              constraints.maxHeight.toFloat()
        val containerWidthPx  =
            if (isHorizontal) constraints.maxHeight.toFloat()
            else              constraints.maxWidth.toFloat()

        val density         = LocalDensity.current
        val paddingPx       = with(density) { padding.toPx() }
        val measuredWidthPx = (containerWidthPx - paddingPx * 2).toInt().coerceAtLeast(1)

        var textHeightPx by remember { mutableFloatStateOf(0f) }
        val offsetAnim   = remember { Animatable(0f) }
        var alpha        by remember { mutableFloatStateOf(0f) }

        val fullText = remember(pages) { pages.joinToString(" ") }

        // Measure every visual line. Compute fade window per line using the reading region
        // centred exactly on containerHeightPx/2.
        val lineDataList: List<ScrollLineData> = remember(
            fullText, textStyle, measuredWidthPx, containerHeightPx
        ) {
            if (fullText.isBlank() || measuredWidthPx <= 0 || containerHeightPx <= 0f)
                return@remember emptyList()

            val measured = textMeasurer.measure(
                text        = fullText,
                style       = textStyle,
                constraints = Constraints(maxWidth = measuredWidthPx),
                overflow    = TextOverflow.Clip,
            )

            // the viewport centre (containerHeightPx / 2).
            //
            // screenMid  = offsetAnim + lineMidPx
            //
            // fadeInOffset:  screenMid == containerHeightPx/2  entering from below
            //   ∴ fadeInOffset = containerHeightPx/2 - lineMidPx
            //
            // fadeOutOffset for last line (no next line): same formula, used as fallback.
            val raw = (0 until measured.lineCount).map { idx ->
                val start        = measured.getLineStart(idx)
                val end          = measured.getLineEnd(idx, visibleEnd = true)
                    .coerceIn(start, fullText.length)
                val lineText     = fullText.substring(start, end).trimEnd()
                val lineTopPx    = measured.getLineTop(idx)
                val lineBottomPx = measured.getLineBottom(idx)
                val lineMidPx    = (lineTopPx + lineBottomPx) / 2f

                val fadeInOffset  = containerHeightPx / 2f - lineMidPx
                val fadeOutOffset = fadeInOffset   // same point — used only as last-line fallback

                ScrollLineData(lineText, lineTopPx, lineBottomPx, fadeInOffset, fadeOutOffset,
                    nextFadeInOffset = fadeOutOffset)
            }

            // Second pass: nextFadeInOffset of line N = fadeInOffset of line N+1.
            // Fade-out of line N ends exactly when fade-in of line N+1 begins.
            raw.mapIndexed { idx, ld ->
                val nextFadeIn = raw.getOrNull(idx + 1)?.fadeInOffset ?: ld.fadeOutOffset
                ld.copy(nextFadeInOffset = nextFadeIn)
            }
        }

        // Pixel velocity derived from wpm: totalDurationMs scales with total scroll distance
        // so the reading speed in words/min matches wpm regardless of text length.
        val totalDurationMs: Long = remember(textHeightPx, containerHeightPx, wpm, fullText) {
            if (textHeightPx <= 0f || containerHeightPx <= 0f) return@remember 3000L
            val textDurationMs = calculatePageDurationMs(fullText, wpm).coerceAtLeast(1000L)
            val totalDistance  = textHeightPx + containerHeightPx * 2f
            (textDurationMs * totalDistance / textHeightPx.coerceAtLeast(1f)).toLong()
        }

        // Single constant-velocity animateTo — same structure as TextHorizontalScrollBox.
        // start/end match exactly: text enters from below, exits above.
        LaunchedEffect(textHeightPx, totalDurationMs, containerHeightPx) {
            if (textHeightPx <= 0f) return@LaunchedEffect

            while (true) {
                val start = containerHeightPx
                val end   = textHeightPx + containerHeightPx

                offsetAnim.snapTo(start)
                alpha = 1f

                offsetAnim.animateTo(
                    targetValue   = -end,
                    animationSpec = tween(
                        durationMillis = totalDurationMs.toInt(),
                        easing         = LinearEasing,
                    )
                )

                alpha = 0f
            }
        }

        DisposableEffect(fullText, isHorizontal) { onDispose { alpha = 0f } }

        val rotatedModifier = if (isHorizontal) {
            Modifier
                .layout { measurable, constraints ->
                    val placeable = measurable.measure(
                        constraints.copy(
                            minWidth  = constraints.minHeight,
                            maxWidth  = constraints.maxHeight,
                            minHeight = constraints.minWidth,
                            maxHeight = constraints.maxWidth,
                        )
                    )
                    layout(placeable.height, placeable.width) {
                        placeable.place(
                            x = -(placeable.width  - placeable.height) / 2,
                            y = -(placeable.height - placeable.width)  / 2,
                        )
                    }
                }
                .rotate(90f)
        } else {
            Modifier
        }

        Box(
            modifier         = rotatedModifier
                .padding(padding)
                .fillMaxSize()
                .graphicsLayer { this.alpha = alpha },
            contentAlignment = Alignment.TopCenter,
        ) {
            val scrollModifier = Modifier
                .wrapContentHeight(unbounded = true, align = Alignment.Top)
                .graphicsLayer { translationY = offsetAnim.value }
                .onGloballyPositioned { coordinates ->
                    val h = coordinates.size.height.toFloat()
                    if (h != textHeightPx) textHeightPx = h
                }

            when (transitionMode) {
                TransitionMode.Fade -> ScrollFadeText(
                    pages      = pages,
                    wpm        = wpm,
                    textStyle  = textStyle,
                    lines      = lineDataList,
                    offsetAnim = offsetAnim,
                    modifier   = scrollModifier,
                )
                TransitionMode.Print -> ScrollPrintText(
                    pages      = pages,
                    wpm        = wpm,
                    textStyle  = textStyle,
                    lines      = lineDataList,
                    offsetAnim = offsetAnim,
                    modifier   = scrollModifier,
                )
                TransitionMode.None -> Text(
                    text     = fullText,
                    style    = textStyle,
                    overflow = TextOverflow.Clip,
                    modifier = scrollModifier,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// ScrollFadeText
// ─────────────────────────────────────────────

@Composable
fun ScrollFadeText(
    pages:      List<String>,
    wpm:        Int,
    textStyle:  TextStyle,
    lines:      List<ScrollLineData>,
    offsetAnim: Animatable<Float, *>,
    modifier:   Modifier = Modifier,
) {
    val fullText = remember(pages) { pages.joinToString(" ") }

    Box(modifier = modifier) {
        if (lines.isEmpty()) {
            Text(text = fullText, style = textStyle,
                modifier = Modifier.graphicsLayer { alpha = 0f })
            return@Box
        }
        Column {
            lines.forEach { lineData ->
                LineFadeText(
                    lineData   = lineData,
                    offsetAnim = offsetAnim,
                    fadeOut    = true,
                    textStyle  = textStyle,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// ScrollPrintText
// ─────────────────────────────────────────────

@Composable
fun ScrollPrintText(
    pages:      List<String>,
    wpm:        Int,
    textStyle:  TextStyle,
    lines:      List<ScrollLineData>,
    offsetAnim: Animatable<Float, *>,
    modifier:   Modifier = Modifier,
) {
    val fullText = remember(pages) { pages.joinToString(" ") }

    Box(modifier = modifier) {
        if (lines.isEmpty()) {
            Text(text = fullText, style = textStyle,
                modifier = Modifier.graphicsLayer { alpha = 0f })
            return@Box
        }
        Column {
            lines.forEach { lineData ->
                LineFadeText(
                    lineData   = lineData,
                    offsetAnim = offsetAnim,
                    fadeOut    = false,
                    textStyle  = textStyle,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────
// LineFadeText — internal
//
// Alpha is computed directly from offsetAnim.value each frame inside graphicsLayer —
// no LaunchedEffect, no snapshotFlow, no per-letter Animatable.
// This guarantees perfect synchronisation with the scroll and correct cycling.
//
// For each letter at position index/letterCount within the line:
//
//   The full fade window spans fadeInOffset → fadeOutOffset (offsetAnim decreasing).
//   fadeWindowSize = fadeInOffset - fadeOutOffset
//
//   Each letter's personal fade-in starts at:
//     letterFadeIn = fadeInOffset - (fadeWindowSize * index / letterCount) * staggerFraction
//   and rises over fadeBand pixels of scroll travel.
//
//   For fadeOut=true, fade-out mirrors this from fadeOutOffset upward.
//   For fadeOut=false (Print), letter stays at alpha=1 once reached.
// ─────────────────────────────────────────────

// ─────────────────────────────────────────────
// LineFadeText — internal
//
// Alpha computed from offsetAnim.value each frame in graphicsLayer.
// offsetAnim decreases over time (text scrolls upward).
//
// Trigger point: line midpoint crossing the viewport centre.
//
// Both fade-in and fade-out stagger first→last (index 0 leads):
//   fade-in  letter[i] starts at: fadeInOffset      - staggerSpan * i / n
//   fade-out letter[i] starts at: nextFadeInOffset  - staggerSpan * i / n
//
// fade-out of line N starts exactly when fade-in of line N+1 starts —
// seamless handoff, no gap, no overlap.
// ─────────────────────────────────────────────

@Composable
private fun LineFadeText(
    lineData:   ScrollLineData,
    offsetAnim: Animatable<Float, *>,
    fadeOut:    Boolean,
    textStyle:  TextStyle,
) {
    if (lineData.lineText.isEmpty()) return

    val n           = lineData.lineText.length.coerceAtLeast(1)
    val fadeWindow  = lineData.fadeInOffset - lineData.nextFadeInOffset  // total px span for this line
    val staggerSpan = (fadeWindow * 0.60f).coerceAtLeast(1f)
    val fadeBand    = (staggerSpan / 2f).coerceAtLeast(1f)

    Row {
        lineData.lineText.forEachIndexed { index, char ->

            // Fade-in:  letter[i] starts at fadeInOffset - staggerSpan * i / n  (0 leads)
            val fadeInStart = lineData.fadeInOffset - staggerSpan * index / n
            val fadeInEnd   = fadeInStart - fadeBand

            // Fade-out: letter[i] starts at nextFadeInOffset - staggerSpan * i / n  (0 leads, same direction)
            val fadeOutStart = lineData.nextFadeInOffset - staggerSpan * index / n
            val fadeOutEnd   = fadeOutStart - fadeBand

            val display = if (char == ' ') "\u00A0" else char.toString()
            Text(
                text     = display,
                style    = textStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.graphicsLayer {
                    if (char == ' ') { alpha = 0f; return@graphicsLayer }

                    val offset = offsetAnim.value

                    // Fade-in alpha: 0 before fadeInStart, ramps to 1 by fadeInEnd
                    val inAlpha = when {
                        offset >= fadeInStart -> 0f
                        offset <= fadeInEnd   -> 1f
                        else -> 1f - (offset - fadeInEnd) / fadeBand
                    }

                    alpha = if (!fadeOut) {
                        inAlpha
                    } else {
                        // Fade-out alpha: 1 before fadeOutStart, ramps to 0 by fadeOutEnd
                        val outAlpha = when {
                            offset >= fadeOutStart -> 1f
                            offset <= fadeOutEnd   -> 0f
                            else -> (offset - fadeOutEnd) / fadeBand
                        }
                        minOf(inAlpha, outAlpha)
                    }
                },
            )
        }
    }
}

// ─────────────────────────────────────────────


// ─────────────────────────────────────────────
// calculatePageDurationMs
// ─────────────────────────────────────────────

fun calculatePageDurationMs(pageText: String, wpm: Int): Long {
    if (pageText.isBlank() || wpm <= 0) return 0L
    val wordCount = pageText.trim().split(Regex("\\s+")).size
    val baseMs    = wordCount * 60_000L / wpm
    val pauseMs   = pageText.sumOf { ch ->
        when (ch) { '.', '!', '?' -> 300L; ',', ';', ':' -> 200L; else -> 0L }
    }
    return baseMs + pauseMs
}



private const val WPM_NORMAL = 130

private const val PREVIEW_TEXT =
    "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog. " +
            "The quick brown fox jumps over the lazy dog."

internal val NORMAL_SIZES = listOf(
    Triple("Small", 16.dp, 21.dp),
    Triple("Normal", 24.dp, 27.dp),
    Triple("Large", 32.dp, 35.dp),
    Triple("Huge", 40.dp, 45.dp),
    Triple("Massive", 56.dp, 68.dp),
)

// ─────────────────────────────────────────────
// SizeSection — one labelled group in the preview
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NormalSizeSection(
    fontSizeDp: Dp,
    lineHeightDp: Dp,
) {
    val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
        fontSize   = fontSize1(fontSizeDp),
        lineHeight = fontSize1(lineHeightDp),
    )
    var result by remember { mutableStateOf<TextFitResult?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
    ) {

        // Invisible calculator — drives onResult
        TextFitCalculator(
            text       = PREVIEW_TEXT,
            fontSize   = textStyle.fontSize,
            lineHeight = textStyle.lineHeight,
            padding    = 10.dp,
            modifier   = Modifier.width(180.dp).height(380.dp),
            onResult   = { result = it }
        )

        // Pages — horizontal row, each cell shows one page cycling on its own
        result?.let { r ->
            Row {
                r.pagesText.forEachIndexed { idx, pageText ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text  = "${idx + 1}/${r.parentCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                        TextFitBox(
                            pages          = listOf(pageText),
                            linesPerParent = r.linesPerParent,
                            textStyle      = textStyle,
                            wpm            = WPM_NORMAL,
                            padding        = 10.dp,
                            modifier       = Modifier.width(180.dp).height(380.dp),
                        )
                    }
                }
            }
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 900,
    heightDp = 420,
    name = "Small"
)
@Composable
fun PreviewSmall() {
    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            NormalSizeSection(
                fontSizeDp = 16.dp,
                lineHeightDp = 21.dp
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 900,
    heightDp = 420,
    name = "Normal"
)
@Composable
fun PreviewNormal() {
    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            NormalSizeSection(
                fontSizeDp = 24.dp,
                lineHeightDp = 27.dp
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 900,
    heightDp = 420,
    name = "Large"
)
@Composable
fun PreviewLarge() {
    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            NormalSizeSection(
                fontSizeDp = 32.dp,
                lineHeightDp = 35.dp
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 900,
    heightDp = 420,
    name = "Huge"
)
@Composable
fun PreviewHuge() {
    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            NormalSizeSection(
                fontSizeDp = 40.dp,
                lineHeightDp = 45.dp
            )
        }
    }
}

@Preview(
    showBackground = true,
    widthDp = 900,
    heightDp = 420,
    name = "Massive"
)
@Composable
fun PreviewMassive() {
    AppTheme {
        Box(
            modifier = Modifier.fillMaxSize()
        ) {
            NormalSizeSection(
                fontSizeDp = 56.dp,
                lineHeightDp = 68.dp
            )
        }
    }
}

// ─────────────────────────────────────────────
// Preview: TextVerticalScrollBox
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 200, name = "VerticalScroll – None")
@Composable
fun PreviewVerticalScrollBox() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
        val wpm   = WPM_NORMAL
        val pages = listOf(
            PREVIEW_TEXT.take(120),
            PREVIEW_TEXT.takeLast(120),
        )
        val totalDurationMs = pages.sumOf { calculatePageDurationMs(it, wpm) } * 2L
        TextVerticalScrollBox(
            totalDurationMs = totalDurationMs,
            text            = pages.joinToString(" "),
            textStyle       = textStyle,
            isHorizontal    = false,
            transitionMode  = TransitionMode.None,
            modifier        = Modifier.fillMaxSize(),
        )
    }
}

// ─────────────────────────────────────────────
// Preview: TextHorizontalScrollBox
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 200, name = "HorizontalScroll – None")
@Composable
fun PreviewHorizontalScrollBox() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
        val wpm   = WPM_NORMAL
        val pages = listOf(
            PREVIEW_TEXT.take(120),
            PREVIEW_TEXT.takeLast(120),
        )
        val totalDurationMs = pages.sumOf { calculatePageDurationMs(it, wpm) } * 2L
        TextHorizontalScrollBox(
            totalDurationMs = totalDurationMs,
            text            = pages.joinToString(" ").replace("\n", ""),
            textStyle       = textStyle,
            transitionMode  = TransitionMode.None,
            isHorizontal    = false,
            modifier        = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 200, name = "HorizontalScroll – Fade")
@Composable
fun PreviewHorizontalScrollBoxFade() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
        val wpm   = WPM_NORMAL
        val pages = listOf(
            PREVIEW_TEXT.take(120),
            PREVIEW_TEXT.takeLast(120),
        )
        val totalDurationMs = pages.sumOf { calculatePageDurationMs(it, wpm) } * 2L
        TextHorizontalScrollBox(
            totalDurationMs = totalDurationMs,
            text            = pages.joinToString(" ").replace("\n", ""),
            textStyle       = textStyle,
            transitionMode  = TransitionMode.Fade,
            isHorizontal    = false,
            modifier        = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 200, name = "HorizontalScroll – Print")
@Composable
fun PreviewHorizontalScrollBoxPrint() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
        val wpm   = WPM_NORMAL
        val pages = listOf(
            PREVIEW_TEXT.take(120),
            PREVIEW_TEXT.takeLast(120),
        )
        val totalDurationMs = pages.sumOf { calculatePageDurationMs(it, wpm) } * 2L
        TextHorizontalScrollBox(
            totalDurationMs = totalDurationMs,
            text            = pages.joinToString(" ").replace("\n", ""),
            textStyle       = textStyle,
            transitionMode  = TransitionMode.Print,
            isHorizontal    = false,
            modifier        = Modifier.fillMaxSize(),
        )
    }
}

// ─────────────────────────────────────────────
// Preview: TextCentreVerticalScrollBox
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 200, name = "CentreVerticalScroll – None")
@Composable
fun PreviewCentreVerticalScrollNone() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
        TextCentreVerticalScrollBox(
            pages          = listOf(PREVIEW_TEXT.take(120), PREVIEW_TEXT.takeLast(120)),
            wpm            = WPM_NORMAL,
            textStyle      = textStyle,
            isHorizontal   = false,
            transitionMode = TransitionMode.None,
            modifier       = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 200, name = "CentreVerticalScroll – Fade")
@Composable
fun PreviewCentreVerticalScrollFade() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
        TextCentreVerticalScrollBox(
            pages          = listOf(PREVIEW_TEXT.take(120), PREVIEW_TEXT.takeLast(120)),
            wpm            = WPM_NORMAL,
            textStyle      = textStyle,
            isHorizontal   = false,
            transitionMode = TransitionMode.Fade,
            modifier       = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 200, name = "CentreVerticalScroll – Print")
@Composable
fun PreviewCentreVerticalScrollPrint() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
        TextCentreVerticalScrollBox(
            pages          = listOf(PREVIEW_TEXT.take(120), PREVIEW_TEXT.takeLast(120)),
            wpm            = WPM_NORMAL,
            textStyle      = textStyle,
            isHorizontal   = false,
            transitionMode = TransitionMode.Print,
            modifier       = Modifier.fillMaxSize(),
        )
    }
}

// ─────────────────────────────────────────────
// Preview: TextFitBox
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 180, name = "TextFitBox – None")
@Composable
fun PreviewTextFitBoxNone() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
        TextFitBox(
            pages          = listOf(PREVIEW_TEXT.take(200), PREVIEW_TEXT.takeLast(200)),
            linesPerParent = 3,
            textStyle      = textStyle,
            wpm            = WPM_NORMAL,
            transitionMode = TransitionMode.None,
            modifier       = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 180, name = "TextFitBox – Fade")
@Composable
fun PreviewTextFitBoxFade() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
        TextFitBox(
            pages          = listOf(PREVIEW_TEXT.take(200), PREVIEW_TEXT.takeLast(200)),
            linesPerParent = 3,
            textStyle      = textStyle,
            wpm            = WPM_NORMAL,
            transitionMode = TransitionMode.Fade,
            modifier       = Modifier.fillMaxSize(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 180, name = "TextFitBox – Print")
@Composable
fun PreviewTextFitBoxPrint() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
        TextFitBox(
            pages          = listOf(PREVIEW_TEXT.take(200), PREVIEW_TEXT.takeLast(200)),
            linesPerParent = 3,
            textStyle      = textStyle,
            wpm            = WPM_NORMAL,
            transitionMode = TransitionMode.Print,
            modifier       = Modifier.fillMaxSize(),
        )
    }
}