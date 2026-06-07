package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.cd_back
import kotlinmultiplatform.composeapp.generated.resources.cd_next
import kotlinmultiplatform.composeapp.generated.resources.fab_next
import kotlinmultiplatform.composeapp.generated.resources.screen_new_task
import org.jetbrains.compose.resources.stringResource


// ─────────────────────────────────────────────────────────────────────────────
// Static — toolbar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NewTaskScreenStatic(
    onBack: () -> Unit,
) {
    ToolBar(
        title           = stringResource(Res.string.screen_new_task),
        onLeadingClick  = onBack,
        leadingIcon = {
            Icon(
                imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = stringResource(Res.string.cd_back),
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Body — fields + style bar + dialog overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NewTaskScreenBody(
    state:          NewTaskScreenState,
    onBack:         () -> Unit,
    onNextClick:    () -> Unit,
    showSaveDialog: Boolean,
    onDismissDialog: () -> Unit,
    onSave:         () -> Unit,
    onDiscard:      () -> Unit,
    modifier:       Modifier = Modifier,
    styleState:     ScriptTextStyleState? = null,
) {
    // Track whether the user has attempted "Next" with an empty script field.
    var scriptError by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    LaunchedEffect(state.scriptState.text) {
        if (state.scriptText.isNotEmpty()) scriptError = false
    }

    // ── Live selection tracking ───────────────────────────────────────────────
    //
    // TextFieldState.selection is a SnapshotState — read it inside a
    // derivedStateOf so recompositions stay scoped to the bar only.

    val selStart by remember {
        derivedStateOf { state.scriptState.selection.start }
    }
    val selEnd by remember {
        derivedStateOf { state.scriptState.selection.end }
    }

    // Derive active-state flags for the style bar from the current selection
    val hasSelection   by remember { derivedStateOf { selStart < selEnd } }
    val isBoldActive   by remember { derivedStateOf { styleState?.isRangeBold(selStart, selEnd) == true } }
    val isItalicActive by remember { derivedStateOf { styleState?.isRangeItalic(selStart, selEnd) == true } }
    val isUnderActive  by remember { derivedStateOf { styleState?.isRangeUnderline(selStart, selEnd) == true } }
    val activeColor    by remember { derivedStateOf { styleState?.rangeTextColor(selStart, selEnd) } }

    Box(
        modifier = Modifier
            .padding(top = 64.dp)
            .fillMaxSize(),
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .fillMaxWidth(),
        ) {
            TopicTextField(
                state          = state.topicState,
                focusRequester = focusRequester,
                isError        = scriptError,
            )

            ScriptStyleBar(
                onBoldClick      = {
                    if (hasSelection) styleState?.toggleBold(selStart, selEnd)
                },
                onItalicClick    = {
                    if (hasSelection) styleState?.toggleItalic(selStart, selEnd)
                },
                onUnderlineClick = {
                    if (hasSelection) styleState?.toggleUnderline(selStart, selEnd)
                },
                // Text colour: pick directly from the colour palette
                onTextColorPick  = { color ->
                    if (hasSelection) styleState?.setTextColor(selStart, selEnd, color)
                },
                onFillColorPick  = { color -> styleState?.setFillColor(color) },
                onMoreClick      = {},
                // Active state reflects current selection
                isBoldActive      = isBoldActive,
                isItalicActive    = isItalicActive,
                isUnderlineActive = isUnderActive,
                activeTextColor   = activeColor,
                activeFillColor   = styleState?.activeFillColor,
            )

            ScriptTextField(
                state      = state.scriptState,
                styleState = styleState,
                isError    = scriptError,
            )
        }

        FabBarLayout(
            text    = stringResource(Res.string.fab_next),
            onClick = {
                if (state.scriptText.trim().isEmpty()) {
                    scriptError = true
                } else {
                    scriptError = false
                    onNextClick()
                }
            },
            modifier = modifier
                .fillMaxSize()
                .imePadding(),
            icon = {
                Icon(
                    modifier           = Modifier.size(24.dp),
                    imageVector        = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(Res.string.cd_next),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }

    // ── Dialog overlay ────────────────────────────────────────────────────────
    if (showSaveDialog) {
        SaveChangesDialog(
            onDismissRequest = onDismissDialog,
            onSave           = onSave,
            onDiscard        = onDiscard,
        )
    }
}


// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NewTaskScreenPreview(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        val state = rememberNewTaskScreenState()
        var showDialog by remember { mutableStateOf(false) }

        Column(modifier = Modifier.fillMaxWidth()) {
            NewTaskScreenStatic(onBack = {
                if (state.isNotEmpty) showDialog = true
            })
            NewTaskScreenBody(
                state           = state,
                onBack          = { if (state.isNotEmpty) showDialog = true },
                onNextClick     = {},
                showSaveDialog  = showDialog,
                onDismissDialog = { showDialog = false },
                onSave          = { showDialog = false },
                onDiscard       = { showDialog = false },
            )
        }
    }
}

@Preview(name = "NewTaskScreen – 412dp", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFull() = NewTaskScreenPreview(darkTheme = false)

@Preview(name = "NewTaskScreen – 320dp (compact)", showBackground = true, widthDp = 320)
@Composable
private fun PreviewCompact() = NewTaskScreenPreview(darkTheme = true)

@Preview(name = "NewTaskScreen – dialog visible", showBackground = true, widthDp = 412)
@Composable
private fun PreviewDialog() {
    AppTheme {
        val state = rememberNewTaskScreenState()
        Column(modifier = Modifier.fillMaxWidth()) {
            NewTaskScreenStatic(onBack = {})
            NewTaskScreenBody(
                state           = state,
                onBack          = {},
                onNextClick     = {},
                showSaveDialog  = true,
                onDismissDialog = {},
                onSave          = {},
                onDiscard       = {},
            )
        }
    }
}
