package com.oprojectview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oprojectview.theme.AppTheme

@Composable
fun ToolBar(
    modifier: Modifier = Modifier,
    title: String,
    onTrailingClick: () -> Unit = {},
    onLeadingClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    focusedLeading: Boolean = false,
    focusedTrailing: Boolean = false,
    isHorizontal: Boolean = false,
    fillColor: Color? = null,
) {
    val toolbarModifier = if (isHorizontal) {
        Modifier
            .fillMaxHeight()
            .width(64.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .height(64.dp)
    }

    Box(
        modifier = toolbarModifier
    ) {
        val titleFontSize = fontSize(24.dp)

        Text(
            modifier = Modifier.align(Alignment.Center),
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            fontSize = titleFontSize,
            lineHeight = titleFontSize,
            textAlign = TextAlign.Center
        )

        val containerModifier = if (isHorizontal) {
            Modifier
                .fillMaxHeight()
                .width(48.dp)
                .padding(vertical = 16.dp)
                .align(Alignment.Center)
        } else {
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
                .align(Alignment.Center)
        }

        val iconRotation = if (isHorizontal) 90f else 0f

        if (isHorizontal) {
            Column(
                modifier = containerModifier,
                verticalArrangement = Arrangement.Center,
            ) {
                ToolbarIcon(
                    icon = leadingIcon,
                    onClick = onLeadingClick ?: {},
                    focused = focusedLeading,
                    rotation = iconRotation,
                    fillColor = fillColor
                )
                Spacer(Modifier.weight(1f))
                ToolbarIcon(
                    icon = trailingIcon,
                    onClick = onTrailingClick,
                    focused = focusedTrailing,
                    rotation = iconRotation,
                    fillColor = fillColor
                )
            }
        } else {
            Row(
                modifier = containerModifier,
                horizontalArrangement = Arrangement.Center,
            ) {
                ToolbarIcon(
                    icon = leadingIcon,
                    onClick = onLeadingClick ?: {},
                    focused = focusedLeading,
                    rotation = iconRotation,
                    fillColor = fillColor
                )
                Spacer(Modifier.weight(1f))
                ToolbarIcon(
                    icon = trailingIcon,
                    onClick = onTrailingClick,
                    focused = focusedTrailing,
                    rotation = iconRotation,
                    fillColor = fillColor
                )
            }
        }
    }
}

@Composable
private fun ToolbarIcon(
    icon: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    focused: Boolean,
    rotation: Float = 0f,
    fillColor: Color? = null,
) {
    if (icon != null) {
        val defaultColor = MaterialTheme.colorScheme.inverseOnSurface
        val containerColor = remember(focused, fillColor, defaultColor) {
            if (!focused) {
                Color.Transparent
            } else if (fillColor != null) {
                calculateFocusedContainerColor(fillColor)
            } else {
                defaultColor
            }
        }

        IconButton(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxHeight()
                .graphicsLayer { rotationZ = rotation },
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor =
                    if (focused)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            icon()
        }
    } else {
        Spacer(
            Modifier
                .aspectRatio(1f)
                .fillMaxHeight()
        )
    }
}

@Preview(
    name = "ToolBar – 412dp (design width)",
    showBackground = true,
    widthDp = 412,
)
@Composable
private fun PreviewFull() {
    AppTheme(darkTheme = false) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ToolBar(
                title = "Toolbar",
                focusedLeading = true,
                focusedTrailing = true,
            )
        }
    }
}

@Preview(
    name = "ToolBar – 320dp (compact)",
    showBackground = true,
    widthDp = 320,
)
@Composable
private fun PreviewCompact() {
    AppTheme(darkTheme = true) {
        Column(modifier = Modifier.fillMaxWidth()) {
            ToolBar(
                title = "Toolbar",
                focusedLeading = true,
                focusedTrailing = true,
            )
        }
    }
}

fun calculateFocusedContainerColor(bg: Color): Color {
    val L_bg = bg.luminance()
    val isLightBg = L_bg > 0.8f
    val R = if (L_bg < 0.5f) 1.45f else 1.25f
    val L_target = if (isLightBg) {
        (L_bg + 0.05f) / R - 0.05f
    } else {
        R * (L_bg + 0.05f) - 0.05f
    }

    val targetColor = if (isLightBg) Color.Black else Color.White

    var low = 0f
    var high = 1f
    var bestC = targetColor

    for (i in 0..15) {
        val mid = (low + high) / 2f
        val rC = bg.red + (targetColor.red - bg.red) * mid
        val gC = bg.green + (targetColor.green - bg.green) * mid
        val bC = bg.blue + (targetColor.blue - bg.blue) * mid
        val c = Color(rC, gC, bC, 1f)

        val rBlend = 0.2f * c.red + 0.8f * bg.red
        val gBlend = 0.2f * c.green + 0.8f * bg.green
        val bBlend = 0.2f * c.blue + 0.8f * bg.blue
        val blended = Color(rBlend, gBlend, bBlend, 1f)

        val L_blend = blended.luminance()
        bestC = c

        if (isLightBg) {
            if (L_blend < L_target) high = mid else low = mid
        } else {
            if (L_blend > L_target) high = mid else low = mid
        }
    }

    return bestC.copy(alpha = 0.2f)
}
