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
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
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
import kotlin.math.max
import kotlin.math.roundToInt

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
    val parentCount: Int,
    val pagesText: List<String>,
)

// ─────────────────────────────────────────────
// Core calculation — normal sizes
// ─────────────────────────────────────────────

fun calculateTextFit(
    textMeasurer: TextMeasurer,
    text: String,
    style: TextStyle,
    parentWidthPx: Int,
    parentHeightPx: Int,
): TextFitResult {
    if (text.isBlank() || parentWidthPx <= 0 || parentHeightPx <= 0)
        return TextFitResult(0, 0, emptyList())

    val measured = textMeasurer.measure(
        text = text,
        style = style,
        constraints = Constraints(maxWidth = parentWidthPx),
        overflow = TextOverflow.Clip,
    )

    val totalLines     = measured.lineCount
    val lineHeightPx   = measured.size.height.toFloat() / totalLines.coerceAtLeast(1)
    val linesPerParent = (parentHeightPx / lineHeightPx).toInt().coerceAtLeast(1)
    val parentCount    = ceil(totalLines.toFloat() / linesPerParent).toInt()

    val pages = mutableListOf<String>()
    var line = 0
    while (line < totalLines) {
        val endLine = minOf(line + linesPerParent - 1, totalLines - 1)
        pages += text.substring(measured.getLineStart(line), measured.getLineEnd(endLine))
        line += linesPerParent
    }

    return TextFitResult(linesPerParent, parentCount, pages)
}

// ─────────────────────────────────────────────
// TextFitCalculator — invisible, normal sizes
// ─────────────────────────────────────────────

@Composable
fun TextFitCalculator(
    text: String,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    padding: Dp = 10.dp,
    isHorizontal: Boolean = false,
    modifier: Modifier = Modifier,
    onResult: (TextFitResult) -> Unit,
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val style = TextStyle(fontSize = fontSize, lineHeight = lineHeight)

    BoxWithConstraints(modifier = modifier) {

        val contentWidthPx = with(density) {
            (maxWidth - (padding * 2)).roundToPx().coerceAtLeast(1)
        }

        val contentHeightPx = with(density) {
            (maxHeight - (padding * 2)).roundToPx().coerceAtLeast(1)
        }

        // Vertical mode: text runs rotated 90°, so line-width == physical height
        // and page-height == physical width.
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
// TextFitBox — normal sizes, one page
// ─────────────────────────────────────────────

@Composable
fun TextFitBox(
    pageText: String,
    linesPerParent: Int,
    textStyle: TextStyle,
    padding: Dp = 10.dp,
    isHorizontal: Boolean = false,
    modifier: Modifier = Modifier,
) {
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

    Box(modifier = rotatedModifier.padding(padding)) {
        Text(
            text     = pageText,
            style    = textStyle,
            maxLines = linesPerParent,
            overflow = TextOverflow.Clip,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
fun TextVerticalScrollBox(
    totalDurationMs: Long,
    text: String,
    textStyle: TextStyle,
    isHorizontal: Boolean,
    padding: Dp = 10.dp,
    modifier: Modifier = Modifier,
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

                offsetAnim.snapTo(start)
                alpha = 1f

                offsetAnim.animateTo(
                    targetValue = -end,
                    animationSpec = tween(
                        durationMillis = totalDurationMs.toInt(),
                        easing = LinearEasing
                    )
                )
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
    text: String,
    textStyle: TextStyle,
    isHorizontal: Boolean,
    padding: Dp = 10.dp,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        val containerWidthPx =
            if (isHorizontal) constraints.maxHeight.toFloat() else constraints.maxWidth.toFloat()

        var textWidthPx by remember { mutableFloatStateOf(0f) }
        val offsetAnim = remember { Animatable(0f) }
        var alpha by remember { mutableFloatStateOf(0f) }

        LaunchedEffect(textWidthPx, totalDurationMs) {

            if (textWidthPx <= 0f) return@LaunchedEffect

            while (true) {
                val start = containerWidthPx
                val end = textWidthPx + containerWidthPx

                offsetAnim.snapTo(start)
                alpha = 1f

                offsetAnim.animateTo(
                    targetValue = -end,
                    animationSpec = tween(
                        durationMillis = totalDurationMs.toInt(),
                        easing = LinearEasing
                    )
                )
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

                        layout(
                            placeable.height,
                            placeable.width
                        ) {
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
            contentAlignment = Alignment.CenterStart
        ) {

            Text(
                text = text,
                maxLines = 1,
                style = textStyle,
                modifier = Modifier
                    .wrapContentWidth(
                        unbounded = true,
                        align = Alignment.Start
                    )
                    .graphicsLayer {
                        translationX = offsetAnim.value
                    }
                    .onGloballyPositioned { coordinates ->
                        val measuredWidth = coordinates.size.width.toFloat()
                        if (measuredWidth != textWidthPx) textWidthPx = measuredWidth
                    }
            )
        }
    }
}

// ─────────────────────────────────────────────
// Shared preview text + sizes table
// ─────────────────────────────────────────────

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

        // Pages — horizontal row
        result?.let { r ->
            Row() {
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
                            pageText       = pageText,
                            linesPerParent = r.linesPerParent,
                            textStyle      = textStyle,
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
@Preview(showBackground = true, widthDp = 412, heightDp = 200, name = "VerticalScroll – Normal")
@Composable
fun PreviewVerticalScrollBox() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
//        TextVerticalScrollBox(
//            text      = PREVIEW_TEXT,
//            textStyle = textStyle,
//            wpm       = 130,
//            modifier  = Modifier.fillMaxSize(),
//        )
    }
}

// ─────────────────────────────────────────────
// Preview: TextHorizontalScrollBox
// ─────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Preview(showBackground = true, widthDp = 412, heightDp = 200, name = "HorizontalScroll – Normal")
@Composable
fun PreviewHorizontalScrollBox() {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
        )
//        TextHorizontalScrollBox(
//            text         = PREVIEW_TEXT,
//            textStyle    = textStyle,
//            wpm          = 130,
//            isHorizontal = false,
//            modifier     = Modifier.fillMaxSize(),
//        )
    }
}
