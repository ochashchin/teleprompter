package com.example.kotlinmultiplatform

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme
import kotlinx.coroutines.delay
import kotlin.math.ceil

@Composable
fun fontSize(dpSize: Dp): TextUnit =
    with(LocalDensity.current) { dpSize.toSp() }

data class TextFitResult(
    val linesPerParent: Int,
    val parentCount:    Int,
    val pagesText:      List<String>,
)

data class ScrollLineData(
    val lineText:         String,
    val lineTopPx:        Float,
    val lineBottomPx:     Float,
    val fadeInOffset:     Float,
    val fadeOutOffset:    Float,
    val nextFadeInOffset: Float,
    val layout:           TextLayoutResult,
    val charBounds:       Array<Rect>,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScrollLineData) return false
        return lineText         == other.lineText         &&
                lineTopPx        == other.lineTopPx        &&
                lineBottomPx     == other.lineBottomPx     &&
                fadeInOffset     == other.fadeInOffset     &&
                fadeOutOffset    == other.fadeOutOffset    &&
                nextFadeInOffset == other.nextFadeInOffset &&
                layout           == other.layout           &&
                charBounds.contentEquals(other.charBounds)
    }
    override fun hashCode(): Int {
        var r = lineText.hashCode()
        r = 31 * r + lineTopPx.hashCode()
        r = 31 * r + lineBottomPx.hashCode()
        r = 31 * r + fadeInOffset.hashCode()
        r = 31 * r + fadeOutOffset.hashCode()
        r = 31 * r + nextFadeInOffset.hashCode()
        r = 31 * r + layout.hashCode()
        r = 31 * r + charBounds.contentHashCode()
        return r
    }
}

// ── Cached per-glyph data stored at page-build time ──────────────────────
data class GlyphTiming(
    val inTrigger:  Float,
    val inEnd:      Float,
    val outTrigger: Float,
    val outEnd:     Float,
)

// NEW: store the bounding rect alongside timing so we never call
// getBoundingBox() inside the draw loop.
data class GlyphDrawInfo(
    val timing: GlyphTiming,
    val bounds: Rect,           // cached from getBoundingBox() at build time
    val char:   Char,
    val stringIndex: Int,
)

data class FrameDrawState(
    val text:   String,
    val layout: TextLayoutResult,
    // Replaces List<GlyphTiming?> — only non-space glyphs, pre-filtered
    val glyphs: List<GlyphDrawInfo>,
    // Pre-cached color components to avoid Color.copy() allocation in draw
    val colorR: Float,
    val colorG: Float,
    val colorB: Float,
)

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

@Composable
fun TextFitBox(
    pages:               List<String>,
    linesPerParent:      Int,
    textStyle:           TextStyle,
    wpm:                 Int,
    padding:             Dp             = 10.dp,
    isHorizontal:        Boolean        = false,
    transitionMode:      TransitionMode = TransitionMode.None,
    preview:             Boolean        = false,
    modifier:            Modifier       = Modifier,
    onAnimationComplete: (() -> Unit)?  = null,
) {
    var currentPage  by remember(pages) { mutableIntStateOf(0) }
    val progress     = remember { Animatable(0f) }
    val textMeasurer = rememberTextMeasurer()
    var alpha by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(pages, wpm, transitionMode, preview) {
        if (!preview) {
            alpha = 0f
            delay(4000L)
            alpha = 1f
        }

        while (true) {

            val page = pages.getOrNull(currentPage) ?: break

            when (transitionMode) {
                TransitionMode.None -> {
                    val hold = calculatePageDurationMs(page, wpm).coerceAtLeast(600L)
                    delay(hold)
                }

                TransitionMode.Print,
                TransitionMode.Fade -> {
                    progress.snapTo(0f)
                    val hold = calculatePageDurationMs(page, wpm).coerceAtLeast(600L)
                    progress.animateTo(1f, tween(hold.toInt(), easing = LinearEasing))
                }
            }

            if (currentPage == pages.lastIndex && !preview) {
                onAnimationComplete?.invoke()
                break
            }

            currentPage = (currentPage + 1) % pages.size
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
            .graphicsLayer(alpha = alpha)
            .fillMaxSize(),
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
                val density = LocalDensity.current
                val contentWidthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
                val contentColor = textStyle.color.takeOrElse { LocalContentColor.current }

                // ── Build-time state (unchanged from original) ──────────────────────
                val drawState = remember(pageText, textStyle, contentWidthPx, linesPerParent, wpm) {

                    val layout = textMeasurer.measure(
                        text = pageText,
                        style = textStyle,
                        constraints = Constraints(maxWidth = contentWidthPx),
                        overflow = TextOverflow.Clip,
                        maxLines = linesPerParent,
                    )

                    val holdMs = calculatePageDurationMs(pageText, wpm).coerceAtLeast(1L).toFloat()
                    val msPerWord = if (wpm > 0) 60_000f / wpm else 500f

                    data class CharArrival(val index: Int, val arrivalMs: Float)

                    val arrivals = ArrayList<CharArrival>(pageText.length)

                    var wordStartMs = 0f
                    var wordStartIdx = -1

                    fun flushWord(wordEndIdx: Int) {
                        if (wordStartIdx < 0) return
                        val wordChars =
                            pageText.substring(wordStartIdx, wordEndIdx + 1).filter { it != ' ' }
                        val wLen = wordChars.length.coerceAtLeast(1)
                        var charPos = 0
                        for (si in wordStartIdx..wordEndIdx) {
                            val ch = pageText[si]
                            if (ch == ' ') continue
                            arrivals.add(
                                CharArrival(
                                    si,
                                    wordStartMs + (charPos.toFloat() / wLen) * msPerWord
                                )
                            )
                            charPos++
                        }
                        wordStartMs += msPerWord
                        wordStartIdx = -1
                    }

                    pageText.forEachIndexed { si, ch ->
                        when {
                            ch == ' ' -> flushWord(si - 1)
                            else -> if (wordStartIdx < 0) wordStartIdx = si
                        }
                        if (ch != ' ') {
                            val pause = when (ch) {
                                '.', '!', '?' -> 300f
                                ',', ';', ':' -> 200f
                                else -> 0f
                            }
                            if (pause > 0f) {
                                flushWord(si); wordStartMs += pause
                            }
                        }
                    }
                    if (wordStartIdx >= 0) flushWord(pageText.lastIndex)

                    val n = arrivals.size.coerceAtLeast(1)
                    val letterSlice = 1f / n
                    val fadeBand = letterSlice * 10f
                    val pauseSpan = letterSlice * 10f
                    val budget = 1f - fadeBand * 2f - pauseSpan

                    val glyphs = ArrayList<GlyphDrawInfo>(arrivals.size)
                    for (ca in arrivals) {
                        val raw = (ca.arrivalMs / holdMs).coerceIn(0f, 1f)
                        val inTrig = raw * budget
                        val inEnd = inTrig + fadeBand
                        val outTrig = inEnd + pauseSpan
                        val outEnd = outTrig + fadeBand
                        glyphs.add(
                            GlyphDrawInfo(
                                timing = GlyphTiming(inTrig, inEnd, outTrig, outEnd),
                                bounds = layout.getBoundingBox(ca.index),
                                char = pageText[ca.index],
                                stringIndex = ca.index,
                            )
                        )
                    }

                    FrameDrawState(
                        text = pageText,
                        layout = layout,
                        glyphs = glyphs,
                        colorR = contentColor.red,
                        colorG = contentColor.green,
                        colorB = contentColor.blue,
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            val p = progress.value
                            val ds = drawState

                            // Open a layer so DstIn rects can alpha-punch into the text
                            // without affecting anything behind the composable.
                            drawContext.canvas.saveLayer(
                                bounds = Rect(Offset.Zero, size),
                                paint = Paint(),
                            )

                            // 1. Draw full layout once at full opacity.
                            //    Positions come from ds.layout — measured once at build time,
                            //    never recalculated → no position drift between frames.
                            drawText(
                                textLayoutResult = ds.layout,
                                color = Color(ds.colorR, ds.colorG, ds.colorB, 1f),
                                topLeft = Offset.Zero,
                            )

                            // 2. Mask each glyph to its current alpha via DstIn rects.
                            //    Every glyph gets a rect — including ones not yet visible
                            //    (alpha=0 erases them).  Fully visible glyphs (alpha>=1)
                            //    are skipped to avoid unnecessary fillrate.
                            for (glyph in ds.glyphs) {
                                val timing = glyph.timing

                                val glyphAlpha: Float = when {
                                    p < timing.inTrigger -> 0f
                                    transitionMode == TransitionMode.Print -> when {
                                        p >= timing.inEnd -> 1f
                                        else -> (p - timing.inTrigger) /
                                                (timing.inEnd - timing.inTrigger).coerceAtLeast(
                                                    0.0001f
                                                )
                                    }

                                    else -> {   // TransitionMode.Fade
                                        val inAlpha = when {
                                            p >= timing.inEnd -> 1f
                                            else -> (p - timing.inTrigger) /
                                                    (timing.inEnd - timing.inTrigger).coerceAtLeast(
                                                        0.0001f
                                                    )
                                        }
                                        val outAlpha = when {
                                            p <= timing.outTrigger -> 1f
                                            p >= timing.outEnd -> 0f
                                            else -> 1f - (p - timing.outTrigger) /
                                                    (timing.outEnd - timing.outTrigger).coerceAtLeast(
                                                        0.0001f
                                                    )
                                        }
                                        minOf(inAlpha, outAlpha)
                                    }
                                }

                                if (glyphAlpha >= 1f) continue   // fully visible — no masking needed

                                drawRect(
                                    color = Color.Black,     // hue irrelevant; DstIn uses alpha only
                                    topLeft = glyph.bounds.topLeft,
                                    size = glyph.bounds.size,
                                    alpha = glyphAlpha,
                                    blendMode = BlendMode.DstIn,
                                )
                            }

                            drawContext.canvas.restore()
                        }
                )
            }
        }
    }
}

@Composable
fun TextHorizontalScrollBox(
    pages:               List<String>,
    wpm:                 Int,
    textStyle:           TextStyle,
    transitionMode:      TransitionMode,
    isHorizontal:        Boolean,
    padding:             Dp       = 10.dp,
    preview:             Boolean  = false,
    modifier:            Modifier = Modifier,
    onAnimationComplete: (() -> Unit)?  = null,
) {
    val totalDurationMs = remember(pages, wpm) { inlinePlayerDurationMs(pages, wpm) }
    val text            = pages
        .joinToString(separator = " ")
        .replace("\r", "")
        .replace("\n", "")

    val textMeasurer = rememberTextMeasurer()
    var alpha by remember { mutableFloatStateOf(1f) }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val containerWidthPx =
            if (isHorizontal) constraints.maxHeight.toFloat()
            else constraints.maxWidth.toFloat()

        val measured = remember(text, textStyle) {
            textMeasurer.measure(text = text, style = textStyle)
        }

        val textWidthPx = remember(measured) {
            measured.getHorizontalPosition(text.length.coerceAtLeast(1), true)
                .coerceAtLeast(1f)
        }

        val offsetAnim = remember { Animatable(0f) }

        LaunchedEffect(textWidthPx, totalDurationMs) {
            if (textWidthPx <= 0f) return@LaunchedEffect
            if (!preview) {
                alpha = 0f
                delay(4000L)
                alpha = 1f
            }
            do {
                val start = containerWidthPx
                val end   = textWidthPx + containerWidthPx
                offsetAnim.snapTo(start)
                offsetAnim.animateTo(
                    targetValue   = -end,
                    animationSpec = tween(totalDurationMs.toInt(), easing = LinearEasing)
                )
            } while (preview)
            onAnimationComplete?.invoke()
        }

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
                .graphicsLayer(alpha = alpha)
                .fillMaxSize(),
            contentAlignment = Alignment.CenterStart,
        ) {
            when (transitionMode) {

                TransitionMode.None -> {
                    Text(
                        text     = text,
                        maxLines = 1,
                        style    = textStyle,
                        modifier = Modifier
                            .wrapContentWidth(unbounded = true, align = Alignment.Start)
                            .graphicsLayer { translationX = offsetAnim.value }
                    )
                }

                TransitionMode.Fade,
                TransitionMode.Print -> {

                    val n = text.length.coerceAtLeast(1)

                    val measuredWidth = measured
                        .getHorizontalPosition(n, true)
                        .coerceAtLeast(1f)

                    val widthScale = textWidthPx / measuredWidth

                    val fadeInOffsets: FloatArray =
                        remember(measured, containerWidthPx, textWidthPx) {
                            FloatArray(n) { i ->
                                val leftX  = measured.getHorizontalPosition(i,     true)
                                val rightX = measured.getHorizontalPosition(i + 1, true)
                                val midX   = ((leftX + rightX) / 2f) * widthScale
                                containerWidthPx / 2f - midX
                            }
                        }

                    val charBounds: Array<Rect> =
                        remember(measured, n) {
                            Array(n) { i -> measured.getBoundingBox(i) }
                        }

                    val avgCharWidth = (textWidthPx / n).coerceAtLeast(1f)
                    val fadeBand     = avgCharWidth * 10f

                    val contentColor = textStyle.color.takeOrElse { LocalContentColor.current }
                    val colorR = contentColor.red
                    val colorG = contentColor.green
                    val colorB = contentColor.blue

                    Canvas(
                        modifier = Modifier
                            .wrapContentWidth(unbounded = true, align = Alignment.Start)
                            .height(with(LocalDensity.current) {
                                measured.size.height.toDp()
                            })
                            .width(with(LocalDensity.current) { textWidthPx.toDp() })
                            .graphicsLayer { translationX = offsetAnim.value }
                    ) {
                        val offset = offsetAnim.value

                        drawContext.canvas.saveLayer(
                            bounds = Rect(Offset.Zero, this.size),
                            paint  = Paint(),
                        )

                        drawText(
                            textLayoutResult = measured,
                            color            = Color(colorR, colorG, colorB, 1f),
                            topLeft          = Offset.Zero,
                        )

                        for (i in 0 until n) {
                            val ch = text[i]

                            if (ch == ' ') {
                                drawRect(
                                    color     = Color.Black,
                                    topLeft   = charBounds[i].topLeft,
                                    size      = charBounds[i].size,
                                    alpha     = 0f,
                                    blendMode = BlendMode.DstIn,
                                )
                                continue
                            }

                            val fadeInOffset = fadeInOffsets[i]
                            val fadeInStart  = fadeInOffset + fadeBand
                            val fadeInEnd    = fadeInOffset

                            val inAlpha = when {
                                offset >= fadeInStart -> 0f
                                offset <= fadeInEnd   -> 1f
                                else -> 1f - (offset - fadeInEnd) / fadeBand
                            }

                            val glyphAlpha = if (transitionMode == TransitionMode.Print) {
                                inAlpha
                            } else {
                                val fadeOutStart = fadeInOffset
                                val fadeOutEnd   = fadeInOffset - fadeBand
                                val outAlpha = when {
                                    offset >= fadeOutStart -> 1f
                                    offset <= fadeOutEnd   -> 0f
                                    else -> (offset - fadeOutEnd) / fadeBand
                                }
                                minOf(inAlpha, outAlpha)
                            }

                            if (glyphAlpha >= 1f) continue

                            drawRect(
                                color     = Color.Black,
                                topLeft   = charBounds[i].topLeft,
                                size      = charBounds[i].size,
                                alpha     = glyphAlpha,
                                blendMode = BlendMode.DstIn,
                            )
                        }

                        drawContext.canvas.restore()
                    }
                }
            }
        }
    }
}

private fun inlinePlayerDurationMs(pages: List<String>, wpm: Int): Long {
    val page            = calculatePageDurationMs(pages[0], wpm)
    val frameDurationMs = pages.sumOf { calculatePageDurationMs(it, wpm) }
    return frameDurationMs + page * 2
}

@Composable
fun TextCentreVerticalScrollBox(
    pages:          List<String>,
    wpm:            Int,
    textStyle:      TextStyle,
    isHorizontal:   Boolean,
    transitionMode:      TransitionMode = TransitionMode.None,
    padding:             Dp             = 10.dp,
    preview:             Boolean        = false,
    trim:                Boolean        = true,
    modifier:            Modifier       = Modifier,
    onAnimationComplete: (() -> Unit)?  = null,
) {
    val textMeasurer = rememberTextMeasurer()
    var alpha by remember { mutableFloatStateOf(1f) }

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

            val raw = (0 until measured.lineCount).map { idx ->
                val start        = measured.getLineStart(idx)
                val end          = measured.getLineEnd(idx, visibleEnd = true)
                    .coerceIn(start, fullText.length)
                val lineText     = fullText.substring(start, end).trimEnd()
                val lineTopPx    = measured.getLineTop(idx)
                val lineBottomPx = measured.getLineBottom(idx)
                val lineMidPx    = (lineTopPx + lineBottomPx) / 2f
                val fadeInOffset  = containerHeightPx / 2f - lineMidPx
                val fadeOutOffset = fadeInOffset

                // Measure this line in isolation so charBounds coordinates
                // start at (0,0) — matching the Canvas origin directly.
                val lineLayout = if (lineText.isNotEmpty()) {
                    textMeasurer.measure(
                        text        = lineText,
                        style       = textStyle,
                        constraints = Constraints(maxWidth = measuredWidthPx),
                        overflow    = TextOverflow.Clip,
                        maxLines    = 1,
                    )
                } else measured   // blank line: layout unused, charBounds empty

                val bounds: Array<Rect> = if (lineText.isNotEmpty()) {
                    Array(lineText.length) { i -> lineLayout.getBoundingBox(i) }
                } else emptyArray()

                ScrollLineData(
                    lineText         = lineText,
                    lineTopPx        = lineTopPx,
                    lineBottomPx     = lineBottomPx,
                    fadeInOffset     = fadeInOffset,
                    fadeOutOffset    = fadeOutOffset,
                    nextFadeInOffset = fadeOutOffset,   // patched below
                    layout           = lineLayout,
                    charBounds       = bounds,
                )
            }

            raw.mapIndexed { idx, ld ->

                val nextFadeIn = raw.getOrNull(idx + 1)?.fadeInOffset ?: run {
                    val spacing = if (idx > 0) {
                        raw[idx - 1].fadeInOffset - raw[idx].fadeInOffset
                    } else {
                        containerHeightPx * 0.1f
                    }

                    raw[idx].fadeInOffset - spacing
                }

                ld.copy(nextFadeInOffset = nextFadeIn)
            }
        }

        // ── Trim offsets ──────────────────────────────────────────────────────
        // trim=true removes dead scroll at whichever ends are relevant per mode:
        //   None  → trimStart=false, trimEnd=false  (no fade window, nothing to trim)
        //   Fade  → trimStart=true,  trimEnd=true   (trim both ends)
        //   Print → trimStart=true,  trimEnd=false  (no fade-out, only trim entry)
        val doTrimStart = trim && when (transitionMode) {
            TransitionMode.Fade,
            TransitionMode.Print -> true
            TransitionMode.None  -> false
        }
        val doTrimEnd = trim && when (transitionMode) {
            TransitionMode.Fade  -> true
            TransitionMode.Print,
            TransitionMode.None  -> false
        }

        data class TrimOffsets(val startOffset: Float?, val endOffset: Float?)

        val trimOffsets: TrimOffsets? = remember(
            lineDataList, containerHeightPx, transitionMode, doTrimStart, doTrimEnd,
        ) {
            if ((!doTrimStart && !doTrimEnd) || lineDataList.isEmpty()) return@remember null

            val firstLine = lineDataList.first()
            val lastLine  = lineDataList.last()

            // ── fade-band helper ──────────────────────────────────────────────
            fun fadeBandFor(ld: ScrollLineData): Float {
                val fadeWindow  = ld.fadeInOffset - ld.nextFadeInOffset
                val staggerSpan = (fadeWindow * 0.60f).coerceAtLeast(1f)
                return (staggerSpan / 2f).coerceAtLeast(1f)
            }

            // ── Start trim ────────────────────────────────────────────────────
            // Snap to the first-line fade trigger (index 0, no stagger subtracted)
            // so there is zero dead scroll before the first character appears.
            val startOffset: Float? = if (doTrimStart) firstLine.fadeInOffset else null

            // ── End trim ──────────────────────────────────────────────────────
            // The last line has no real successor, so its nextFadeInOffset is patched
            // to equal fadeInOffset (see lineDataList build). This means:
            //   • fadeWindow = 0  →  staggerSpan/fadeBand collapse to 1f (coerce floor)
            //   • fade-out triggers immediately as the line reaches the reading centre
            //   • fade-out completes in ~1px of scroll travel
            //
            // Fade:  add half-container buffer past fadeInOffset so the last line
            //        is visible at centre and its instant fade-out finishes completely.
            // Print: no fade-out — stop when the last character has fully faded in.
            //        Borrow fadeBand from the second-to-last line (real fadeWindow)
            //        since the last line's own band is the collapsed 1f floor.
            val endOffset: Float? = if (doTrimEnd) {
                if (transitionMode == TransitionMode.Print) {
                    val refLine     = lineDataList.getOrElse(lineDataList.lastIndex - 1) { lastLine }
                    val fadeBand    = fadeBandFor(refLine)
                    val staggerSpan = fadeBand * 2f
                    val n           = lastLine.lineText.length.coerceAtLeast(1)
                    val fadeInStart_lastChar = lastLine.fadeInOffset - staggerSpan * (n - 1) / n
                    fadeInStart_lastChar - fadeBand
                } else {
                    // Fade — half-container buffer ensures the last line reaches the
                    // reading centre and its fade-out fully completes.
                    lastLine.fadeInOffset - containerHeightPx / 2f
                }
            } else null

            TrimOffsets(startOffset, endOffset)
        }

        // Pixel velocity derived from wpm: totalDurationMs scales with total scroll distance
        // so the reading speed in words/min matches wpm regardless of text length.
        // When start/end are trimmed the duration is scaled to the actual travel distance
        // so WPM is preserved.
        val totalDurationMs: Long = remember(textHeightPx, containerHeightPx, wpm, fullText, trimOffsets) {
            if (textHeightPx <= 0f || containerHeightPx <= 0f) return@remember 3000L
            val textDurationMs = calculatePageDurationMs(fullText, wpm).coerceAtLeast(1000L)
            val fullDistance   = textHeightPx + containerHeightPx * 2f

            val effectiveStart = trimOffsets?.startOffset ?: containerHeightPx
            val effectiveEnd   = trimOffsets?.endOffset   ?: -(textHeightPx + containerHeightPx)
            // travel distance = start − (−|end|) = start + |end|; but endOffset may be
            // negative (text scrolled past centre), so: distance = start − endOffset
            val trimmedDistance = (effectiveStart - effectiveEnd).coerceAtLeast(1f)

            (textDurationMs * trimmedDistance / textHeightPx.coerceAtLeast(1f)).toLong()
        }

        // Single constant-velocity animateTo — same structure as TextHorizontalScrollBox.
        // start/end match exactly: text enters from below, exits above.
        // trimStart/trimEnd independently control whether each end is clipped.
        LaunchedEffect(textHeightPx, totalDurationMs, containerHeightPx, trimOffsets) {
            if (textHeightPx <= 0f) return@LaunchedEffect
            if (!preview) {
                alpha = 0f
                delay(4000L)
                alpha = 1f
            }
            do {
                val start = trimOffsets?.startOffset ?: containerHeightPx
                val end   = trimOffsets?.endOffset   ?: -(textHeightPx + containerHeightPx)

                offsetAnim.snapTo(start)

                offsetAnim.animateTo(
                    targetValue   = end,
                    animationSpec = tween(
                        durationMillis = totalDurationMs.toInt(),
                        easing         = LinearEasing,
                    )
                )
                onAnimationComplete?.invoke()
            } while (preview)
        }

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
                TransitionMode.Fade -> ScrollAnimText(
                    pages = pages,
                    textStyle = textStyle,
                    lines = lineDataList,
                    offsetAnim = offsetAnim,
                    fadeOut = true,
                    modifier = scrollModifier,
                )

                TransitionMode.Print -> ScrollAnimText(
                    pages = pages,
                    textStyle = textStyle,
                    lines = lineDataList,
                    offsetAnim = offsetAnim,
                    fadeOut = false,
                    modifier = scrollModifier,
                )

                TransitionMode.None -> Text(
                    text = fullText,
                    style = textStyle,
                    overflow = TextOverflow.Clip,
                    modifier = scrollModifier,
                )
            }
        }
    }
}

@Composable
fun ScrollAnimText(
    pages:      List<String>,
    textStyle:  TextStyle,
    lines:      List<ScrollLineData>,
    offsetAnim: Animatable<Float, *>,
    fadeOut:    Boolean,
    modifier:   Modifier = Modifier,
) {
    val fullText     = remember(pages) { pages.joinToString(" ") }
    val contentColor = textStyle.color.takeOrElse { LocalContentColor.current }
    val colorR       = contentColor.red
    val colorG       = contentColor.green
    val colorB       = contentColor.blue

    Box(modifier = modifier) {
        if (lines.isEmpty()) {
            // Invisible placeholder keeps the layout pass stable so
            // textHeightPx is written via onGloballyPositioned.
            Text(
                text     = fullText,
                style    = textStyle,
                modifier = Modifier.graphicsLayer { alpha = 0f },
            )
            return@Box
        }
        Column {
            lines.forEach { lineData ->
                LineAnimCanvas(
                    lineData   = lineData,
                    offsetAnim = offsetAnim,
                    fadeOut    = fadeOut,
                    colorR     = colorR,
                    colorG     = colorG,
                    colorB     = colorB,
                )
            }
        }
    }
}

// ── LineAnimCanvas ─────────────────────────────────────────────────────────
// Replaces LineFadeText (Row of N Text composables each with graphicsLayer).
//
// BEFORE: N render nodes, N hardware texture layers, alpha computed in
//         graphicsLayer lambda re-running every frame per character.
//
// AFTER:  1 Canvas node, 0 extra layers.  Per frame:
//           saveLayer
//           drawText(lineData.layout)   — 1 call, cached layout, no re-measure
//           for each char: drawRect(DstIn, alpha)   — skipped if alpha >= 1
//           restore
//
// Jitter fix: positions come exclusively from lineData.charBounds which was
// populated by getBoundingBox() at build time on the per-line layout.
// Nothing is measured or re-measured inside this function.

@Composable
private fun LineAnimCanvas(
    lineData:   ScrollLineData,
    offsetAnim: Animatable<Float, *>,
    fadeOut:    Boolean,
    colorR:     Float,
    colorG:     Float,
    colorB:     Float,
) {
    if (lineData.lineText.isEmpty()) return

    val n           = lineData.lineText.length.coerceAtLeast(1)
    val fadeWindow  = lineData.fadeInOffset - lineData.nextFadeInOffset
    val staggerSpan = (fadeWindow * 0.60f).coerceAtLeast(1f)
    val fadeBand    = (staggerSpan / 2f).coerceAtLeast(1f)

    val lineHeightDp = with(LocalDensity.current) {
        (lineData.lineBottomPx - lineData.lineTopPx).toDp()
    }

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(lineHeightDp)
    ) {
        val offset = offsetAnim.value

        // Open an offscreen layer so DstIn rects only erase pixels we drew
        // here, not anything behind the Canvas.
        drawContext.canvas.saveLayer(
            bounds = Rect(Offset.Zero, this.size),
            paint  = Paint(),
        )

        // 1. Draw the entire line once at full opacity.
        //    lineData.layout was measured independently for this line, so
        //    its glyph coordinates are relative to (0,0) — no offset needed.
        //    Positions are identical every frame → no jitter.
        drawText(
            textLayoutResult = lineData.layout,
            color            = Color(colorR, colorG, colorB, 1f),
            topLeft          = Offset.Zero,
        )

        // 2. Per-character DstIn rect to apply alpha.
        //    DstIn: output.alpha = drawn_text.alpha × rect.alpha
        //    rect alpha 0 → glyph erased   (not yet appeared / fully faded)
        //    rect alpha 1 → glyph intact   (skip rect entirely — saves fillrate)
        //    0 < alpha < 1 → partially visible
        for (index in 0 until n) {
            val ch = lineData.lineText[index]

            // Spaces are always invisible — erase unconditionally.
            if (ch == ' ') {
                val b = lineData.charBounds.getOrNull(index) ?: continue
                drawRect(
                    color     = Color.Black,
                    topLeft   = b.topLeft,
                    size      = b.size,
                    alpha     = 0f,
                    blendMode = BlendMode.DstIn,
                )
                continue
            }

            // Fade-in: letter[index] leads (index 0 first), staggered across
            // the fade window.  Same formula as original LineFadeText.
            val fadeInStart = lineData.fadeInOffset - staggerSpan * index / n
            val fadeInEnd   = fadeInStart - fadeBand

            val inAlpha = when {
                offset >= fadeInStart -> 0f
                offset <= fadeInEnd   -> 1f
                else -> 1f - (offset - fadeInEnd) / fadeBand
            }

            val glyphAlpha = if (!fadeOut) {
                inAlpha
            } else {
                val fadeOutStart = lineData.nextFadeInOffset - staggerSpan * index / n
                val fadeOutEnd   = fadeOutStart - fadeBand
                val outAlpha = when {
                    offset >= fadeOutStart -> 1f
                    offset <= fadeOutEnd   -> 0f
                    else -> (offset - fadeOutEnd) / fadeBand
                }
                minOf(inAlpha, outAlpha)
            }

            // Fully visible — drawText already drew it at alpha=1, skip rect.
            if (glyphAlpha >= 1f) continue

            val b = lineData.charBounds.getOrNull(index) ?: continue
            drawRect(
                color     = Color.Black,
                topLeft   = b.topLeft,
                size      = b.size,
                alpha     = glyphAlpha,
                blendMode = BlendMode.DstIn,
            )
        }

        drawContext.canvas.restore()
    }
}

// ─────────────────────────────────────────────
// ScrollFadeText
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
// calculatePageDurationMs
// ─────────────────────────────────────────────
// SizeSection — one labelled group in the preview
@Composable
fun DisplayTextBar(
    task:                Task,
    wpm:                 Int,
    textStyle:           TextStyle,
    padding:             Dp,
    isHorizontal:        Boolean,
    isMirror:            Boolean,
    distortionMode:      Float,
    animationMode:       AnimationMode,
    transitionMode:      TransitionMode,
    preview:             Boolean        = true,
    modifier:            Modifier       = Modifier,
    onAnimationComplete: (() -> Unit)?  = null,
) {
    var result by remember { mutableStateOf<TextFitResult?>(null) }

    var alpha by remember { mutableFloatStateOf(1f) }

    BoxWithConstraints(
        modifier = Modifier
            .graphicsLayer {
                this.alpha = alpha
                scaleX = if (isHorizontal) distortionMode else 1f
                scaleY = if (isHorizontal) 1f else distortionMode
            }
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        val playerSize = Modifier
            .width(if (isHorizontal) maxWidth / distortionMode else maxWidth)
            .height(if (isHorizontal) maxHeight else maxHeight / distortionMode)

        BoxWithConstraints(
            modifier = Modifier
                .graphicsLayer {
                    scaleY = if (isMirror) -1f else 1f
                }
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            TextFitCalculator(
                text = task.description,
                fontSize = textStyle.fontSize,
                lineHeight = textStyle.lineHeight,
                padding = padding,
                isHorizontal = isHorizontal,
                modifier = playerSize,
                onResult = { result = it },
            )

            result?.let { r ->
                val pages = remember(r.pagesText, preview) {
                    if (preview) r.pagesText.take(2) else r.pagesText
                }
                TextFitPlayer(
                    result = r,
                    pages = pages,
                    wpm = wpm,
                    textStyle = textStyle,
                    padding = padding,
                    isHorizontal = isHorizontal,
                    animationMode = animationMode,
                    transitionMode = transitionMode,
                    preview = preview,
                    modifier = playerSize,
                    onAnimationComplete = onAnimationComplete,
                )

                LaunchedEffect(isMirror, isHorizontal, animationMode, transitionMode, distortionMode) {
                    alpha = 1f
                }

                DisposableEffect(isMirror, isHorizontal, animationMode, transitionMode, distortionMode) {
                    onDispose {
                        alpha = 0f
                    }
                }
            }
        }
    }
}

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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NormalSizeSection(
    fontSizeDp: Dp,
    lineHeightDp: Dp,
) {
    val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
        fontSize   = fontSize(fontSizeDp),
        lineHeight = fontSize(lineHeightDp),
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