package com.example.kotlinmultiplatform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.FormatBold
import androidx.compose.material.icons.rounded.FormatColorFill
import androidx.compose.material.icons.rounded.FormatColorText
import androidx.compose.material.icons.rounded.FormatItalic
import androidx.compose.material.icons.rounded.FormatUnderlined
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.example.kotlinmultiplatform.ui.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.cd_more_options
import kotlinmultiplatform.composeapp.generated.resources.style_bold
import kotlinmultiplatform.composeapp.generated.resources.style_fill_color
import kotlinmultiplatform.composeapp.generated.resources.style_italic
import kotlinmultiplatform.composeapp.generated.resources.style_text_color
import kotlinmultiplatform.composeapp.generated.resources.style_underline
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource

// ── ScriptAction ──────────────────────────────────────────────────────────────

data class ScriptAction(
    val icon:            ImageVector,
    val labelRes:        StringResource,
    val onClick:         () -> Unit,
    /** When true the button is rendered with the primary colour to signal "active". */
    val isActive:        Boolean          = false,
    /** Override the tint (used for the colour-text button to show the active colour). */
    val tintColor:       Color?           = null,
)

// ── ColorPickerPopup ──────────────────────────────────────────────────────────

/**
 * Horizontal pill of colour swatches shown as a Popup.
 * Layout matches the spec: Surface height=60dp, RoundedCornerShape(40dp),
 * shadowElevation=6dp, 10dp inner padding, 10dp gap between 40dp swatches.
 * No excluded/active state — all colours are shown equally; tapping picks the colour,
 * tapping the same colour again clears it.
 */
@Composable
private fun ColorPickerPopup(
    colors:      List<Color>,
    activeColor: Color?,
    onColorPick: (Color?) -> Unit,
    onDismiss:   () -> Unit,
) {
    Popup(
        alignment        = Alignment.BottomCenter,
        properties       = PopupProperties(focusable = true, dismissOnClickOutside = true),
        onDismissRequest = onDismiss,
    ) {
        Surface(
            modifier        = Modifier.height(60.dp),
            shape           = RoundedCornerShape(40.dp),
            color           = MaterialTheme.colorScheme.surfaceContainer,
            shadowElevation = 6.dp,
        ) {
            Row(
                modifier              = Modifier
                    .wrapContentWidth()
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment     = Alignment.CenterVertically,
            ) {
                colors.forEach { color ->
                    val isActive = activeColor != null && color.value == activeColor.value
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .shadow(elevation = 3.dp, shape = CircleShape)
                            .background(color, CircleShape)
                            .then(
                                if (isActive)
                                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                else
                                    Modifier
                            )
                            .clickable(
                                indication        = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ) {
                                if (isActive) onColorPick(null) else onColorPick(color)
                                onDismiss()
                            },
                    )
                }
            }
        }
    }
}

// ── ScriptStyleBar ────────────────────────────────────────────────────────────

@Composable
fun ScriptStyleBar(
    modifier:        Modifier = Modifier,
    onBoldClick:      () -> Unit = {},
    onItalicClick:    () -> Unit = {},
    onUnderlineClick: () -> Unit = {},
    /** Called when a colour is chosen from the text-colour picker (null = clear). */
    onTextColorPick:  (Color?) -> Unit = {},
    /** Called when a colour is chosen from the fill-colour picker (null = clear). */
    onFillColorPick:  (Color?) -> Unit = {},
    // Active-state flags — set from the current text selection
    isBoldActive:      Boolean = false,
    isItalicActive:    Boolean = false,
    isUnderlineActive: Boolean = false,
    /** Currently active text colour for the selection, or null if default. */
    activeTextColor:   Color?  = null,
    /** Currently active fill colour, or null if fill is off. */
    activeFillColor:   Color?  = null,
) {
    val primary          = MaterialTheme.colorScheme.primary
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    var showTextColorPicker by remember { mutableStateOf(false) }
    var showFillColorPicker by remember { mutableStateOf(false) }
    var menuExpanded        by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {

        // ── Text-colour picker popup ──────────────────────────────────────────
        if (showTextColorPicker) {
            ColorPickerPopup(
                colors      = ScriptTextColors,
                activeColor = activeTextColor,
                onColorPick = onTextColorPick,
                onDismiss   = { showTextColorPicker = false },
            )
        }

        // ── Fill-colour picker popup ──────────────────────────────────────────
        if (showFillColorPicker) {
            ColorPickerPopup(
                colors      = ScriptFillColors,
                activeColor = activeFillColor,
                onColorPick = onFillColorPick,
                onDismiss   = { showFillColorPicker = false },
            )
        }

        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(58.dp),
            contentAlignment = Alignment.Center,
        ) {
            val parentWidth = maxWidth
            val maxBoxes    = 6
            val minBoxSize  = 58.dp
            val boxSize     = minOf(maxHeight, parentWidth / maxBoxes).coerceAtLeast(minBoxSize)
            val count       = (parentWidth / boxSize).toInt().coerceAtMost(maxBoxes)

            val actions = listOf(
                ScriptAction(
                    icon     = Icons.Rounded.FormatBold,
                    labelRes = Res.string.style_bold,
                    onClick  = onBoldClick,
                    isActive = isBoldActive,
                ),
                ScriptAction(
                    icon     = Icons.Rounded.FormatItalic,
                    labelRes = Res.string.style_italic,
                    onClick  = onItalicClick,
                    isActive = isItalicActive,
                ),
                ScriptAction(
                    icon     = Icons.Rounded.FormatUnderlined,
                    labelRes = Res.string.style_underline,
                    onClick  = onUnderlineClick,
                    isActive = isUnderlineActive,
                ),
                ScriptAction(
                    icon      = Icons.Rounded.FormatColorText,
                    labelRes  = Res.string.style_text_color,
                    onClick   = {
                        showFillColorPicker = false
                        showTextColorPicker = !showTextColorPicker
                    },
                    isActive  = activeTextColor != null,
                    tintColor = activeTextColor,
                ),
                ScriptAction(
                    icon      = Icons.Rounded.FormatColorFill,
                    labelRes  = Res.string.style_fill_color,
                    onClick   = {
                        showTextColorPicker = false
                        showFillColorPicker = !showFillColorPicker
                    },
                    isActive  = activeFillColor != null,
                    tintColor = activeFillColor,
                ),
            )

            val totalSlots      = count.coerceAtLeast(1)
            val showOnlyMore    = actions.isEmpty()
            val hasOverflow     = actions.size >= totalSlots
            val visibleActions  = when {
                showOnlyMore -> 0
                hasOverflow  -> totalSlots - 1
                else         -> actions.size
            }

            Row(
                modifier = Modifier
                    .offset(y = 4.dp)
                    .padding(start = 16.dp, end = 16.dp),
                verticalAlignment   = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                repeat(visibleActions) { index ->
                    val action = actions[index]
                    val tint = when {
                        action.tintColor != null -> action.tintColor
                        action.isActive          -> primary
                        else                     -> onSurfaceVariant
                    }

                    Box(
                        modifier          = Modifier.size(minBoxSize),
                        contentAlignment  = Alignment.Center,
                    ) {
                        IconButton(onClick = action.onClick) {
                            Icon(
                                imageVector        = action.icon,
                                contentDescription = "",
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                val shouldShowMore = showOnlyMore || hasOverflow || actions.isNotEmpty()
                if (shouldShowMore) {
                    Box(
                        modifier         = Modifier.size(minBoxSize),
                        contentAlignment = Alignment.Center,
                    ) {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(
                                imageVector        = Icons.Default.MoreVert,
                                contentDescription = stringResource(Res.string.cd_more_options),
                                tint               = onSurfaceVariant,
                            )
                        }

                        // ── Overflow DropdownMenu ─────────────────────────────
                        val overflowActions = actions.drop(visibleActions)
                        if (overflowActions.isNotEmpty()) {
                            DropdownMenu(
                                expanded         = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                overflowActions.forEach { action ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text     = stringResource(action.labelRes),
                                                fontSize = fontSize(18.dp),
                                                style    = MaterialTheme.typography.bodyMedium,
                                            )
                                        },
                                        onClick = {
                                            action.onClick()
                                            menuExpanded = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}


// ── Previews ──────────────────────────────────────────────────────────────────

@Composable
private fun NewTaskScreenPreview(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxWidth()) {
            ScriptStyleBar(
                isBoldActive     = true,
                isUnderlineActive = false,
                activeFillColor  = ScriptFillColors.first(),
            )
        }
    }
}

// ── ColorPickerPopup previews ──────────────────────────────────────────────────

@Composable
private fun NewColorPickerPopupPreview(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        // Two pills matching the spec layout, stacked vertically:
        //   Top    — text-colour picker (ScriptTextColors), Blue active
        //   Bottom — fill-colour picker (ScriptFillColors), Green active
        val activeTextColor = ScriptTextColors[1]  // Blue
        val activeFillColor = ScriptFillColors[2]  // Green

        Column(
            modifier            = Modifier
                .width(366.dp)
                .padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Text-colour pill
            Surface(
                modifier        = Modifier.height(60.dp),
                shape           = RoundedCornerShape(40.dp),
                color           = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier              = Modifier
                        .wrapContentWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    ScriptTextColors.forEach { color ->
                        val isActive = color.value == activeTextColor.value
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(elevation = 3.dp, shape = CircleShape)
                                .background(color, CircleShape)
                                .then(
                                    if (isActive)
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else
                                        Modifier
                                ),
                        )
                    }
                }
            }

            // Fill-colour pill
            Surface(
                modifier        = Modifier.height(60.dp),
                shape           = RoundedCornerShape(40.dp),
                color           = MaterialTheme.colorScheme.surfaceContainer,
                shadowElevation = 6.dp,
            ) {
                Row(
                    modifier              = Modifier
                        .wrapContentWidth()
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    ScriptFillColors.forEach { color ->
                        val isActive = color.value == activeFillColor.value
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .shadow(elevation = 3.dp, shape = CircleShape)
                                .background(color, CircleShape)
                                .then(
                                    if (isActive)
                                        Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                    else
                                        Modifier
                                ),
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "ColorPickerPopup – light", showBackground = true, widthDp = 366)
@Composable
private fun PreviewColorPickerLight() = NewColorPickerPopupPreview(darkTheme = false)

@Preview(name = "ColorPickerPopup – dark", showBackground = true, widthDp = 366)
@Composable
private fun PreviewColorPickerDark() = NewColorPickerPopupPreview(darkTheme = true)

// ── StyleBar previews ──────────────────────────────────────────────────────────

@Preview(name = "TaskScreen – 412dp", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFull() = NewTaskScreenPreview(darkTheme = false)