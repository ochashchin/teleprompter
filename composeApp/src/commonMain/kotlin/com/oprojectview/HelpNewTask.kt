package com.oprojectview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.oprojectview.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.help_bold
import kotlinmultiplatform.composeapp.generated.resources.help_fill_color
import kotlinmultiplatform.composeapp.generated.resources.help_italic
import kotlinmultiplatform.composeapp.generated.resources.help_text_color
import kotlinmultiplatform.composeapp.generated.resources.help_underline

@Composable
fun HelpNewTaskBottomSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        HelpItem(iconVector = Icons.Rounded.FormatBold, textRes = Res.string.help_bold),
        HelpItem(iconVector = Icons.Rounded.FormatItalic, textRes = Res.string.help_italic),
        HelpItem(iconVector = Icons.Rounded.FormatUnderlined, textRes = Res.string.help_underline),
        HelpItem(iconVector = Icons.Rounded.FormatColorText, textRes = Res.string.help_text_color),
        HelpItem(iconVector = Icons.Rounded.FormatColorFill, textRes = Res.string.help_fill_color)
    )

    HelpBottomSheet(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        items = items,
        modifier = modifier
    )
}

@Preview
@Composable
fun HelpNewTaskBottomSheetPreview() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            HelpNewTaskBottomSheet(
                expanded = true,
                onDismissRequest = {}
            )
        }
    }
}
