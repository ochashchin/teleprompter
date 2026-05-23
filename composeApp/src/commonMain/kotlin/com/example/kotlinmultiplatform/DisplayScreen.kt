package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme

// ── static — top bar only ─────────────────────────────────────────────────────

@Composable
fun DisplayScreenStatic(
    task   : Task,
    onBack : () -> Unit,
) {
    ToolBar(
        title          = task.title,
        onLeadingClick = onBack,
        leadingIcon    = {
            Icon(
                imageVector        = Icons.AutoMirrored.Rounded.ArrowBack,
                contentDescription = "Back",
                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

// ── dynamic — body only ───────────────────────────────────────────────────────

@Composable
fun DisplayScreenBody(
    task    : Task,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp)
    ) {
        Text(
            text  = task.title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text     = task.description,
            style    = MaterialTheme.typography.bodyLarge,
            color    = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

// ── previews ──────────────────────────────────────────────────────────────────

private val previewTask1 =
    Task(1, "Buy groceries", "Milk, Eggs, Bread, Coffee", LeadingShapeType.HEART)
private val previewTask2 =
    Task(2, "KMP Project", "Sync repository and update dependencies", LeadingShapeType.COOKIE_6)

@Composable
private fun DisplayScreenPreview(task: Task) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DisplayScreenStatic(task = task, onBack = {})
        DisplayScreenBody(task = task, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "DisplayScreen – 412dp", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFull() {
    AppTheme(darkTheme = false) { DisplayScreenPreview(previewTask1) }
}

@Preview(name = "DisplayScreen – 320dp (compact)", showBackground = true, widthDp = 320)
@Composable
private fun PreviewCompact() {
    AppTheme(darkTheme = false) { DisplayScreenPreview(previewTask2) }
}