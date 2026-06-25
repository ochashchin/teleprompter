package com.oprojectview

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import com.oprojectview.features.player.LocalPlayerViewModel
import com.oprojectview.features.player.PlayerIntent
import com.oprojectview.frame.FrameViewModel
import com.oprojectview.frame.FrameVmIntent
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.cd_back
import kotlinmultiplatform.composeapp.generated.resources.cd_close
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
fun PlayerScreenStatic(
    taskId: Int,
    toolbarVisible: Boolean,
    onToolbarTap: () -> Unit,
    onBack: () -> Unit,
    onClose: () -> Unit,
    isPlaying: Boolean,
    isFinished: Boolean,
    onPlayPauseClick: () -> Unit,
    onReplayClick: () -> Unit,
    fillColor: Color = Color.Transparent,
) {
    val displayState    = rememberDisplayTaskState(taskId)
    val orientationItem = DisplayTaskList.first { it.id == 2 }
    val isHorizontal    =
        (displayState.selectedIndex(orientationItem) ?: orientationItem.defaultIndex) == 1

    Box(Modifier.fillMaxSize()) {
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
                        contentDescription = stringResource(Res.string.cd_back),
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                },
                trailingIcon = {
                    Icon(
                        imageVector        = Icons.Rounded.Close,
                        contentDescription = stringResource(Res.string.cd_close),
                        tint               = MaterialTheme.colorScheme.onSurface,
                    )
                },
                focusedLeading  = true,
                focusedTrailing = true,
                isHorizontal    = isHorizontal,
                fillColor       = fillColor,
            )
        }

        AnimatedVisibility(
            visible = toolbarVisible,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(if (isHorizontal) Alignment.CenterStart else Alignment.BottomCenter)
        ) {
            PlayBar(
                isPlaying = isPlaying,
                isFinished = isFinished,
                onPlayPauseClick = onPlayPauseClick,
                onReplayClick = onReplayClick,
                isHorizontal = isHorizontal,
                fillColor = fillColor
            )
        }
    }
}

// ── PlayerScreenBody ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlayerScreenBody(
    task: Task,
    modifier: Modifier = Modifier,
    styleSpans: List<StyleSpan> = emptyList(),
    fillColor: Color = Color.Transparent,
    onReadingComplete: () -> Unit = {},
    // Injected for PiP lifecycle and timing synchronization.
    frameViewModel: FrameViewModel? = null,
) {
    KeepScreenAwake()

    val displayState = rememberDisplayTaskState(task.id)

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

    val isHorizontal   = selectedOrientationIndex == 1
    val animationMode  = animationModeOf(selectedAnimationIndex)
    val transitionMode = transitionModeOf(selectedTransitionIndex)
    val distortionMode = distortionValueOf(selectedDistortionIndex)
    val isMirror       = selectedMirrorIndex == 1

    val (_, fontSizeDp, lineHeightDp) = NORMAL_SIZES.getOrNull(selectedSizeIndex)
        ?: NORMAL_SIZES[1]

    val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
        fontSize   = fontSize(fontSizeDp),
        lineHeight = fontSize(lineHeightDp),
        color      = MaterialTheme.colorScheme.onSurface,
    )

    val wpm            = speedIndexToWpm(selectedSpeedIndex)

    val vm             = LocalPlayerViewModel.current
    val playerState    by vm.state.collectAsState()
    val countdownDone  = playerState.countdownDone
    val scrollFraction = playerState.scrollFraction
    
    val hPadding = if (playerState.pipActive) 10.dp else 46.dp
    val vPadding = if (playerState.pipActive) 5.dp else 32.dp
    val contentPadding = androidx.compose.foundation.layout.PaddingValues(start = hPadding, end = hPadding, top = vPadding, bottom = vPadding)

    val isShowingUpNext = playerState.isShowingUpNext
    val upNextSelectedIndex = playerState.upNextSelectedIndex
    val upNextTasks = playerState.upNextTasks
    val allTasks = playerState.allTasks
    
    val fontFamilyResolver = LocalFontFamilyResolver.current
    val frameStateFlow = frameViewModel?.frameStateFlow?.collectAsState()
    LaunchedEffect(fontFamilyResolver, frameViewModel) {
        if (frameViewModel != null) {
            frameViewModel.onIntent(FrameVmIntent.SetFontFamilyResolver(fontFamilyResolver))
        }
    }

    val defaultFill = MaterialTheme.colorScheme.surface.value.toLong()
    // Sync play position, options, and duration to FrameViewModel
    LaunchedEffect(
        playerState.task?.id,
        playerState.isPlaying,
        styleSpans,
        animationMode,
        transitionMode,
        playerState.playbackStartUs,
        playerState.pausedElapsedUs,
        playerState.totalDurationMs,
        fillColor
    ) {
        val t = playerState.task ?: return@LaunchedEffect
        frameViewModel?.onIntent(
            FrameVmIntent.SyncPlayerState(
                isPlaying = playerState.isPlaying,
                countdownDone = playerState.countdownDone,
                countdownStartUs = playerState.countdownStartUs,
                pausedCountdownElapsedUs = playerState.pausedCountdownElapsedUs,
                scrollFraction = 0f,
                scriptText = t.description,
                styleSpans = styleSpans,
                fillColorVal = fillColor.value.toLong(),
                animationMode = animationMode,
                transitionMode = transitionMode,
                playbackStartUs = playerState.playbackStartUs,
                pausedElapsedUs = playerState.pausedElapsedUs,
                totalDurationMs = playerState.totalDurationMs
            )
        )
    }

    val countdownDurationMs = 5000L
    val progress            = remember { Animatable(if (countdownDone) 0f else 1f) }

    LaunchedEffect(countdownDone, playerState.isPlaying) {
        if (countdownDone) {
            progress.snapTo(0f)
            return@LaunchedEffect
        }
        if (!playerState.isPlaying) {
            progress.stop()
            return@LaunchedEffect
        }
        val elapsedUs = com.oprojectview.core.MonotonicClock.currentTimeUs() - playerState.countdownStartUs
        val elapsedMs = elapsedUs / 1000L
        val remainingMs = (countdownDurationMs - elapsedMs).toInt()
        if (remainingMs > 0) {
            progress.snapTo(remainingMs.toFloat() / countdownDurationMs.toFloat())
            progress.animateTo(
                targetValue   = 0f,
                animationSpec = androidx.compose.animation.core.tween(durationMillis = remainingMs, easing = androidx.compose.animation.core.LinearEasing),
            )
        } else {
            progress.snapTo(0f)
        }
        if (progress.value == 0f) {
            delay(1000L)
            vm.onIntent(PlayerIntent.CountdownDone)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { size ->
                // Don't overwrite PiP-owned dimensions while PiP is active
                val pipOwnsSize = frameStateFlow?.value?.pipSizeOverrideActive == true
                if (!pipOwnsSize) {
                    frameViewModel?.onIntent(
                        FrameVmIntent.SetFrameSize(
                            widthPx  = size.width.coerceAtLeast(1),
                            heightPx = size.height.coerceAtLeast(1),
                        )
                    )
                }
            }
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication        = null,
                onClick           = { vm.onIntent(PlayerIntent.ScreenTapped) },
            ),
        contentAlignment = Alignment.Center,
    ) {

        val textAlpha by androidx.compose.animation.core.animateFloatAsState(
            targetValue = if (playerState.isTransitioningToNextTask) 0f else if (isShowingUpNext) 0.5f else 1f,
            animationSpec = if (playerState.isTransitioningToNextTask) androidx.compose.animation.core.tween(200) else if (isShowingUpNext) androidx.compose.animation.core.tween(200) else androidx.compose.animation.core.snap()
        )
        Box(
            modifier = modifier
                .graphicsLayer { alpha = if (countdownDone) textAlpha else 0f }
                .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            DisplayTextBar(
                task                = task,
                wpm                 = wpm,
                textStyle           = textStyle,
                contentPadding      = contentPadding,
                isHorizontal        = isHorizontal,
                isMirror            = isMirror,
                distortionMode      = distortionMode,
                animationMode       = animationMode,
                transitionMode      = transitionMode,
                styleSpans          = styleSpans,
                fillColor           = fillColor,
                preview             = false,
                countdownDone       = countdownDone,
                initialScrollFraction = scrollFraction,
                onScrollFraction    = { vm.onIntent(PlayerIntent.ScrollProgress(it)) },
                modifier            = modifier,
                onAnimationComplete = {
                    if (displayState.isAnimationLoopEnabled()) {
                        val skipDelay = animationMode != AnimationMode.Inline
                        vm.onIntent(PlayerIntent.ReplayClicked(isManual = false, skipDelay = skipDelay))
                    } else {
                        onReadingComplete()
                    }
                },
            )
        }

        AnimatedVisibility(
            visible = isShowingUpNext,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            val isLastTask = task.id == allTasks.lastOrNull()?.id
            val showCountdown = !isLastTask || playerState.hasManuallySelectedUpNext
            RotatedLayout(
                rotate90 = isHorizontal
            ) {
                UpNextList(
                    tasks = upNextTasks,
                    selectedIndex = upNextSelectedIndex,
                    onItemSelected = { vm.onIntent(PlayerIntent.OnUpNextItemSelected(it)) },
                    onCountdownFinished = { vm.onIntent(PlayerIntent.UpNextCountdownDone) },
                    showCountdown = showCountdown,
                    fillColor = fillColor
                )
            }
        }

        CircularProgressIndicator(
            progress = { progress.value },
            modifier = Modifier.size(100.dp)
                .graphicsLayer {
                    alpha = if (countdownDone || progress.value == 0f || isShowingUpNext) 0f else 1f
                    rotationZ = if (isHorizontal) 90f else 0f
                },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 8.dp,
        )

    }
}

@Composable
fun RotatedLayout(
    rotate90: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    androidx.compose.ui.layout.Layout(
        content = content,
        modifier = modifier.graphicsLayer {
            rotationZ = if (rotate90) 90f else 0f
        }
    ) { measurables, constraints ->
        val childConstraints = if (rotate90) {
            androidx.compose.ui.unit.Constraints(
                minWidth = constraints.minHeight,
                maxWidth = constraints.maxHeight,
                minHeight = constraints.minWidth,
                maxHeight = constraints.maxWidth
            )
        } else {
            constraints
        }
        val placeable = measurables.first().measure(childConstraints)
        
        if (rotate90) {
            layout(placeable.height, placeable.width) {
                val x = -(placeable.width - placeable.height) / 2
                val y = -(placeable.height - placeable.width) / 2
                placeable.place(x, y)
            }
        } else {
            layout(placeable.width, placeable.height) {
                placeable.place(0, 0)
            }
        }
    }
}