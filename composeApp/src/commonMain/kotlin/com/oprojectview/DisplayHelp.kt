package com.oprojectview

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.oprojectview.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.help_animation
import kotlinmultiplatform.composeapp.generated.resources.help_distortion
import kotlinmultiplatform.composeapp.generated.resources.help_mirror
import kotlinmultiplatform.composeapp.generated.resources.help_orientation
import kotlinmultiplatform.composeapp.generated.resources.help_overlay
import kotlinmultiplatform.composeapp.generated.resources.help_speed
import kotlinmultiplatform.composeapp.generated.resources.help_text_size
import kotlinmultiplatform.composeapp.generated.resources.help_transition
import kotlinmultiplatform.composeapp.generated.resources.ic_animation
import kotlinmultiplatform.composeapp.generated.resources.ic_distortion
import kotlinmultiplatform.composeapp.generated.resources.ic_mirror
import kotlinmultiplatform.composeapp.generated.resources.ic_orientation
import kotlinmultiplatform.composeapp.generated.resources.ic_overlay
import kotlinmultiplatform.composeapp.generated.resources.ic_speed
import kotlinmultiplatform.composeapp.generated.resources.ic_text_size
import kotlinmultiplatform.composeapp.generated.resources.ic_transition

@Composable
fun HelpDisplayBottomSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        HelpItem(iconDrawable = Res.drawable.ic_text_size, textRes = Res.string.help_text_size),
        HelpItem(iconDrawable = Res.drawable.ic_orientation, textRes = Res.string.help_orientation),
        HelpItem(iconDrawable = Res.drawable.ic_speed, textRes = Res.string.help_speed),
        HelpItem(iconDrawable = Res.drawable.ic_animation, textRes = Res.string.help_animation),
        HelpItem(iconDrawable = Res.drawable.ic_transition, textRes = Res.string.help_transition),
        HelpItem(iconDrawable = Res.drawable.ic_distortion, textRes = Res.string.help_distortion),
        HelpItem(iconDrawable = Res.drawable.ic_mirror, textRes = Res.string.help_mirror),
        HelpItem(iconDrawable = Res.drawable.ic_overlay, textRes = Res.string.help_overlay)
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
fun HelpDisplayBottomSheetPreview() {
    AppTheme {
        Box(modifier = Modifier.fillMaxSize()) {
            HelpDisplayBottomSheet(
                expanded = true,
                onDismissRequest = {}
            )
        }
    }
}
