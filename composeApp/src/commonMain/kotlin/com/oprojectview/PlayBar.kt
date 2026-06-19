package com.oprojectview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.cd_pause
import kotlinmultiplatform.composeapp.generated.resources.cd_play
import kotlinmultiplatform.composeapp.generated.resources.cd_replay
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayBar(
    modifier: Modifier = Modifier,
    isPlaying: Boolean,
    isFinished: Boolean,
    onPlayPauseClick: () -> Unit,
    onReplayClick: () -> Unit,
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
        modifier = modifier.then(toolbarModifier)
    ) {
        val containerModifier = if (isHorizontal) {
            Modifier
                .fillMaxHeight()
                .width(48.dp)
                .align(Alignment.Center)
        } else {
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .align(Alignment.Center)
        }

        val iconRotation = if (isHorizontal) 90f else 0f

        Box(
            modifier = containerModifier,
            contentAlignment = Alignment.Center
        ) {
            val (icon, onClick, contentDescRes) = when {
                isFinished -> Triple(Icons.Rounded.Replay, onReplayClick, Res.string.cd_replay)
                isPlaying -> Triple(Icons.Rounded.Pause, onPlayPauseClick, Res.string.cd_pause)
                else -> Triple(Icons.Rounded.PlayArrow, onPlayPauseClick, Res.string.cd_play)
            }

            PlayBarIcon(
                icon = {
                    androidx.compose.material3.Icon(
                        imageVector = icon,
                        contentDescription = stringResource(contentDescRes)
                    )
                },
                onClick = onClick,
                focused = true,
                rotation = iconRotation,
                fillColor = fillColor
            )
        }
    }
}

@Composable
private fun PlayBarIcon(
    icon: (@Composable () -> Unit)?,
    onClick: () -> Unit,
    focused: Boolean,
    rotation: Float = 0f,
    fillColor: Color? = null,
) {
    if (icon != null) {
        val defaultColor = MaterialTheme.colorScheme.inverseOnSurface
        val containerColor = androidx.compose.runtime.remember(focused, fillColor, defaultColor) {
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

@Preview
@Composable
private fun PlayBarPreview() {
    MaterialTheme {
        Surface {
            Column(modifier = Modifier.padding(16.dp)) {
                // Vertical orientation: play -> pause -> finished
                PlayBar(
                    isPlaying = false,
                    isFinished = false,
                    onPlayPauseClick = {},
                    onReplayClick = {}
                )
                Spacer(Modifier.height(8.dp))
                PlayBar(
                    isPlaying = true,
                    isFinished = false,
                    onPlayPauseClick = {},
                    onReplayClick = {}
                )
                Spacer(Modifier.height(8.dp))
                PlayBar(
                    isPlaying = false,
                    isFinished = true,
                    onPlayPauseClick = {},
                    onReplayClick = {}
                )
            }
        }
    }
}
