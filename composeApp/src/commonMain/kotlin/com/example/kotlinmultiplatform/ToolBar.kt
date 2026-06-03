package com.example.kotlinmultiplatform

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme


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
                    rotation = iconRotation
                )
                Spacer(Modifier.weight(1f))
                ToolbarIcon(
                    icon = trailingIcon,
                    onClick = onTrailingClick,
                    focused = focusedTrailing,
                    rotation = iconRotation
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
                    rotation = iconRotation
                )
                Spacer(Modifier.weight(1f))
                ToolbarIcon(
                    icon = trailingIcon,
                    onClick = onTrailingClick,
                    focused = focusedTrailing,
                    rotation = iconRotation
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
) {
    if (icon != null) {
        IconButton(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxHeight()
                .graphicsLayer { rotationZ = rotation },
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor =
                    if (focused)
                        MaterialTheme.colorScheme.inverseOnSurface
                    else
                        Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurface
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