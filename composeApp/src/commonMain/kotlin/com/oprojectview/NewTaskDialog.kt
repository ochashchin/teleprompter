package com.oprojectview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.oprojectview.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.dialog_discard
import kotlinmultiplatform.composeapp.generated.resources.dialog_save
import kotlinmultiplatform.composeapp.generated.resources.dialog_save_changes_title
import org.jetbrains.compose.resources.stringResource


@Composable
fun SaveChangesDialog(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(38.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(380.dp)
                .wrapContentHeight()
        ) {
            val H = 28.dp
            val fontSize = fontSize(H)

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp)
                    .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(Res.string.dialog_save_changes_title),
                    fontSize = fontSize(24.dp),
                    lineHeight = fontSize(28.dp),
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(28.dp))

                Row(
                    modifier = Modifier
                        .height(48.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    DialogButton(
                        text = stringResource(Res.string.dialog_discard),
                        hovered = false,
                        modifier = Modifier.fillMaxHeight(),
                        onClick = {
                            onDismissRequest()
                            onDiscard()
                        }
                    )

                    Spacer(Modifier.width(16.dp))

                    DialogButton(
                        text = stringResource(Res.string.dialog_save),
                        hovered = true,
                        modifier = Modifier.fillMaxHeight(),
                        onClick = {
                            onSave()
                        }
                    )
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
    onClick: () -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onClick,
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
        Text(
            fontSize = fontSize,
            text = text,
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