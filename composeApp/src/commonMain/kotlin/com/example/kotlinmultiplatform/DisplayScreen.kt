package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kotlinmultiplatform.ui.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.ic_animation
import kotlinmultiplatform.composeapp.generated.resources.ic_distortion
import kotlinmultiplatform.composeapp.generated.resources.ic_mirror
import kotlinmultiplatform.composeapp.generated.resources.ic_orientation
import kotlinmultiplatform.composeapp.generated.resources.ic_overlay
import kotlinmultiplatform.composeapp.generated.resources.ic_speed
import kotlinmultiplatform.composeapp.generated.resources.ic_text_size
import kotlinmultiplatform.composeapp.generated.resources.ic_transition

// ── data ──────────────────────────────────────────────────────────────────────

data class DisplayTaskItem(
    val id            : Int,
    val leadingIconRes: DrawableResource,
    val title         : String,
    val description   : String,
    val options       : List<String>,
    val defaultOption : String,
)

// ── static — toolbar ──────────────────────────────────────────────────────────

@Composable
fun DisplayScreenStatic(
    onBack : () -> Unit,
) {
    ToolBar(
        title          = "Display",
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

// ── SegmentedListItem ─────────────────────────────────────────────────────────

@Composable
fun SegmentedListItem(
    item    : DisplayTaskItem,
    shape   : Shape,
    modifier: Modifier = Modifier,
) {
    // Each item memorises its own selected option independently
    var selectedOption by rememberSaveable(item.id) {
        mutableStateOf(item.defaultOption)
    }
    var menuExpanded by remember { mutableStateOf(false) }

    val leadingPainter: Painter = painterResource(item.leadingIconRes)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                // ── Leading icon ──────────────────────────────────────
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(48f / 64f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(48f / 28f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .aspectRatio(20f / 28f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = leadingPainter,
                                contentDescription = item.title,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(1f),
                            )
                        }
                    }
                }

                // ── Content — title + description ─────────────────────
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        // Show selectedOption when user has made a choice,
                        // otherwise show the full description of all options
                        Text(
                            text = if (selectedOption != item.defaultOption)
                                selectedOption
                            else
                                item.description,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Trailing — MoreVert + DropdownMenu ────────────────
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(48f / 64f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .aspectRatio(48f / 28f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box {
                            IconButton(
                                onClick = { menuExpanded = true },
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(20f / 28f),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MoreVert,
                                    contentDescription = "More options",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(1f),
                                )
                            }

                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                item.options.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = option,
                                                fontSize = fontSize(28.dp)
                                            )
                                        },
                                        onClick = {
                                            selectedOption = option
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

// ── SegmentedList ─────────────────────────────────────────────────────────────

@Composable
fun SegmentedList(
    items   : List<DisplayTaskItem>,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            items = items,
            key   = { _, item -> item.id },
        ) { index, item ->

            val itemShape = when {
                items.size == 1          -> RoundedCornerShape(28.dp)
                index == 0               -> RoundedCornerShape(
                    topStart    = 28.dp,
                    topEnd      = 28.dp,
                    bottomStart = 8.dp,
                    bottomEnd   = 8.dp,
                )
                index == items.lastIndex -> RoundedCornerShape(
                    topStart    = 8.dp,
                    topEnd      = 8.dp,
                    bottomStart = 28.dp,
                    bottomEnd   = 28.dp,
                )
                else                     -> RoundedCornerShape(8.dp)
            }

            SegmentedListItem(
                item  = item,
                shape = itemShape,
            )
        }
    }
}

// ── DisplayTaskList data ───────────────────────────────────────────────────────

val DisplayTaskList: List<DisplayTaskItem> = listOf(
    DisplayTaskItem(
        id             = 1,
        leadingIconRes = Res.drawable.ic_text_size,
        title          = "Text Size",
        description    = "Small, normal, large, huge, massive, maximize",
        options        = listOf("Small", "Normal", "Large", "Huge", "Massive", "Maximize"),
        defaultOption  = "Normal",
    ),
    DisplayTaskItem(
        id             = 2,
        leadingIconRes = Res.drawable.ic_orientation,
        title          = "Orientation",
        description    = "Vertical, horizontal",
        options        = listOf("Vertical", "Horizontal"),
        defaultOption  = "Vertical",
    ),
    DisplayTaskItem(
        id             = 3,
        leadingIconRes = Res.drawable.ic_speed,
        title          = "Speed",
        description    = "Slow, normal, fast",
        options        = listOf("Slow", "Normal", "Fast"),
        defaultOption  = "Slow",
    ),
    DisplayTaskItem(
        id             = 4,
        leadingIconRes = Res.drawable.ic_animation,
        title          = "Animation",
        description    = "None, slide, scroll",
        options        = listOf("None", "Slide", "Scroll"),
        defaultOption  = "None",
    ),
    DisplayTaskItem(
        id             = 5,
        leadingIconRes = Res.drawable.ic_transition,
        title          = "Transition",
        description    = "None, fade, print",
        options        = listOf("None", "Fade", "Print"),
        defaultOption  = "None",
    ),
    DisplayTaskItem(
        id             = 6,
        leadingIconRes = Res.drawable.ic_distortion,
        title          = "Distortion",
        description    = "0°, 45°, 70°",
        options        = listOf("0°", "45°", "70°"),
        defaultOption  = "0°",
    ),
    DisplayTaskItem(
        id             = 7,
        leadingIconRes = Res.drawable.ic_mirror,
        title          = "Mirror",
        description    = "Disabled, enabled",
        options        = listOf("Disabled", "Enabled"),
        defaultOption  = "Disabled",
    ),
    DisplayTaskItem(
        id             = 8,
        leadingIconRes = Res.drawable.ic_overlay,
        title          = "Overlay",
        description    = "Disabled, enabled",
        options        = listOf("Disabled", "Enabled"),
        defaultOption  = "Disabled",
    ),
)

// ── dynamic — body only ───────────────────────────────────────────────────────

@Composable
fun DisplayScreenBody(
    task    : Task,
    modifier: Modifier = Modifier,
) {
    SegmentedList(
        items    = DisplayTaskList,
        modifier = modifier,
    )
}

// ── previews ──────────────────────────────────────────────────────────────────

private val previewTask1 =
    Task(1, "Buy groceries", "Milk, Eggs, Bread, Coffee", LeadingShapeType.HEART)
private val previewTask2 =
    Task(2, "KMP Project", "Sync repository and update dependencies", LeadingShapeType.COOKIE_6)

@Composable
private fun DisplayScreenPreview(task: Task) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DisplayScreenStatic(onBack = {})
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