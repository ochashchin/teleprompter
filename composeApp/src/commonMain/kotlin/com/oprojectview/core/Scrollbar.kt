package com.oprojectview.core

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme

fun Modifier.verticalScrollbar(
    state: ScrollState,
    width: Dp = 4.dp,
    paddingEnd: Dp = 4.dp,
    paddingTop: Dp = 12.dp,
    paddingBottom: Dp = 4.dp,
    color: Color? = null
): Modifier = composed {
    val scrollbarColor = color ?: MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    val targetAlpha = if (state.isScrollInProgress) 1f else 0f
    val duration = if (state.isScrollInProgress) 150 else 500

    val alpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = duration),
        label = "ScrollbarAlpha"
    )

    drawWithContent {
        drawContent()
        
        val needDrawScrollbar = state.isScrollInProgress || alpha > 0.0f
        if (needDrawScrollbar && state.maxValue > 0) {
            val paddingTopPx = paddingTop.toPx()
            val paddingBottomPx = paddingBottom.toPx()
            val trackAvailableHeight = size.height - paddingTopPx - paddingBottomPx

            val visibleHeight = size.height
            val totalContentHeight = state.maxValue.toFloat() + visibleHeight
            
            // Proportional thumb height
            // We ensure a minimum thumb height (e.g., 24.dp) so it's always draggable/visible
            val minThumbHeightPx = 24.dp.toPx()
            val rawThumbHeight = (visibleHeight / totalContentHeight) * trackAvailableHeight
            val thumbHeight = rawThumbHeight.coerceAtLeast(minThumbHeightPx)
            
            // Available space for the thumb to travel
            val trackHeight = trackAvailableHeight - thumbHeight
            
            // Calculate scroll fraction
            val scrollFraction = state.value.toFloat() / state.maxValue.toFloat()
            val thumbOffsetY = paddingTopPx + (scrollFraction * trackHeight)
            
            val scrollbarWidthPx = width.toPx()
            val paddingEndPx = paddingEnd.toPx()
            
            drawRoundRect(
                color = scrollbarColor,
                topLeft = Offset(size.width - scrollbarWidthPx - paddingEndPx, thumbOffsetY),
                size = Size(scrollbarWidthPx, thumbHeight),
                alpha = alpha,
                cornerRadius = CornerRadius(scrollbarWidthPx / 2, scrollbarWidthPx / 2)
            )
        }
    }
}
