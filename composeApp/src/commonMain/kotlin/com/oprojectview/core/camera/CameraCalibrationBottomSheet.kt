package com.oprojectview.core.camera

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cached
import androidx.compose.material.icons.rounded.CheckCircleOutline
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oprojectview.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.calibration_action_cancel
import kotlinmultiplatform.composeapp.generated.resources.calibration_action_continue
import kotlinmultiplatform.composeapp.generated.resources.calibration_capture_desc
import kotlinmultiplatform.composeapp.generated.resources.calibration_flash_desc
import kotlinmultiplatform.composeapp.generated.resources.calibration_flash_title
import kotlinmultiplatform.composeapp.generated.resources.calibration_framing_desc
import kotlinmultiplatform.composeapp.generated.resources.calibration_framing_title
import kotlinmultiplatform.composeapp.generated.resources.calibration_lens_face_desc
import kotlinmultiplatform.composeapp.generated.resources.calibration_lens_face_title
import kotlinmultiplatform.composeapp.generated.resources.calibration_save_settings
import kotlinmultiplatform.composeapp.generated.resources.calibration_sheet_description
import kotlinmultiplatform.composeapp.generated.resources.calibration_sheet_footer
import kotlinmultiplatform.composeapp.generated.resources.calibration_sheet_title
import kotlinmultiplatform.composeapp.generated.resources.calibration_zoom_desc
import kotlinmultiplatform.composeapp.generated.resources.calibration_zoom_title
import kotlinmultiplatform.composeapp.generated.resources.ic_calibration_zoom
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraCalibrationBottomSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onConfirm: (saveChecked: Boolean) -> Unit
) {
    if (expanded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var saveChecked by remember { mutableStateOf(false) }

        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    text = stringResource(Res.string.calibration_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(Res.string.calibration_sheet_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Feature List
                val features = listOf(
                    Triple(Icons.Rounded.CropFree, Res.string.calibration_framing_title, Res.string.calibration_framing_desc),
                    Triple(Icons.Rounded.Cached, Res.string.calibration_lens_face_title, Res.string.calibration_lens_face_desc),
                    Triple(Res.drawable.ic_calibration_zoom, Res.string.calibration_zoom_title, Res.string.calibration_zoom_desc),
                    Triple(Icons.Rounded.FlashOn, Res.string.calibration_flash_title, Res.string.calibration_flash_desc)
                )

                features.forEach { (iconRes, titleRes, descRes) ->
                    val title = stringResource(titleRes)
                    val desc = stringResource(descRes)

                    val annotatedText = buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(title)
                        }
                        withStyle(SpanStyle(fontWeight = FontWeight.Normal)) {
                            append(" — ")
                            append(desc)
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 14.dp, vertical = 2.dp)
                                .size(28.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            if (iconRes is DrawableResource) {
                                Icon(
                                    painter = painterResource(iconRes),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(4.dp)
                                )
                            } else if (iconRes is ImageVector) {
                                Icon(
                                    imageVector = iconRes,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 16.dp)
                        ) {
                            Text(
                                text = annotatedText,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                val rawString = stringResource(Res.string.calibration_sheet_footer)

                val placeholderToken = "[check]"
                val inlineContentId = "inline_check_circle"

                // 2. Build AnnotatedString by splitting text around the token
                val annotatedString = buildAnnotatedString {
                    val index = rawString.indexOf(placeholderToken)
                    if (index != -1) {
                        // Append everything before [check]
                        append(rawString.substring(0, index))
                        // Insert the inline composable hook
                        appendInlineContent(inlineContentId, placeholderToken)
                        // Append everything after [check]
                        append(rawString.substring(index + placeholderToken.length))
                    } else {
                        // Fallback safety layer
                        append(rawString)
                    }
                }

                // 3. Define the inline graphic configuration mapping
                val inlineContentMap = mapOf(
                    inlineContentId to InlineTextContent(
                        Placeholder(
                            width = 20.sp, // Scales elegantly alongside the font style size
                            height = 20.sp,
                            placeholderVerticalAlign = PlaceholderVerticalAlign.Center
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircleOutline,
                            contentDescription = stringResource(Res.string.calibration_capture_desc),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )

                Text(
                    text = annotatedString,
                    inlineContent = inlineContentMap,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { saveChecked = !saveChecked }
                        .padding(vertical = 8.dp)
                ) {
                    Checkbox(
                        checked = saveChecked,
                        onCheckedChange = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(Res.string.calibration_save_settings),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))
                Row(
                    horizontalArrangement = Arrangement.End,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onDismissRequest) {
                        Text(
                            text = stringResource(Res.string.calibration_action_cancel),
                            fontSize = MaterialTheme.typography.titleMedium.fontSize
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onConfirm(saveChecked) }) {
                        Text(
                            text = stringResource(Res.string.calibration_action_continue),
                            fontSize = MaterialTheme.typography.titleMedium.fontSize
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Preview
@Composable
fun CameraCalibrationBottomSheetPreview() {
    AppTheme {
        CameraCalibrationBottomSheet(
            expanded = true,
            onDismissRequest = {},
            onConfirm = {}
        )
    }
}
