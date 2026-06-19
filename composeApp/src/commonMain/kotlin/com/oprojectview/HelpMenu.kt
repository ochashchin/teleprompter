package com.oprojectview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

data class HelpItem(
    val iconVector: ImageVector? = null,
    val iconDrawable: DrawableResource? = null,
    val textRes: StringResource
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelpBottomSheet(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    items: List<HelpItem>,
    modifier: Modifier = Modifier
) {
    if (expanded) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            dragHandle = { BottomSheetDefaults.DragHandle() },
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 32.dp)
            ) {
                items.forEach { item ->
                    HelpMenuItem(item = item)
                }
            }
        }
    }
}

@Composable
private fun HelpMenuItem(item: HelpItem) {
    val fullText = stringResource(item.textRes)
    val annotatedText = buildAnnotatedString {
        val parts = fullText.split(" — ", limit = 2)
        if (parts.size == 2) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                append(parts[0])
            }
            append(" — " + parts[1])
        } else {
            append(fullText)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // ── Leading ───────────────────────────────────────
        Box(
            modifier = Modifier
                .padding(horizontal = 14.dp, vertical = 2.dp)
                .size(28.dp),
            contentAlignment = Alignment.Center
        ) {
            if (item.iconVector != null) {
                Icon(
                    imageVector = item.iconVector,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp)
                )
            } else if (item.iconDrawable != null) {
                Icon(
                    painter = painterResource(item.iconDrawable),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        // ── Content ───────────────────────────────────────
        Box(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(
                text = annotatedText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
