package com.example.kotlinmultiplatform

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreenStatic(
    taskId: Int,
    toolbarVisible: Boolean,
    onToolbarTap: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
) {
    val displayState    = rememberDisplayTaskState(taskId)
    val orientationItem = DisplayTaskList.first { it.id == 2 }
    val isHorizontal    =
        (displayState.selectedIndex(orientationItem) ?: orientationItem.defaultIndex) == 1

    Box(Modifier.fillMaxSize()) {
        if (toolbarVisible.not()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication        = null,
                        onClick           = onToolbarTap,
                    )
            )
        }

        AnimatedVisibility(
            visible  = toolbarVisible,
            enter    = fadeIn(tween(200)),
            exit     = fadeOut(tween(200)),
            modifier = if (isHorizontal) {
                Modifier
                    .fillMaxHeight()
                    .width(64.dp)
                    .align(Alignment.CenterEnd)
            } else {
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .align(Alignment.TopCenter)
            },
        ) {
            ToolBar(
                title           = "",
                onLeadingClick  = onBack,
                onTrailingClick = onClose,
                leadingIcon = {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector        = Icons.Rounded.Close,
                        contentDescription = "Close",
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                },
                focusedLeading  = true,
                focusedTrailing = true,
                isHorizontal    = isHorizontal,
            )
        }
    }
}

// ── PlayerScreenBody ──────────────────────────────────────────────────────────
//
// Resolves all display settings from DisplayTaskState using the same logic as
// DisplayScreenBody. No cross-screen data class is needed — TextStyle is a
// Compose runtime value (it references MaterialTheme) and must be derived here,
// not passed in as a parameter. Task.id is the only thing needed to reconstruct
// the full persisted state.

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreenBody(
    task: Task,
    modifier: Modifier = Modifier,
    onReadingComplete: () -> Unit = {},
) {
    val displayState = rememberDisplayTaskState(task.id)

    // ── Resolve settings from persisted state ─────────────────────────────────

    val textSizeItem    = DisplayTaskList.first { it.id == 1 }
    val orientationItem = DisplayTaskList.first { it.id == 2 }
    val speedItem       = DisplayTaskList.first { it.id == 3 }
    val animationItem   = DisplayTaskList.first { it.id == 4 }
    val transitionItem  = DisplayTaskList.first { it.id == 5 }
    val distortionItem  = DisplayTaskList.first { it.id == 6 }
    val mirrorItem      = DisplayTaskList.first { it.id == 7 }

    val selectedSizeIndex        = displayState.selectedIndex(textSizeItem)        ?: textSizeItem.defaultIndex
    val selectedOrientationIndex = displayState.selectedIndex(orientationItem)     ?: orientationItem.defaultIndex
    val selectedSpeedIndex       = displayState.selectedIndex(speedItem)           ?: speedItem.defaultIndex
    val selectedAnimationIndex   = displayState.selectedIndex(animationItem)       ?: animationItem.defaultIndex
    val selectedTransitionIndex  = displayState.selectedIndex(transitionItem)      ?: transitionItem.defaultIndex
    val selectedDistortionIndex  = displayState.selectedIndex(distortionItem)      ?: distortionItem.defaultIndex
    val selectedMirrorIndex      = displayState.selectedIndex(mirrorItem)          ?: mirrorItem.defaultIndex

    // Orientation: index 0 = Vertical, 1 = Horizontal
    val isHorizontal   = selectedOrientationIndex == 1
    val animationMode  = animationModeOf(selectedAnimationIndex)
    val transitionMode = transitionModeOf(selectedTransitionIndex)
    val distortionMode = distortionValueOf(selectedDistortionIndex)
    // Mirror: index 0 = Disabled, 1 = Enabled
    val isMirror       = selectedMirrorIndex == 1

    val (_, fontSizeDp, lineHeightDp) = NORMAL_SIZES.getOrNull(selectedSizeIndex)
        ?: NORMAL_SIZES[1]

    val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
        fontSize   = fontSize(fontSizeDp),
        lineHeight = fontSize(lineHeightDp),
        color      = MaterialTheme.colorScheme.onSurface,
    )

    val wpm            = speedIndexToWpm(selectedSpeedIndex)
    val previewPadding = 10.dp

    // ── Countdown state ───────────────────────────────────────────────────────

    val countdownDurationMs = 3000L
    val progress            = remember { Animatable(1f) }
    var countdownFinished   by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue   = 0f,
            animationSpec = tween(durationMillis = countdownDurationMs.toInt(), easing = LinearEasing),
        )
        countdownFinished = true
    }

    // ── Full-screen player ────────────────────────────────────────────────────

    Box(
        modifier = modifier
            .fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {

        Box(
            modifier = modifier
                .graphicsLayer { alpha = if (countdownFinished) 1f else 0f }
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            DisplayTextBar(
                task                = task,
                wpm                 = wpm,
                textStyle           = textStyle,
                padding             = previewPadding,
                isHorizontal        = isHorizontal,
                isMirror            = isMirror,
                distortionMode      = distortionMode,
                animationMode       = animationMode,
                transitionMode      = transitionMode,
                preview             = false,
                modifier            = modifier,
                onAnimationComplete = onReadingComplete,
            )
        }

        CircularProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.size(100.dp)
                .graphicsLayer {
                    alpha = if (countdownFinished) 0f else 1f
                    rotationZ = if (isHorizontal) 90f else 0f
                },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 8.dp,
        )
    }
}