package com.oprojectview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.unit.dp
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.oprojectview.core.LocalPlatformContext
import com.oprojectview.core.MonotonicClock
import com.oprojectview.core.camera.CalibrationData
import com.oprojectview.core.camera.CalibrationOverlay
import com.oprojectview.core.camera.CameraCalibrationBottomSheet
import com.oprojectview.core.camera.CameraControlState
import com.oprojectview.core.camera.DraggableCameraOverlay
import com.oprojectview.core.camera.ExportGalleryOverlay
import com.oprojectview.core.camera.exportVideoToGallery
import com.oprojectview.core.camera.rememberCameraCalibrator
import com.oprojectview.core.camera.rememberPermissionHelper
import com.oprojectview.features.player.LocalPlayerViewModel
import com.oprojectview.features.player.PlayerIntent
import com.oprojectview.frame.FrameViewModel
import com.oprojectview.frame.FrameVmIntent
import com.oprojectview.navigation.PlatformBackHandler
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.cd_back
import kotlinmultiplatform.composeapp.generated.resources.cd_close
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.*
import kotlinx.datetime.toLocalDateTime
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
                    .statusBarsPadding()
                    .width(64.dp)
                    .fillMaxHeight()
                    .align(Alignment.CenterEnd)
            } else {
                Modifier
                    .statusBarsPadding()
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
            modifier = Modifier
                .align(if (isHorizontal) Alignment.CenterStart else Alignment.BottomCenter)
                .navigationBarsPadding()
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
    onSetBackInterceptor: (((() -> Boolean)?) -> Unit) = {},
    onUpdateShouldInterceptBack: (Boolean) -> Unit = {},
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
    val isShowingUpNext = playerState.isShowingUpNext

    val settings = LocalSettings.current
    val context = LocalPlatformContext.current ?: Unit
    val scope = rememberCoroutineScope()

    val overlayItem = DisplayTaskList.first { it.id == 8 }
    val selectedOverlayIndex = displayState.selectedIndex(overlayItem) ?: overlayItem.defaultIndex

    var recordedVideoPath by remember(task.id) { mutableStateOf<String?>(null) }
    var showExportDialog by remember(task.id) { mutableStateOf(false) }
    var isExporting by remember(task.id) { mutableStateOf(false) }
    var isReadingFinished by remember(task.id) { mutableStateOf(false) }
    var cameraOffsetX by remember(task.id) { mutableStateOf(0f) }
    var cameraOffsetY by remember(task.id) { mutableStateOf(0f) }

    var pendingExitAction by remember(task.id) { mutableStateOf<(() -> Unit)?>(null) }
    var exportFilename by remember(task.id) { mutableStateOf("") }

    val triggerExportFlow = {
        val topicName = (playerState.task?.title ?: task.title).trim()
        val sanitizedTopic = topicName
            .lowercase()
            .replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F\\x7F]"), "-")
            .replace(Regex("[\\s_]+"), "-")
            .replace(Regex("-+"), "-")
            .trim('-')
        val finalTopic = sanitizedTopic.ifEmpty { "recorded_video" }

        // Get the current local system time
        val currentMoment = Clock.System.now()
        val localDateTime = currentMoment.toLocalDateTime(TimeZone.currentSystemDefault())

        val dateFormatter = LocalDate.Format {
            monthNumber(padding = Padding.ZERO)
            char('-')
            day(padding = Padding.ZERO)
            char('-')
            year()
        }
        val dateString = dateFormatter.format(localDateTime.date)

        val timeFormatter = LocalTime.Format {
            hour()
            char(':')
            minute()
        }
        val timeString = timeFormatter.format(localDateTime.time)

        val baseName = "$finalTopic-$dateString-$timeString.mp4"

        exportFilename = baseName
        isReadingFinished = true
        recordedVideoPath = null // Reset before waiting for new path
        showExportDialog = true  // Show immediately to eliminate UI delay
        vm.onIntent(PlayerIntent.SetPlaying(false))
    }

    // Track calibration completion
    var calibrationPending by remember(task.id) {
        val isCameraOverlay = selectedOverlayIndex == 1
        val isCalibrated = settings.getBoolean("task_${task.id}_calibrated_active", false)
        mutableStateOf(isCameraOverlay && !isCalibrated)
    }

    var showCalibrationWarning by remember { mutableStateOf(false) }
    var isCalibrationActive by remember { mutableStateOf(false) }
    val isCameraOverlayActive = selectedOverlayIndex == 1 && (!calibrationPending || isCalibrationActive)
    var tempSaveSettings by remember { mutableStateOf(false) }

    val currentOnSetBackInterceptor by rememberUpdatedState(onSetBackInterceptor)
    val currentOnUpdateShouldInterceptBack by rememberUpdatedState(onUpdateShouldInterceptBack)
    LaunchedEffect(isCameraOverlayActive, isReadingFinished, showExportDialog, isExporting, isShowingUpNext) {
        val shouldIntercept = isCameraOverlayActive && !isReadingFinished && !showExportDialog && !isShowingUpNext
        currentOnUpdateShouldInterceptBack(shouldIntercept)
        currentOnSetBackInterceptor {
            if (isCameraOverlayActive && !isReadingFinished && !showExportDialog && !isShowingUpNext) {
                pendingExitAction = { vm.onIntent(PlayerIntent.BackClicked) }
                triggerExportFlow()
                true
            } else if (showExportDialog && !isExporting) {
                showExportDialog = false
                isReadingFinished = false
                val exitAction = pendingExitAction
                if (exitAction != null) {
                    exitAction()
                    pendingExitAction = null
                } else {
                    vm.onIntent(PlayerIntent.BackClicked)
                }
                true
            } else if (isExporting) {
                true
            } else {
                vm.onIntent(PlayerIntent.BackClicked)
                true
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            onSetBackInterceptor(null)
            onUpdateShouldInterceptBack(false)
        }
    }

    val cameraControlState = remember(task.id) {
        CameraControlState().apply {
            val isCalibrated = settings.getBoolean("task_${task.id}_calibrated_active", false)
            if (isCalibrated) {
                zoomRatio = settings.getFloat("task_${task.id}_calibrated_zoom", 1.0f)
                flashEnabled = settings.getBoolean("task_${task.id}_calibrated_flash", false)
                isFrontCamera = settings.getBoolean("task_${task.id}_calibrated_is_front", true)
            } else {
                zoomRatio = 1.0f
                flashEnabled = false
                isFrontCamera = true
            }
        }
    }

    val calibrator = rememberCameraCalibrator(
        onCancel = {
            vm.onIntent(PlayerIntent.BackClicked)
        },
        onLaunch = {
            calibrationPending = true
        },
        onCalibrationCompleted = { calibrationData ->
            settings.putFloat("task_${task.id}_calibrated_zoom", calibrationData.zoomRatio)
            settings.putFloat("task_${task.id}_calibrated_exposure", calibrationData.exposureBias)
            settings.putBoolean("task_${task.id}_calibrated_is_front", calibrationData.isFrontCamera)
            settings.putInt("task_${task.id}_calibrated_width", calibrationData.width)
            settings.putInt("task_${task.id}_calibrated_height", calibrationData.height)
            settings.putInt("task_${task.id}_calibrated_orientation", calibrationData.orientation)
            settings.putBoolean("task_${task.id}_calibrated_flash", calibrationData.flashEnabled)
            settings.putBoolean("task_${task.id}_calibrated_active", tempSaveSettings)

            cameraControlState.zoomRatio = calibrationData.zoomRatio
            cameraControlState.flashEnabled = calibrationData.flashEnabled
            cameraControlState.isFrontCamera = calibrationData.isFrontCamera

            calibrationPending = false
            // Trigger manual replay to run the 5-second countdown spinner before starting
            vm.onIntent(PlayerIntent.ReplayClicked(isManual = true))
        }
    )

    val proceedToCalibration = {
        showCalibrationWarning = false

        // Reset camera control state to defaults for clean recalibration
        cameraControlState.zoomRatio = 1.0f
        cameraControlState.flashEnabled = false
        cameraControlState.isFrontCamera = true
        cameraControlState.zoomOptions = listOf(1.0f)
        cameraControlState.currentZoomIndex = 0

        isCalibrationActive = true
        calibrator.launch()
    }

    var requestAudioPermissionFn by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hasAudioPermissionFn by remember { mutableStateOf<(() -> Boolean)?>(null) }

    var showSettingsDialog by remember { mutableStateOf(false) }

    val permissionHelper = rememberPermissionHelper(
        onCameraResult = { granted, permanentlyDenied ->
            if (granted) {
                val hasAudio = hasAudioPermissionFn?.invoke() ?: false
                if (hasAudio) {
                    proceedToCalibration()
                } else {
                    requestAudioPermissionFn?.invoke()
                }
            } else {
                if (permanentlyDenied) {
                    showSettingsDialog = true
                }
            }
        },
        onAudioResult = { granted ->
            proceedToCalibration()
        }
    )

    LaunchedEffect(permissionHelper) {
        requestAudioPermissionFn = { permissionHelper.requestAudioPermission() }
        hasAudioPermissionFn = { permissionHelper.hasAudioPermission() }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, permissionHelper) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                if (permissionHelper.hasCameraPermission()) {
                    showSettingsDialog = false
                    if (showCalibrationWarning) {
                        showCalibrationWarning = false
                        if (permissionHelper.hasAudioPermission()) {
                            proceedToCalibration()
                        } else {
                            permissionHelper.requestAudioPermission()
                        }
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Intercept/Pause task transition playback if calibration is pending
    LaunchedEffect(task.id, calibrationPending) {
        if (calibrationPending) {
            if (playerState.isPlaying) {
                vm.onIntent(PlayerIntent.SetPlaying(false))
            }
            delay(500)
            showCalibrationWarning = true
        } else {
            showCalibrationWarning = false
            isCalibrationActive = false
        }
    }

    var wasPipActive by remember { mutableStateOf(false) }
    LaunchedEffect(playerState.pipActive) {
        if (wasPipActive && !playerState.pipActive) {
            vm.onIntent(PlayerIntent.SetPlaying(false))
        }
        wasPipActive = playerState.pipActive
    }

    LaunchedEffect(playerState.scrollFraction) {
        if (playerState.scrollFraction == 0f && !showExportDialog && !isExporting) {
            showExportDialog = false
            isReadingFinished = false
        }
    }

    val hPadding = if (playerState.pipActive) 10.dp else 46.dp
    val vPadding = if (playerState.pipActive) 5.dp else 32.dp
    val contentPadding = PaddingValues(start = hPadding, end = hPadding, top = vPadding, bottom = vPadding)

    val upNextSelectedIndex = playerState.upNextSelectedIndex
    val upNextTasks = playerState.upNextTasks
    val allTasks = playerState.allTasks

    val fontFamilyResolver = LocalFontFamilyResolver.current
    val frameStateFlow = frameViewModel?.frameStateFlow?.collectAsState()
    LaunchedEffect(fontFamilyResolver, frameViewModel) {
        frameViewModel?.onIntent(FrameVmIntent.SetFontFamilyResolver(fontFamilyResolver))
    }

    MaterialTheme.colorScheme.surface.value.toLong()
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
        fillColor,
        isMirror
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
                totalDurationMs = playerState.totalDurationMs,
                isMirror = isMirror
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
        val elapsedUs = MonotonicClock.currentTimeUs() - playerState.countdownStartUs
        val elapsedMs = elapsedUs / 1000L
        val remainingMs = (countdownDurationMs - elapsedMs).toInt()
        if (remainingMs > 0) {
            progress.snapTo(remainingMs.toFloat() / countdownDurationMs.toFloat())
            progress.animateTo(
                targetValue   = 0f,
                animationSpec = tween(durationMillis = remainingMs, easing = LinearEasing),
            )
        } else {
            progress.snapTo(0f)
        }
        if (progress.value == 0f) {
            delay(1000L)
            vm.onIntent(PlayerIntent.CountdownDone)
        }
    }

    BoxWithConstraints(
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

        val textAlpha by animateFloatAsState(
            targetValue = if (playerState.isTransitioningToNextTask) 0f else if (isShowingUpNext) 0.5f else 1f,
            animationSpec = if (playerState.isTransitioningToNextTask) tween(200) else if (isShowingUpNext) tween(200) else androidx.compose.animation.core.snap()
        )
        Box(
            modifier = Modifier
                .graphicsLayer { alpha = if (calibrationPending) 0f else if (!playerState.isPlaying || countdownDone) textAlpha else 0f }
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
                modifier            = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
                onAnimationComplete = {
                    if (displayState.isAnimationLoopEnabled()) {
                        val skipDelay = animationMode != AnimationMode.Inline
                        vm.onIntent(PlayerIntent.ReplayClicked(isManual = false, skipDelay = skipDelay))
                    } else {
                        if (isCameraOverlayActive) {
                            triggerExportFlow()
                            vm.onIntent(PlayerIntent.ScrollProgress(1f))
                            vm.onIntent(PlayerIntent.SetPlaying(false))
                        } else {
                            onReadingComplete()
                        }
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
            val showCountdown = (!isLastTask || playerState.hasManuallySelectedUpNext) && playerState.isUpNextCountdownActive
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
                    alpha = if (calibrationPending || countdownDone || progress.value == 0f || isShowingUpNext) 0f else 1f
                    rotationZ = if (isHorizontal) 90f else 0f
                },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeWidth = 8.dp,
        )

        if (!playerState.pipActive && !calibrationPending) {
            PlayerScreenStatic(
                taskId         = task.id,
                toolbarVisible = playerState.toolbarVisible,
                onToolbarTap   = { vm.onIntent(PlayerIntent.ScreenTapped) },
                onBack         = {
                    if (isCameraOverlayActive && !isReadingFinished && !showExportDialog && !isShowingUpNext) {
                        pendingExitAction = { vm.onIntent(PlayerIntent.BackClicked) }
                        triggerExportFlow()
                    } else if (!showExportDialog && !isExporting) {
                        vm.onIntent(PlayerIntent.BackClicked)
                    }
                },
                onClose        = {
                    if (isCameraOverlayActive && !isReadingFinished && !showExportDialog && !isShowingUpNext) {
                        pendingExitAction = { vm.onIntent(PlayerIntent.CloseClicked) }
                        triggerExportFlow()
                    } else if (!showExportDialog && !isExporting) {
                        vm.onIntent(PlayerIntent.CloseClicked)
                    }
                },
                isPlaying      = playerState.isPlaying,
                isFinished     = scrollFraction >= 1f,
                onPlayPauseClick = { vm.onIntent(PlayerIntent.SetPlaying(!playerState.isPlaying)) },
                onReplayClick  = { vm.onIntent(PlayerIntent.ReplayClicked(isManual = true)) },
                fillColor      = fillColor,
            )
        }

        // Draggable camera preview PiP overlay
        val isTransitioning = playerState.isTransitioningToNextTask
        if (isCameraOverlayActive) {
            val isRecording = !isShowingUpNext && !isReadingFinished
            val isOverlayVisible = !showCalibrationWarning && !isShowingUpNext && !showExportDialog && !isTransitioning
            DraggableCameraOverlay(
                calibrationData = CalibrationData(
                    zoomRatio = cameraControlState.zoomRatio,
                    exposureBias = 0.0f,
                    isFrontCamera = cameraControlState.isFrontCamera,
                    width = 1920,
                    height = 1080,
                    orientation = 0,
                    flashEnabled = cameraControlState.flashEnabled
                ),
                isRecording = isRecording,
                onVideoSaved = { path ->
                    recordedVideoPath = path
                    if (isReadingFinished && path.isEmpty()) {
                        // If the recording was aborted or failed entirely, auto-dismiss the dialog
                        if (showExportDialog && !isExporting) {
                            showExportDialog = false
                            isReadingFinished = false
                            val exitAction = pendingExitAction
                            if (exitAction != null) {
                                exitAction()
                                pendingExitAction = null
                            } else {
                                onReadingComplete()
                            }
                        }
                    }
                },
                parentWidth = maxWidth,
                parentHeight = maxHeight,
                isExpanded = calibrationPending,
                offsetX = cameraOffsetX,
                offsetY = cameraOffsetY,
                onDrag = { dx, dy ->
                    cameraOffsetX += dx
                    cameraOffsetY += dy
                },
                onZoomStateAvailable = { minZoom, maxZoom ->
                    val dynamicZooms = mutableListOf<Float>()
                    if (minZoom < 1.0f) dynamicZooms.add(0.5f)
                    dynamicZooms.add(1f)
                    if (maxZoom >= 2.0f) dynamicZooms.add(2f)
                    if (maxZoom >= 5.0f) dynamicZooms.add(5f)
                    cameraControlState.zoomOptions = dynamicZooms

                    val selectedZoom = cameraControlState.zoomOptions.getOrNull(cameraControlState.currentZoomIndex) ?: 1f
                    val newIndex = dynamicZooms.indexOf(selectedZoom)
                    cameraControlState.currentZoomIndex = if (newIndex >= 0) newIndex else dynamicZooms.indexOf(1f).coerceAtLeast(0)
                    cameraControlState.zoomRatio = dynamicZooms.getOrNull(cameraControlState.currentZoomIndex) ?: 1f
                },
                onTorchStateAvailable = { isSupported ->
                    cameraControlState.isTorchSupported = isSupported
                },
                taskId = task.id,
                visible = isOverlayVisible,
                modifier = Modifier.align(Alignment.BottomStart)
            )
        }

        // Export Dialog
        ExportGalleryOverlay(
            visible = showExportDialog,
            onConfirm = {
                isExporting = true
                scope.launch {
                    // Wait up to 5 seconds for AVFoundation/CameraX to finish flushing the file
                    var waitCount = 0
                    while (recordedVideoPath == null && waitCount < 50) {
                        delay(100)
                        waitCount++
                    }

                    recordedVideoPath?.let { path ->
                        if (path.isNotEmpty()) {
                            exportVideoToGallery(path, context, exportFilename)
                        }
                    }
                    isExporting = false
                    showExportDialog = false
                    isReadingFinished = false
                    val exitAction = pendingExitAction
                    if (exitAction != null) {
                        exitAction()
                        pendingExitAction = null
                    } else {
                        onReadingComplete()
                    }
                }
            },
            onDismiss = {
                showExportDialog = false
                isReadingFinished = false
                val exitAction = pendingExitAction
                if (exitAction != null) {
                    exitAction()
                    pendingExitAction = null
                } else {
                    onReadingComplete()
                }
            },
            isExporting = isExporting,
            exportFilename = exportFilename,
            fillColor = fillColor,
            isHorizontal = isHorizontal
        )

        AnimatedVisibility(
            visible = showCalibrationWarning,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(300))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        // Camera calibration bottom sheet warning
        if (showCalibrationWarning) {
            CameraCalibrationBottomSheet(
                expanded = true,
                onDismissRequest = {
                    showCalibrationWarning = false
                    isCalibrationActive = false
                    vm.onIntent(PlayerIntent.BackClicked) // Back gesture/cancel skips calibration, goes back to display screen
                },
                onConfirm = { saveChecked ->
                    tempSaveSettings = saveChecked
                    if (permissionHelper.hasCameraPermission()) {
                        if (permissionHelper.hasAudioPermission()) {
                            proceedToCalibration()
                        } else {
                            permissionHelper.requestAudioPermission()
                        }
                    } else {
                        permissionHelper.requestCameraPermission()
                    }
                }
            )
        }

        if (showSettingsDialog) {
            CameraPermissionRequiredDialog(
                onDismissRequest = { showSettingsDialog = false },
                onSettings = {
                    showSettingsDialog = false
                    permissionHelper.openSettings()
                }
            )
        }

        CalibrationOverlay(
            visible = calibrationPending && isCalibrationActive,
            onDismiss = {
                calibrationPending = false
                isCalibrationActive = false
                vm.onIntent(PlayerIntent.BackClicked)
            },
            onCalibrationCompleted = { calibrationData ->
                settings.putFloat("task_${task.id}_calibrated_zoom", calibrationData.zoomRatio)
                settings.putFloat("task_${task.id}_calibrated_exposure", calibrationData.exposureBias)
                settings.putBoolean("task_${task.id}_calibrated_is_front", calibrationData.isFrontCamera)
                settings.putInt("task_${task.id}_calibrated_width", calibrationData.width)
                settings.putInt("task_${task.id}_calibrated_height", calibrationData.height)
                settings.putInt("task_${task.id}_calibrated_orientation", calibrationData.orientation)
                settings.putBoolean("task_${task.id}_calibrated_flash", calibrationData.flashEnabled)
                settings.putBoolean("task_${task.id}_calibrated_active", tempSaveSettings)

                cameraControlState.zoomRatio = calibrationData.zoomRatio
                cameraControlState.flashEnabled = calibrationData.flashEnabled
                cameraControlState.isFrontCamera = calibrationData.isFrontCamera

                calibrationPending = false
                isCalibrationActive = false
                vm.onIntent(PlayerIntent.ReplayClicked(isManual = true))
            },
            cameraControlState = cameraControlState,
            isHorizontal = isHorizontal,
            modifier = Modifier.fillMaxSize()
        )

    }
}

@Composable
fun RotatedLayout(
    rotate90: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
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