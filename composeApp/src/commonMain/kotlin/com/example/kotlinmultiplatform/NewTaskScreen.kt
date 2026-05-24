package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
// Body — fields + dialog1 overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NewTaskScreenBody(
    state: NewTaskScreenState,
    onBack: () -> Unit,
    showSaveDialog: Boolean,
    onDismissDialog: () -> Unit,
    onSave: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

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

        ScriptTextField(state = state.scriptState)
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
                if (state.isDirty) showDialog = true
            })
            NewTaskScreenBody(
                state           = state,
                onBack          = { if (state.isDirty) showDialog = true },
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

@Preview(name = "NewTaskScreen – dialog1 visible", showBackground = true, widthDp = 412)
@Composable
private fun PreviewDialog() {
    AppTheme {
        val state = rememberNewTaskScreenState()
        Column(modifier = Modifier.fillMaxWidth()) {
            NewTaskScreenStatic(onBack = {})
            NewTaskScreenBody(
                state           = state,
                onBack          = {},
                showSaveDialog  = true,
                onDismissDialog = {},
                onSave          = {},
                onDiscard       = {},
            )
        }
    }
}
