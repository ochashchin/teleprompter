package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.kotlinmultiplatform.ui.theme.AppTheme


@Composable
fun SaveChangesDialog(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    var P by remember { mutableStateOf(0f) }
    var H by remember { mutableStateOf(0.dp) }
    var W by remember { mutableStateOf(0f) }

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(420.dp)
                .aspectRatio(312f / 276f)
        ) {

            Box(modifier = Modifier.fillMaxSize()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
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
            }

            val fontSize = with(LocalDensity.current) { H.toSp() * 0.82f }
            fun percentToBias(percent: Float): Float = (percent * 2f) - 1f

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .height(H)
                        .width(H)
                        .align(BiasAlignment(percentToBias(.5f), percentToBias(.10f)))
                ) {
                    Icon(
                        modifier = Modifier.fillMaxSize(),
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = "Swap Horiz Icon",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                Box(
                    modifier = Modifier
                        .height(H)
                        .wrapContentWidth()
                        .align(BiasAlignment(percentToBias(.5f), percentToBias(.27f)))
                ) {
                    Text(
                        text = "Save changes?",
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Box(
                    modifier = Modifier
                        .wrapContentHeight()
                        .wrapContentWidth()
                        .padding(start = fontSize.value.dp, end = fontSize.value.dp)
                        .align(BiasAlignment(percentToBias(.5f), percentToBias(.57f)))
                ) {
                    Text(
                        text = "You have unsaved changes. If you leave now, you will lose the information you’ve entered.",
                        fontSize = fontSize * 0.65,
                        lineHeight = fontSize,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Row(
                    modifier = Modifier
                        .height(H * 2)
                        .padding(end = fontSize.value.dp)
                        .align(BiasAlignment(percentToBias(.5f), percentToBias(.89f)))
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {

                    TextButton(
                        modifier = Modifier
                            .height(H * 2),
                        onClick = {
                            onDismissRequest()
                            onDiscard()
                        }
                    ) {
                        Text(
                            fontSize = fontSize * 0.7,
                            text = "Discard"
                        )
                    }

                    Spacer(Modifier.width(18.dp))

                    SaveButton(
                        modifier = Modifier
                            .height(H * 2),
                        fontSize = fontSize,
                        hovered = true,
                        onSave = {
                            onSave()
                        }
                    )
                }
            }

        }
    }

}

@Composable
fun SaveButton(
    modifier: Modifier,
    fontSize: TextUnit,
    hovered: Boolean = true,
    onSave: () -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onSave,
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
        Text(
            fontSize = fontSize * 0.7,
            text = "Save",
            style = MaterialTheme.typography.titleMedium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@androidx.compose.ui.tooling.preview.Preview(
    name = "SaveChangesDialog – light",
    showBackground = true,
)
@Composable
private fun PreviewSaveChangesDialog() {
    AppTheme {
        SaveChangesDialog(
            onDismissRequest = {},
            onSave = {},
            onDiscard = {},
        )
    }
}