package com.oprojectview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
            shape = RoundedCornerShape(40.dp),
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
                    .padding(bottom = 24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(Res.string.dialog_save_changes_title),
                        fontSize = fontSize(24.dp),
                        lineHeight = fontSize(28.dp),
                        style = MaterialTheme.typography.headlineSmall
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .padding(end = fontSize.value.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        modifier = Modifier
                            .fillMaxHeight(),
                        onClick = {
                            onDismissRequest()
                            onDiscard()
                        }
                    ) {
                        Text(
                            fontSize = fontSize(18.dp),
                            text = stringResource(Res.string.dialog_discard)
                        )
                    }

                    Spacer(Modifier.width(24.dp))

                    SaveButton(
                        modifier = Modifier
                            .fillMaxHeight(),
                        fontSize = fontSize(18.dp),
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
            text = stringResource(Res.string.dialog_save),
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