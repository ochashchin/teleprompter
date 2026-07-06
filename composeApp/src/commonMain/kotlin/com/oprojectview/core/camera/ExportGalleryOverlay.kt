package com.oprojectview.core.camera

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import com.oprojectview.RotatedLayout
import com.oprojectview.fontSize
import org.jetbrains.compose.resources.stringResource
import kotlinmultiplatform.composeapp.generated.resources.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import com.oprojectview.theme.AppTheme

@Composable
fun ExportGalleryOverlay(
    visible: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    isExporting: Boolean,
    exportFilename: String,
    modifier: Modifier = Modifier,
    fillColor: Color? = null,
    isHorizontal: Boolean = false
) {
    if (!visible) return

    val progress = remember { Animatable(1f) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        // Request default focus on the "Yes" button
        focusRequester.requestFocus()
        
        // Run 5-second countdown
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = 5000, easing = LinearEasing)
        )
        // Timeout automatically saves/exports
        onConfirm()
    }

    val baseColor = fillColor ?: MaterialTheme.colorScheme.surface
    val cardBackground = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f)
        .compositeOver(baseColor)

    Box(
        modifier = modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    if (!isExporting) {
                        onDismiss()
                    }
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        RotatedLayout(rotate90 = isHorizontal) {
            Surface(
                shape = RoundedCornerShape(38.dp),
                color = cardBackground,
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 380.dp)
                    .wrapContentHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {} // Consume clicks to prevent click-through within the card bounds
                    )
            ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.export_dialog_title),
                    fontSize = fontSize(24.dp),
                    lineHeight = fontSize(28.dp),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = exportFilename,
                    fontSize = fontSize(16.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    DialogButton(
                        text = stringResource(Res.string.export_dialog_action_no),
                        hovered = false,
                        enabled = !isExporting,
                        modifier = Modifier.fillMaxHeight(),
                        onClick = onDismiss
                    )

                    Spacer(Modifier.width(16.dp))

                    DialogButton(
                        text = stringResource(Res.string.export_dialog_action_yes),
                        hovered = true,
                        enabled = !isExporting,
                        modifier = Modifier
                            .fillMaxHeight()
                            .focusRequester(focusRequester),
                        onClick = onConfirm,
                        leadingIcon = {
                            if (isExporting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                CircularProgressIndicator(
                                    progress = { progress.value },
                                    modifier = Modifier.size(16.dp),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    trackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun DialogButton(
    text: String,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = MaterialTheme.typography.titleMedium.fontSize,
    hovered: Boolean = true,
    enabled: Boolean = true,
    onClick: () -> Unit,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 24.dp),
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.textButtonColors(
            containerColor =
                if (hovered)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else
                    Color.Transparent,

            contentColor =
                if (hovered)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.primary
        )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (leadingIcon != null) {
                leadingIcon()
            }
            Text(
                fontSize = fontSize,
                text = text,
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Preview
@Composable
private fun ExportGalleryOverlayPreview() {
    AppTheme {
        ExportGalleryOverlay(
            visible = true,
            onConfirm = {},
            onDismiss = {},
            isExporting = false,
            exportFilename = "morning-workout-14-32-10.mp4"
        )
    }
}
