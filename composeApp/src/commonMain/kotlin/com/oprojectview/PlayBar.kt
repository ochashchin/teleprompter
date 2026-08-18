package com.oprojectview

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
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
    buttonModifier: Modifier = Modifier,
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
                    Icon(
                        imageVector = icon,
                        contentDescription = stringResource(contentDescRes)
                    )
                },
                onClick = onClick,
                focused = false,
                rotation = iconRotation,
                fillColor = fillColor,
                modifier = buttonModifier,
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
    modifier: Modifier = Modifier,
) {
    if (icon != null) {
        var isDpadFocused by remember { mutableStateOf(false) }
        val inverseOnSurface = MaterialTheme.colorScheme.inverseOnSurface
        val containerColor = remember(inverseOnSurface) {
            inverseOnSurface.copy(alpha = 0.5f)
        }

        IconButton(
            modifier = Modifier
                .aspectRatio(1f)
                .fillMaxHeight()
                .padding(4.dp)
                .graphicsLayer { rotationZ = rotation }
                .onFocusChanged { isDpadFocused = it.isFocused }
                .then(
                    if (isDpadFocused || focused)
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    else Modifier
                )
                .then(modifier),
            onClick = onClick,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = containerColor,
                contentColor =
                    if (isDpadFocused || focused)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurface
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
