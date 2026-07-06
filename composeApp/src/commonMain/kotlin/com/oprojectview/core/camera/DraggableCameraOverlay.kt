package com.oprojectview.core.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.util.lerp


@Composable
fun DraggableCameraOverlay(
    calibrationData: CalibrationData,
    isRecording: Boolean,
    onVideoSaved: (String) -> Unit,
    offsetX: Float,
    offsetY: Float,
    onDrag: (Float, Float) -> Unit,
    parentWidth: Dp,
    parentHeight: Dp,
    isExpanded: Boolean = false,
    onZoomStateAvailable: (Float, Float) -> Unit = { _, _ -> },
    onTorchStateAvailable: (Boolean) -> Unit = {},
    taskId: Int,
    visible: Boolean = true,
    modifier: Modifier = Modifier
) {
    val pAnimatable = remember { Animatable(if (isExpanded) 0f else 1f) }
    
    LaunchedEffect(isExpanded) {
        pAnimatable.animateTo(if (isExpanded) 0f else 1f, tween(400))
    }

    val p = pAnimatable.value
    
    var isPreviewReady by remember { mutableStateOf(false) }
    val previewAlpha by animateFloatAsState(
        targetValue = if (isExpanded || isPreviewReady) 1f else 0f,
        animationSpec = tween(200)
    )
    
    val density = LocalDensity.current
    val systemBarsInsets = WindowInsets.systemBars
    val bottomInsetPx = systemBarsInsets.getBottom(density).let {
        if (it == 0 && isIos()) {
            with(density) { 34.dp.toPx().toInt() }
        } else {
            it
        }
    }
    val leftInsetPx = systemBarsInsets.getLeft(density, layoutDirection = LocalLayoutDirection.current)
    
    val expandedOffsetX = 0f
    val expandedOffsetY = 0f
    
    val pipOffsetX = leftInsetPx.toFloat() + offsetX
    val pipOffsetY = -bottomInsetPx.toFloat() + offsetY
    
    val currentWidth = lerp(parentWidth, 128.dp, p)
    val currentHeight = lerp(parentHeight, 134.dp, p)
    val currentPaddingStart = lerp(0.dp, 32.dp, p)
    val currentPaddingBottom = lerp(0.dp, 8.dp, p)
    val currentCorner = lerp(0.dp, 16.dp, p)
    
    val currentOffsetX = lerp(expandedOffsetX, pipOffsetX, p)
    val currentOffsetY = lerp(expandedOffsetY, pipOffsetY, p)
    
    val targetAlpha = if (p < 0.1f) 1.0f else 0.3f
    val currentAlpha by animateFloatAsState(targetAlpha, animationSpec = tween(300))

    val finalAlpha = if (visible) (currentAlpha * previewAlpha) else 0f

    val gestureModifier = if (visible) {
        Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            )
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x, dragAmount.y)
                }
            }
    } else {
        Modifier
    }

    Box(
        modifier = modifier
            .padding(start = currentPaddingStart, bottom = currentPaddingBottom)
            .offset { IntOffset(currentOffsetX.toInt(), currentOffsetY.toInt()) }
            .requiredSize(currentWidth, currentHeight)
            .clip(RoundedCornerShape(currentCorner))
            .then(gestureModifier)
    ) {
        InAppCameraView(
            modifier = Modifier
                .matchParentSize()
                .clip(RoundedCornerShape(currentCorner)),
            taskId = taskId,
            calibrationData = calibrationData,
            isRecording = isRecording,
            onVideoSaved = onVideoSaved,
            onZoomStateAvailable = onZoomStateAvailable,
            onPreviewStateChanged = { isPreviewReady = it },
            onTorchStateAvailable = onTorchStateAvailable,
            alpha = finalAlpha,
            cornerRadiusDp = currentCorner
        )
    }
}
