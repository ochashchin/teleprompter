package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatColorText
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme


data class ScriptAction(
    val icon: ImageVector,
    val onClick: () -> Unit,
)

@Composable
fun ScriptStyleBar(
    modifier: Modifier = Modifier,
    onBoldClick: () -> Unit = {},
    onItalicClick: () -> Unit = {},
    onUnderlineClick: () -> Unit = {},
    onTextColorClick: () -> Unit = {},
    onFillColorClick: () -> Unit = {},
    onMoreClick: () -> Unit = {},
) {
    var P by remember { mutableStateOf(0f) }
    var H by remember { mutableStateOf(0.dp) }
    var W by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = modifier
                .imePadding()

                .height(64.dp)
        ) {
            W = maxWidth.value
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(W / 64f)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(W / 28f)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) { H = maxHeight }

                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .aspectRatio(16f / 64f)
                            .fillMaxHeight()
                    ) { P = maxWidth.value }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight())

                    Box(
                        modifier = Modifier
                            .aspectRatio(16f / 64f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(W / 64f)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.Center),
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(P / 64f)
                            .fillMaxHeight()
                    )
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        val parentWidth = maxWidth
                        val maxBoxes = 6
                        val minBoxSize = 58.dp

                        val boxSize = minOf(maxHeight, parentWidth / maxBoxes)
                            .coerceAtLeast(minBoxSize)

                        val count = (parentWidth / boxSize).toInt()
                            .coerceAtMost(maxBoxes)

// icons + callbacks
                        val actions = listOf(
                            ScriptAction(
                                icon = Icons.Rounded.FormatBold,
                                onClick = onBoldClick,
                            ),
                            ScriptAction(
                                icon = Icons.Rounded.FormatItalic,
                                onClick = onItalicClick,
                            ),
                            ScriptAction(
                                icon = Icons.Rounded.FormatUnderlined,
                                onClick = onUnderlineClick,
                            ),
                            ScriptAction(
                                icon = Icons.Rounded.FormatColorText,
                                onClick = onTextColorClick,
                            ),
                            ScriptAction(
                                icon = Icons.Rounded.FormatColorFill,
                                onClick = onFillColorClick,
                            ),
                        )

                        val totalSlots = count.coerceAtLeast(1)

                        val showOnlyMore = actions.isEmpty()

                        val hasOverflow = actions.size >= totalSlots

                        val visibleActions =
                            when {
                                showOnlyMore -> 0
                                hasOverflow -> totalSlots - 1
                                else -> actions.size
                            }

                        Row(
                            modifier = Modifier
                                .width(maxWidth)
                                .height(maxHeight),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {

                            // visible actions
                            repeat(visibleActions) { index ->

                                val action = actions[index]

                                Box(
                                    modifier = Modifier.size(boxSize),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    IconButton(onClick = action.onClick) {
                                        Icon(
                                            imageVector = action.icon,
                                            contentDescription = "",
                                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }


                            val shouldShowMore =
                                showOnlyMore || hasOverflow || actions.isNotEmpty()

                            if (shouldShowMore) {

                                Box(
                                    modifier = Modifier.size(boxSize),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    IconButton(
                                        onClick = onMoreClick
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.MoreVert,
                                            contentDescription = "More",
                                            tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }

                        }
                    Box(
                        modifier = Modifier
                            .aspectRatio(P / 64f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}


@Composable
private fun NewTaskScreenPreview(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ScriptStyleBar()
        }
    }
}


@Preview(name = "TaskScreen – 412dp", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFull() = NewTaskScreenPreview(darkTheme = false)
