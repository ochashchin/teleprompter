package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.layout
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme
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
    val lineCountPerParent: Int,
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

        // Vertical mode: text runs rotated -90°, so line-width == physical height
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

        SideEffect { onResult(result) }
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
    // In vertical mode the composable occupies its normal slot in the layout but
    // its content is rotated -90°. We achieve this by:
    //   1. Rotating the Box -90° (visual only — layout bounds stay the same).
    //   2. Using a custom `layout` modifier that reports swapped width/height so
    //      the rotated content fills the parent correctly.
    val rotatedModifier = if (isHorizontal) {
        modifier
            .layout { measurable, constraints ->
                // Swap the incoming w/h so the rotated child is measured correctly.
                val placeable = measurable.measure(
                    constraints.copy(
                        minWidth  = constraints.minHeight,
                        maxWidth  = constraints.maxHeight,
                        minHeight = constraints.minWidth,
                        maxHeight = constraints.maxWidth,
                    )
                )
                // Report swapped dimensions back to the parent.
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
                            linesPerParent = r.lineCountPerParent,
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