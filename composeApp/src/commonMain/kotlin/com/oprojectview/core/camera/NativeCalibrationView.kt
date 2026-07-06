package com.oprojectview.core.camera

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.calibration_capture_desc
import kotlinmultiplatform.composeapp.generated.resources.calibration_close_desc
import kotlinmultiplatform.composeapp.generated.resources.calibration_flip_camera_desc
import kotlinmultiplatform.composeapp.generated.resources.calibration_toggle_flash_desc
import org.jetbrains.compose.resources.stringResource

@Composable
fun NativeCalibrationView(
    onDismiss: () -> Unit,
    onCalibrationCompleted: (CalibrationData) -> Unit,
    zoomOptions: List<Float>,
    currentZoomIndex: Int,
    onZoomIndexChanged: (Int) -> Unit,
    isTorchOn: Boolean,
    onTorchChanged: (Boolean) -> Unit,
    isFrontCamera: Boolean,
    onFlipCamera: () -> Unit,
    isTorchSupported: Boolean,
    isHorizontal: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
    ) {
        // UI Safe Area Wrapper
        Box(modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()) {

            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 16.dp)
                    .size(48.dp)
                    .padding(4.dp)
                    .clip(CircleShape)
                    .clickable { onDismiss() }
                    .background(Color.White.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(Res.string.calibration_close_desc),
                    tint = Color.White,
                    modifier = Modifier.scale(1.2f)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 48.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (zoomOptions.size > 1) {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        zoomOptions.forEachIndexed { index, zoom ->
                            val isSelected = index == currentZoomIndex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) Color(0xFFF7C8D0) else Color.Transparent)
                                    .clickable {
                                        onZoomIndexChanged(index)
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (zoom == 0.5f) ".5" else if (zoom == 1f) "1x" else "${zoom.toInt()}",
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 48.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .rotate(if (isHorizontal) 90f else 0f)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable(enabled = isTorchSupported) {
                                onTorchChanged(!isTorchOn)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isTorchSupported && isTorchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                            contentDescription = stringResource(Res.string.calibration_toggle_flash_desc),
                            tint = Color.White
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .rotate(if (isHorizontal) 90f else 0f)
                            .border(4.dp, Color.White, CircleShape)
                            .clip(CircleShape)
                            .clickable {
                                val zoomRatio = zoomOptions.getOrElse(currentZoomIndex) { 1f }
                                onCalibrationCompleted(
                                    CalibrationData(
                                        zoomRatio = zoomRatio,
                                        exposureBias = 0f,
                                        isFrontCamera = isFrontCamera,
                                        width = 1920,
                                        height = 1080,
                                        orientation = 0,
                                        flashEnabled = isTorchOn
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier,
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = stringResource(Res.string.calibration_capture_desc),
                                modifier = Modifier
                                    .size(80.dp)
                                    .padding(4.dp),
                                tint = Color.White
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .rotate(if (isHorizontal) 90f else 0f)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.25f))
                            .clickable { onFlipCamera() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Cached,
                            contentDescription = stringResource(Res.string.calibration_flip_camera_desc),
                            tint = Color.White,
                            modifier = Modifier.scale(1.2f)
                        )
                    }
                }
            }
        }
    }
}
