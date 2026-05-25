package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme


// ─────────────────────────────────────────────────────────────────────────────
// Static — toolbar
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NewTaskScreenStatic(
    onBack: () -> Unit,
) {
    ToolBar(
        title = "New Task",
        onLeadingClick = onBack,
        leadingIcon = {
            Icon(
                imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Body — fields + dialog overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NewTaskScreenBody(
    state: NewTaskScreenState,
    onBack: () -> Unit,
    onNextClick: () -> Unit,
    showSaveDialog: Boolean,
    onDismissDialog: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    // Track whether the user has attempted "Next" with an empty script field.
    // Reset whenever the script becomes non-empty so the error clears naturally.
    var scriptError by remember { mutableStateOf(false) }

    // Clear the error as soon as the user starts typing in the script field.
    LaunchedEffect(state.scriptState.text) {
        if (state.scriptText.isNotEmpty()) scriptError = false
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = modifier
                .imePadding()
                .fillMaxWidth(),
        ) {
            TopicTextField(
                state          = state.topicState,
                focusRequester = focusRequester,
            )

            ScriptStyleBar(
                onBoldClick      = {},
                onItalicClick    = {},
                onUnderlineClick = {},
                onTextColorClick = {},
                onFillColorClick = {},
                onMoreClick      = {},
            )

            ScriptTextField(
                state       = state.scriptState,
                isError     = scriptError,
            )
        }

        FabBarLayout(
            text = "Next",
            onClick = {
                if (state.scriptText.trim().isEmpty()) {
                    // Show error on the script field — do not navigate.
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
                    modifier = Modifier.size(26.dp),
                    imageVector        = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Next",
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
