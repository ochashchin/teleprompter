package com.oprojectview.frame

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.oprojectview.AnimationMode
import com.oprojectview.StyleSpan
import com.oprojectview.TextFitResult
import com.oprojectview.TransitionMode
import com.oprojectview.calculateTextFit
import com.oprojectview.core.MonotonicClock
import com.oprojectview.speedIndexToWpm
import kotlin.concurrent.Volatile
import kotlin.math.ceil
import kotlin.time.TimeSource

/**
 * CPU-only Skia-backed Frame Renderer.
 * This class runs entirely on the CPU (using software-backed canvases and bitmaps),
 * making it safe to execute while the application is backgrounded.
 */
class CpuTeleprompterFrameRenderer(
    private val density: Density,
    private val layoutDirection: LayoutDirection = LayoutDirection.Ltr
) : FrameRenderer {

    private var frameCounter = 0L
    private val epoch = TimeSource.Monotonic.markNow()
    private val drawScope = CanvasDrawScope()

    // Set dynamically from the Compose UI composition
    @Volatile
    var fontFamilyResolver: FontFamily.Resolver? = null
        set(value) {
            if (field != value) {
                field = value
                textMeasurer = null
                cachedTextLayoutResult = null
                cachedTextFitResult = null
                cachedPageGlyphCache = null
            }
        }

    private var textMeasurer: TextMeasurer? = null

    private fun getOrCreateTextMeasurer(): TextMeasurer? {
        val resolver = fontFamilyResolver ?: return null
        var measurer = textMeasurer
        if (measurer == null) {
            measurer = TextMeasurer(
                defaultFontFamilyResolver = resolver,
                defaultDensity = density,
                defaultLayoutDirection = layoutDirection,
                cacheSize = 8
            )
            textMeasurer = measurer
        }
        return measurer
    }

    // Caching layout results to avoid heavy measurements on every tick
    private var cachedTextLayoutResult: TextLayoutResult? = null
    private var cachedTextFitResult: TextFitResult? = null
    private var cachedLayoutStateSig: LayoutStateSignature? = null
    private var cachedPageGlyphCache: PageGlyphCache? = null
    private var cachedInlineXPositions: FloatArray? = null

    private data class LayoutStateSignature(
        val scriptText: String,
        val styleSpans: List<StyleSpan>,
        val textSizeIndex: Int,
        val frameWidthPx: Int,
        val frameHeightPx: Int,
        val animationMode: AnimationMode
    )

    // Per-glyph timing data for Fade/Print transitions (mirrors DisplayTextBar.GlyphTiming)
    private data class GlyphTiming(
        val inTrigger:  Float,
        val inEnd:      Float,
        val outTrigger: Float,
        val outEnd:     Float,
    )
    private data class GlyphDrawInfo(
        val timing:      GlyphTiming,
        val bounds:      Rect,
        val stringIndex: Int,
    )
    private data class PageGlyphCache(
        val pageText:    String,
        val textStyle:   TextStyle,
        val layoutWidth: Int,
        val wpm:         Int,
        val glyphs:      List<GlyphDrawInfo>,
    )

    override suspend fun renderFrame(state: FrameState, pool: BitmapPool): RenderedFrame {
        val bitmap = pool.acquire()
        val canvas = Canvas(bitmap)

        // 1. Draw Background
        val bgPaint = Paint().apply {
            color = if (state.fillColorVal != 0L) {
                Color(state.fillColorVal.toULong())
            } else if (state.defaultFillColorVal != 0L) {
                Color(state.defaultFillColorVal.toULong())
            } else {
                Color.Black
            }
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), bgPaint)

        // 2. Resolve Text/Layout Constraints
        if (state.scriptText.isNotEmpty()) {
            val hPaddingPx = with(density) { 10.dp.toPx() }
            val vPaddingPx = with(density) { 5.dp.toPx() }
            val contentWidth = (bitmap.width - hPaddingPx * 2).toInt().coerceAtLeast(1)
            val contentHeight = (bitmap.height - vPaddingPx * 2).toInt().coerceAtLeast(1)

            val textStyle = createTextStyle(state.textSizeIndex, state.textColorVal)

            // Layout caching validation signature
            val currentSig = LayoutStateSignature(
                scriptText = state.scriptText,
                styleSpans = state.styleSpans,
                textSizeIndex = state.textSizeIndex,
                frameWidthPx = bitmap.width,
                frameHeightPx = bitmap.height,
                animationMode = state.animationMode
            )

            val isCacheValid = cachedLayoutStateSig == currentSig

            if (!isCacheValid) {
                cachedLayoutStateSig = currentSig
                cachedTextLayoutResult = null
                cachedTextFitResult = null
                cachedPageGlyphCache = null
                cachedInlineXPositions = null
            }

            val measurer = getOrCreateTextMeasurer()

            var drawSpinner = false
            if (!state.countdownDone) {
                if (state.countdownStartUs > 0L) {
                    val nowUs = MonotonicClock.currentTimeUs()
                    val elapsedMs = if (state.isPlaying) {
                        (nowUs - state.countdownStartUs) / 1000L
                    } else {
                        state.pausedCountdownElapsedUs / 1000L
                    }
                    if (elapsedMs < 6000L) {
                        drawSpinner = true
                    }
                } else {
                    // Initial unsynchronized state on first run
                    drawSpinner = true
                }
            }

            if (drawSpinner) {
                val nowUs = MonotonicClock.currentTimeUs()
                val elapsedMs = if (state.countdownStartUs > 0L) {
                    if (state.isPlaying) {
                        (nowUs - state.countdownStartUs) / 1000L
                    } else {
                        state.pausedCountdownElapsedUs / 1000L
                    }
                } else {
                    0L
                }

                val progress = 1f - (elapsedMs.toFloat() / 6000f).coerceIn(0f, 1f)
                val pColor = if (state.primaryColorVal != 0L) Color(state.primaryColorVal.toULong()) else Color(0xFF1E88E5)
                val bgArcColor = if (state.surfaceVariantColorVal != 0L) Color(state.surfaceVariantColorVal.toULong()) else Color.LightGray

                val arcPaint = Paint().apply {
                    color = pColor
                    style = PaintingStyle.Stroke
                    strokeWidth = with(density) { 8.dp.toPx() }
                }
                val bgArcPaint = Paint().apply {
                    color = bgArcColor
                    style = PaintingStyle.Stroke
                    strokeWidth = with(density) { 8.dp.toPx() }
                }
                val center = Offset(bitmap.width / 2f, bitmap.height / 2f)
                val radius = with(density) { 50.dp.toPx() }
                canvas.drawArc(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius,
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    paint = bgArcPaint
                )
                canvas.drawArc(
                    left = center.x - radius,
                    top = center.y - radius,
                    right = center.x + radius,
                    bottom = center.y + radius,
                    startAngle = -90f + (1f - progress) * 360f,
                    sweepAngle = progress * 360f,
                    useCenter = false,
                    paint = arcPaint
                )
            }
            
            if (!drawSpinner && measurer != null) {

                    when (state.animationMode) {
                        AnimationMode.Scroll -> {
                            // Vertical continuous center scroll
                            val layoutResult = cachedTextLayoutResult ?: run {
                                val annotatedString = buildAnnotatedString(state.scriptText, state.styleSpans)
                                measurer.measure(
                                    text = annotatedString,
                                    style = textStyle,
                                    constraints = Constraints(maxWidth = contentWidth),
                                    overflow = TextOverflow.Clip
                                ).also { cachedTextLayoutResult = it }
                            }

                            // Start at vertical center and scroll until text is fully off screen at the top
                            val startY = bitmap.height / 2f
                            val endY = -(layoutResult.size.height.toFloat() + vPaddingPx)
                            val yOffset = startY + (endY - startY) * state.scrollFraction
                            val xOffset = hPaddingPx

                            if (state.transitionMode == TransitionMode.None) {
                                drawScope.draw(
                                    density = density,
                                    layoutDirection = layoutDirection,
                                    canvas = canvas,
                                    size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
                                ) {
                                    withTransform({
                                        if (state.isMirror) {
                                            translate(size.width, 0f)
                                            scale(-1f, 1f, pivot = Offset.Zero)
                                        }
                                    }) {
                                        drawText(layoutResult, topLeft = Offset(xOffset, yOffset))
                                    }
                                }
                            } else {
                                val layerBounds = Rect(
                                    xOffset, yOffset,
                                    xOffset + layoutResult.size.width,
                                    yOffset + layoutResult.size.height
                                )
                                val layerPaint = Paint()
                                canvas.saveLayer(layerBounds, layerPaint)

                                drawScope.draw(
                                    density = density,
                                    layoutDirection = layoutDirection,
                                    canvas = canvas,
                                    size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
                                ) {
                                    withTransform({
                                        if (state.isMirror) {
                                            translate(size.width, 0f)
                                            scale(-1f, 1f, pivot = Offset.Zero)
                                        }
                                    }) {
                                        drawText(layoutResult, topLeft = Offset(xOffset, yOffset))
                                    }
                                }

                                val dstInPaint = Paint().apply { blendMode = BlendMode.DstIn }
                                val containerHeightPx = bitmap.height.toFloat()
                                val fadeBand = containerHeightPx * 0.1f
                                val centerPoint = containerHeightPx / 2f

                                for (i in 0 until layoutResult.lineCount) {
                                    val lineTop = layoutResult.getLineTop(i)
                                    val lineBottom = layoutResult.getLineBottom(i)
                                    val lineMid = (lineTop + lineBottom) / 2f
                                    val absoluteMidY = yOffset + lineMid

                                    val fadeInStart = centerPoint + fadeBand
                                    val fadeInEnd = centerPoint

                                    val inAlpha = if (absoluteMidY >= fadeInStart) 0f
                                                  else if (absoluteMidY <= fadeInEnd) 1f
                                                  else 1f - (absoluteMidY - fadeInEnd) / fadeBand

                                    val lineAlpha = if (state.transitionMode == TransitionMode.Print) {
                                        inAlpha
                                    } else {
                                        val fadeOutStart = centerPoint
                                        val fadeOutEnd = centerPoint - fadeBand
                                        val outAlpha = if (absoluteMidY >= fadeOutStart) 1f
                                                       else if (absoluteMidY <= fadeOutEnd) 0f
                                                       else (absoluteMidY - fadeOutEnd) / fadeBand
                                        minOf(inAlpha, outAlpha)
                                    }

                                    if (lineAlpha >= 1f) continue

                                    dstInPaint.alpha = lineAlpha
                                    val lineLeft = layoutResult.getLineLeft(i)
                                    val lineRight = layoutResult.getLineRight(i)

                                    val absLeft = xOffset + lineLeft
                                    val absTop = yOffset + lineTop
                                    val absRight = xOffset + lineRight
                                    val absBottom = yOffset + lineBottom

                                    canvas.drawRect(absLeft, absTop, absRight, absBottom, dstInPaint)
                                }
                                canvas.restore()
                            }
                        }

                        AnimationMode.Inline -> {
                            // Horizontal scrolling single-line bar
                            val inlineText = state.scriptText.replace("\r", " ").replace("\n", " ")
                            val layoutResult = cachedTextLayoutResult ?: run {
                                val annotatedString = buildAnnotatedString(inlineText, state.styleSpans)
                                measurer.measure(
                                    text = annotatedString,
                                    style = textStyle,
                                    constraints = Constraints(maxWidth = Constraints.Infinity),
                                    overflow = TextOverflow.Clip
                                ).also { cachedTextLayoutResult = it }
                            }

                            val startX = bitmap.width.toFloat()
                            val endX = -(layoutResult.size.width.toFloat() + bitmap.width.toFloat())
                            val xOffset = startX + (endX - startX) * state.scrollFraction
                            val yOffset = (bitmap.height - layoutResult.size.height) / 2f

                            if (state.transitionMode == TransitionMode.None) {
                                drawScope.draw(
                                    density = density,
                                    layoutDirection = layoutDirection,
                                    canvas = canvas,
                                    size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
                                ) {
                                    withTransform({
                                        if (state.isMirror) {
                                            translate(size.width, 0f)
                                            scale(-1f, 1f, pivot = Offset.Zero)
                                        }
                                    }) {
                                        drawText(layoutResult, topLeft = Offset(xOffset, yOffset))
                                    }
                                }
                            } else {
                                val layerBounds = Rect(
                                    xOffset, yOffset,
                                    xOffset + layoutResult.size.width,
                                    yOffset + layoutResult.size.height
                                )
                                val layerPaint = Paint()
                                canvas.saveLayer(layerBounds, layerPaint)

                                drawScope.draw(
                                    density = density,
                                    layoutDirection = layoutDirection,
                                    canvas = canvas,
                                    size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
                                ) {
                                    withTransform({
                                        if (state.isMirror) {
                                            translate(size.width, 0f)
                                            scale(-1f, 1f, pivot = Offset.Zero)
                                        }
                                    }) {
                                        drawText(layoutResult, topLeft = Offset(xOffset, yOffset))
                                    }
                                }

                                val dstInPaint = Paint().apply { blendMode = BlendMode.DstIn }
                                val containerWidthPx = bitmap.width.toFloat()
                                val fadeBand = containerWidthPx * 0.1f
                                val centerPoint = containerWidthPx / 2f

                                val positions = cachedInlineXPositions?.takeIf { it.size == inlineText.length + 1 }
                                    ?: FloatArray(inlineText.length + 1) { i ->
                                        layoutResult.getHorizontalPosition(i, true)
                                    }.also { cachedInlineXPositions = it }

                                for (i in inlineText.indices) {
                                    if (inlineText[i] == ' ') continue

                                    val leftX = positions[i]
                                    val rightX = positions[i + 1]
                                    val charMid = (leftX + rightX) / 2f
                                    val absoluteMidX = xOffset + charMid

                                    val fadeInStart = centerPoint + fadeBand
                                    val fadeInEnd = centerPoint

                                    val inAlpha = if (absoluteMidX >= fadeInStart) 0f
                                                  else if (absoluteMidX <= fadeInEnd) 1f
                                                  else 1f - (absoluteMidX - fadeInEnd) / fadeBand

                                    val charAlpha = if (state.transitionMode == TransitionMode.Print) {
                                        inAlpha
                                    } else {
                                        val fadeOutStart = centerPoint
                                        val fadeOutEnd = centerPoint - fadeBand
                                        val outAlpha = if (absoluteMidX >= fadeOutStart) 1f
                                                       else if (absoluteMidX <= fadeOutEnd) 0f
                                                       else (absoluteMidX - fadeOutEnd) / fadeBand
                                        minOf(inAlpha, outAlpha)
                                    }

                                    if (charAlpha >= 1f) continue

                                    dstInPaint.alpha = charAlpha
                                    val absLeft = xOffset + leftX
                                    val absTop = yOffset
                                    val absRight = xOffset + rightX
                                    val absBottom = yOffset + layoutResult.size.height.toFloat()

                                    canvas.drawRect(absLeft, absTop, absRight, absBottom, dstInPaint)
                                }
                                canvas.restore()
                            }
                        }

                        AnimationMode.Frame -> {
                            // Page by page cycle
                            val calcWidthPx = if (state.isHorizontal) contentHeight else contentWidth
                            val calcHeightPx = if (state.isHorizontal) contentWidth else contentHeight

                            val fitResult = cachedTextFitResult ?: run {
                                calculateTextFit(
                                    textMeasurer = measurer,
                                    text = state.scriptText,
                                    style = textStyle,
                                    parentWidthPx = calcWidthPx,
                                    parentHeightPx = calcHeightPx
                                ).also { cachedTextFitResult = it }
                            }

                            val pages = fitResult.pagesText
                            if (pages.isNotEmpty()) {
                                val globalPos = (state.scrollFraction * pages.size)
                                    .coerceIn(0f, pages.size.toFloat())
                                val activePageIndex = globalPos.toInt()
                                    .coerceIn(0, pages.lastIndex)
                                val pageProgress = (globalPos - activePageIndex).coerceIn(0f, 1f)
                                val activePageText = pages[activePageIndex]

                                val pageAnnotated = buildPageAnnotatedString(
                                    fullText = state.scriptText,
                                    pageText = activePageText,
                                    styleSpans = state.styleSpans
                                )

                                val pageLayout = measurer.measure(
                                    text = pageAnnotated,
                                    style = textStyle,
                                    constraints = Constraints(maxWidth = calcWidthPx),
                                    overflow = TextOverflow.Clip
                                )

                                val xOffset = hPaddingPx
                                val yOffset = vPaddingPx

                                when (state.transitionMode) {
                                    TransitionMode.None -> {
                                        // Hard cut: draw text directly
                                        drawScope.draw(
                                            density = density,
                                            layoutDirection = layoutDirection,
                                            canvas = canvas,
                                            size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
                                        ) {
                                            withTransform({
                                                if (state.isMirror) {
                                                    translate(size.width, 0f)
                                                    scale(-1f, 1f, pivot = Offset.Zero)
                                                }
                                            }) {
                                                drawText(pageLayout, topLeft = Offset(xOffset, yOffset))
                                            }
                                        }
                                    }

                                    TransitionMode.Fade, TransitionMode.Print -> {
                                        // Build glyph timing cache (once per unique page+style+wpm)
                                        val wpm = state.wpm.coerceAtLeast(1)
                                        val glyphCache = cachedPageGlyphCache?.takeIf {
                                            it.pageText == activePageText &&
                                            it.textStyle == textStyle &&
                                            it.layoutWidth == calcWidthPx &&
                                            it.wpm == wpm
                                        } ?: buildGlyphCache(
                                            pageText = activePageText,
                                            layout = pageLayout,
                                            wpm = wpm
                                        ).also { cachedPageGlyphCache = it }

                                        // Open an isolated layer (same DstIn trick as DisplayTextBar)
                                        val layerBounds = Rect(
                                            xOffset, yOffset,
                                            xOffset + pageLayout.size.width,
                                            yOffset + pageLayout.size.height
                                        )
                                        val layerPaint = Paint()
                                        canvas.saveLayer(layerBounds, layerPaint)

                                        // 1. Draw full text at 100% alpha within the layer
                                        drawScope.draw(
                                            density = density,
                                            layoutDirection = layoutDirection,
                                            canvas = canvas,
                                            size = Size(bitmap.width.toFloat(), bitmap.height.toFloat())
                                        ) {
                                            withTransform({
                                                if (state.isMirror) {
                                                    translate(size.width, 0f)
                                                    scale(-1f, 1f, pivot = Offset.Zero)
                                                }
                                            }) {
                                                drawText(pageLayout, topLeft = Offset(xOffset, yOffset))
                                            }
                                        }

                                        // 2. Per-glyph DstIn mask at computed alpha
                                        val dstInPaint = Paint().apply {
                                            blendMode = BlendMode.DstIn
                                        }
                                        for (glyph in glyphCache.glyphs) {
                                            val t = glyph.timing
                                            val p = pageProgress
                                            val glyphAlpha: Float = if (p < t.inTrigger) {
                                                0f
                                            } else if (state.transitionMode == TransitionMode.Print) {
                                                if (p >= t.inEnd) 1f
                                                else (p - t.inTrigger) / (t.inEnd - t.inTrigger).coerceAtLeast(0.0001f)
                                            } else { // Fade
                                                val inAlpha = if (p >= t.inEnd) 1f
                                                    else (p - t.inTrigger) / (t.inEnd - t.inTrigger).coerceAtLeast(0.0001f)
                                                val outAlpha = if (p <= t.outTrigger) 1f
                                                    else if (p >= t.outEnd) 0f
                                                    else 1f - (p - t.outTrigger) / (t.outEnd - t.outTrigger).coerceAtLeast(0.0001f)
                                                minOf(inAlpha, outAlpha)
                                            }
                                            if (glyphAlpha >= 1f) continue  // fully visible, skip
                                            dstInPaint.alpha = glyphAlpha
                                            // Glyph bounds are relative to layout (0,0); offset to canvas position
                                            val b = glyph.bounds
                                            canvas.drawRect(
                                                Rect(
                                                    b.left + xOffset,
                                                    b.top + yOffset,
                                                    b.right + xOffset,
                                                    b.bottom + yOffset
                                                ),
                                                dstInPaint
                                            )
                                        }
                                        canvas.restore()
                                    }
                                }
                            }
                        }
                    }
            }
        }

        frameCounter++
        return RenderedFrame(
            bitmap = bitmap,
            presentationTimeUs = epoch.elapsedNow().inWholeMicroseconds,
            frameIndex = frameCounter - 1,
            widthPx = bitmap.width,
            heightPx = bitmap.height,
            isPlaceholder = state.scriptText.isEmpty()
        )
    }

    private fun buildGlyphCache(
        pageText: String,
        layout:   TextLayoutResult,
        wpm:      Int,
    ): PageGlyphCache {
        val msPerWord = if (wpm > 0) 60_000f / wpm else 500f
        val holdMs    = pageText.split(' ').size.coerceAtLeast(1) * msPerWord

        data class CharArrival(val index: Int, val arrivalMs: Float)
        val arrivals = ArrayList<CharArrival>(pageText.length)

        var wordStartMs  = 0f
        var wordStartIdx = -1

        fun flushWord(wordEndIdx: Int) {
            if (wordStartIdx < 0) return
            val wLen = pageText.substring(wordStartIdx, wordEndIdx + 1).count { it != ' ' }.coerceAtLeast(1)
            var charPos = 0
            for (si in wordStartIdx..wordEndIdx) {
                val ch = pageText[si]
                if (ch == ' ') continue
                arrivals.add(CharArrival(si, wordStartMs + (charPos.toFloat() / wLen) * msPerWord))
                charPos++
            }
            wordStartMs += msPerWord
            wordStartIdx = -1
        }

        pageText.forEachIndexed { si, ch ->
            when {
                ch == ' ' -> flushWord(si - 1)
                else      -> if (wordStartIdx < 0) wordStartIdx = si
            }
            if (ch != ' ') {
                val pause = when (ch) {
                    '.', '!', '?' -> 300f
                    ',', ';', ':' -> 200f
                    else          -> 0f
                }
                if (pause > 0f) { flushWord(si); wordStartMs += pause }
            }
        }
        if (wordStartIdx >= 0) flushWord(pageText.lastIndex)

        val n         = arrivals.size.coerceAtLeast(1)
        val letterSlice = 1f / n
        val fadeBand  = letterSlice * 10f
        val pauseSpan = letterSlice * 10f
        val budget    = 1f - fadeBand * 2f - pauseSpan

        val glyphs = ArrayList<GlyphDrawInfo>(arrivals.size)
        for (ca in arrivals) {
            val raw     = (ca.arrivalMs / holdMs.coerceAtLeast(1f)).coerceIn(0f, 1f)
            val inTrig  = raw * budget
            val inEnd   = inTrig + fadeBand
            val outTrig = inEnd  + pauseSpan
            val outEnd  = outTrig + fadeBand
            glyphs.add(
                GlyphDrawInfo(
                    timing = GlyphTiming(inTrig, inEnd, outTrig, outEnd),
                    bounds = layout.getBoundingBox(ca.index),
                    stringIndex = ca.index,
                )
            )
        }
        return PageGlyphCache(pageText, TextStyle(), 0, wpm, glyphs)
    }

    private fun createTextStyle(textSizeIndex: Int, textColorVal: Long): TextStyle {
        val (fontSizeDp, lineHeightDp) = when (textSizeIndex) {
            0 -> Pair(17.dp, 23.dp)
            1 -> Pair(23.dp, 30.dp)
            2 -> Pair(32.dp, 41.dp)
            3 -> Pair(36.dp, 46.dp)
            4 -> Pair(40.dp, 51.dp)
            5 -> Pair(56.dp, 71.dp)
            else -> Pair(23.dp, 30.dp)
        }
        return TextStyle(
            fontSize = with(density) { fontSizeDp.toSp() },
            lineHeight = with(density) { lineHeightDp.toSp() },
            color = if (textColorVal != 0L) Color(textColorVal.toULong()) else Color.White
        )
    }

    private fun buildAnnotatedString(text: String, spans: List<StyleSpan>): AnnotatedString {
        if (spans.isEmpty()) return AnnotatedString(text)
        return androidx.compose.ui.text.buildAnnotatedString {
            append(text)
            spans.forEach { span ->
                val s = span.start.coerceIn(0, text.length)
                val e = span.end.coerceIn(s, text.length)
                if (s < e) {
                    addStyle(span.toSpanStyle(com.oprojectview.getScriptTextColors()), s, e)
                }
            }
        }
    }

    private fun buildPageAnnotatedString(
        fullText: String,
        pageText: String,
        styleSpans: List<StyleSpan>
    ): AnnotatedString {
        val pageStart = fullText.indexOf(pageText).coerceAtLeast(0)
        return buildAnnotatedString {
            append(pageText)
            styleSpans.forEach { span ->
                val s = (span.start - pageStart).coerceAtLeast(0)
                val e = (span.end - pageStart).coerceAtMost(pageText.length)
                if (s < e) {
                    addStyle(span.toSpanStyle(com.oprojectview.getScriptTextColors()), s, e)
                }
            }
        }
    }
}
