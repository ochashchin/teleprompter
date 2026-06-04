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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
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
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// ── data ──────────────────────────────────────────────────────────────────────

data class DisplayTaskItem(
    val id: Int,
    val leadingIconRes: DrawableResource,
    val title: String,
    val description: String,
    val options: List<String>,
    val defaultOption: String,
) {
    /** Index of [defaultOption] within [options]. Falls back to 0 if not found. */
    val defaultIndex: Int get() = options.indexOf(defaultOption).coerceAtLeast(0)
}

// ── Speed → WPM mapping ───────────────────────────────────────────────────────

private const val WPM_SLOW = 100
private const val WPM_NORMAL = 130
private const val WPM_FAST = 150

// Speed options: index 0 = Slow, 1 = Normal, 2 = Fast
fun speedIndexToWpm(index: Int): Int = when (index) {
    2    -> WPM_FAST
    1    -> WPM_NORMAL
    else -> WPM_SLOW
}


// ── AnimationMode ─────────────────────────────────────────────────────────────

enum class AnimationMode { Frame, Scroll, Inline }
enum class TransitionMode { None, Fade, Print }

// Animation options: index 0 = Frame, 1 = Scroll, 2 = Inline
fun animationModeOf(index: Int): AnimationMode = when (index) {
    1    -> AnimationMode.Scroll
    2    -> AnimationMode.Inline
    else -> AnimationMode.Frame
}

// Transition options: index 0 = None, 1 = Fade, 2 = Print
fun transitionModeOf(index: Int): TransitionMode = when (index) {
    1    -> TransitionMode.Fade
    2    -> TransitionMode.Print
    else -> TransitionMode.None
}

// Distortion options: index 0 = 0° (1.0f), 1 = 45° (1.4f), 2 = 70° (2.9f)
fun distortionValueOf(index: Int): Float = when (index) {
    1    -> 1.4f
    2    -> 2.9f
    else -> 1f
}

// ── TextPlayer — switches between Frame / Scroll / Inline ────────────────────
//
// Callers supply the exact pages list; preview vs full is the caller's concern.


@Composable
fun TextFitPlayer(
    wpm:                 Int,
    padding:             Dp,
    result:              TextFitResult,
    pages:               List<String>,
    textStyle:           TextStyle,
    isHorizontal:        Boolean,
    animationMode:       AnimationMode  = AnimationMode.Frame,
    transitionMode:      TransitionMode = TransitionMode.None,
    preview:             Boolean        = false,
    modifier:            Modifier       = Modifier,
    onAnimationComplete: (() -> Unit)?  = null,
) {

    Box(modifier = modifier) {

        when (animationMode) {

            // ── Frame: page cycling + transitionMode handled inside TextFitBox ────
            AnimationMode.Frame -> {
                TextFitBox(
                    pages = pages,
                    linesPerParent = result.linesPerParent,
                    textStyle = textStyle,
                    padding = padding,
                    isHorizontal = isHorizontal,
                    wpm = wpm,
                    transitionMode = transitionMode,
                    preview = preview,
                    modifier = Modifier.fillMaxSize(),
                    onAnimationComplete = onAnimationComplete,
                )
            }

            // ── Scroll: centre-scroll with transitionMode letter-alpha ────────────
            AnimationMode.Scroll -> {
                TextCentreVerticalScrollBox(
                    pages = pages,
                    wpm = wpm,
                    textStyle = textStyle,
                    padding = padding,
                    isHorizontal = isHorizontal,
                    transitionMode = transitionMode,
                    preview = preview,
                    modifier = Modifier.fillMaxSize(),
                    onAnimationComplete = onAnimationComplete,
                )
            }

            // ── Inline: horizontal auto-scroll ────────────────────────────────────
            AnimationMode.Inline -> {
                TextHorizontalScrollBox(
                    pages = pages,
                    wpm = wpm,
                    textStyle = textStyle,
                    transitionMode = transitionMode,
                    isHorizontal = isHorizontal,
                    padding = padding,
                    preview = preview,
                    modifier = Modifier.fillMaxSize(),
                    onAnimationComplete = onAnimationComplete,
                )
            }
        }
    }
}

// ── static — toolbar ──────────────────────────────────────────────────────────

@Composable
fun DisplayScreenStatic(onBack: () -> Unit) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        ToolBar(
            title = "Display",
            onLeadingClick = onBack,
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
}

// ── DisplayScreenBody ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DisplayScreenBody(
    task: Task,
    onPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val displayState = rememberDisplayTaskState(task.id)

    val textSizeItem    = DisplayTaskList.first { it.id == 1 }
    val orientationItem = DisplayTaskList.first { it.id == 2 }
    val speedItem       = DisplayTaskList.first { it.id == 3 }
    val animationItem   = DisplayTaskList.first { it.id == 4 }
    val transitionItem  = DisplayTaskList.first { it.id == 5 }
    val distortionItem  = DisplayTaskList.first { it.id == 6 }
    val mirrorItem      = DisplayTaskList.first { it.id == 7 }

    var selectedSizeIndex        by remember {
        mutableStateOf(
            displayState.selectedIndex(textSizeItem) ?: textSizeItem.defaultIndex
        )
    }

    var selectedOrientationIndex by remember {
        mutableStateOf(
            displayState.selectedIndex(orientationItem) ?: orientationItem.defaultIndex
        )
    }

    var selectedSpeedIndex       by remember {
        mutableStateOf(
            displayState.selectedIndex(speedItem) ?: speedItem.defaultIndex
        )
    }

    var selectedAnimationIndex   by remember {
        mutableStateOf(
            displayState.selectedIndex(animationItem) ?: animationItem.defaultIndex
        )
    }

    var selectedTransitionIndex  by remember {
        mutableStateOf(
            displayState.selectedIndex(transitionItem) ?: transitionItem.defaultIndex
        )
    }

    var selectedDistortionIndex  by remember {
        mutableStateOf(
            displayState.selectedIndex(distortionItem) ?: distortionItem.defaultIndex
        )
    }

    var selectedMirrorIndex      by remember {
        mutableStateOf(
            displayState.selectedIndex(mirrorItem) ?: mirrorItem.defaultIndex
        )
    }

    // Orientation: index 0 = Vertical, 1 = Horizontal
    val isHorizontal    = selectedOrientationIndex == 1
    val animationMode   = animationModeOf(selectedAnimationIndex)
    val transitionMode  = transitionModeOf(selectedTransitionIndex)
    val distortionMode  = distortionValueOf(selectedDistortionIndex)
    // Mirror: index 0 = Disabled, 1 = Enabled
    val isMirror        = selectedMirrorIndex == 1

    val (_, fontSizeDp, lineHeightDp) = NORMAL_SIZES.getOrNull(selectedSizeIndex)
        ?: NORMAL_SIZES[1]   // fallback: "Normal"

    val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
        fontSize   = fontSize(fontSizeDp),
        lineHeight = fontSize(lineHeightDp),
        color = MaterialTheme.colorScheme.onSurface
    )

    val wpm = speedIndexToWpm(selectedSpeedIndex)

    val previewPadding = 10.dp

    Box(modifier = modifier
        .padding(top = 64.dp)
        .fillMaxWidth()
    ) {

        Column(modifier = Modifier.fillMaxWidth()) {
            // ── Preview surface ──────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    modifier = modifier,
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    DisplayTextBar(
                        task           = task,
                        wpm            = wpm,
                        textStyle      = textStyle,
                        padding        = previewPadding,
                        isHorizontal   = isHorizontal,
                        isMirror       = isMirror,
                        distortionMode = distortionMode,
                        animationMode  = animationMode,
                        transitionMode = transitionMode,
                        preview        = true,
                        modifier       = modifier,
                    )
                }
            }
            // ── Settings list ────────────────────────────────────────────────
            SegmentedList(
                items         = DisplayTaskList,
                displayState  = displayState,
                modifier      = modifier.padding(top = 8.dp),
                onSelectionChanged = { item, index ->
                    when (item.id) {
                        1 -> selectedSizeIndex        = index
                        2 -> selectedOrientationIndex = index
                        3 -> selectedSpeedIndex       = index
                        4 -> selectedAnimationIndex   = index
                        5 -> selectedTransitionIndex  = index
                        6 -> selectedDistortionIndex  = index
                        7 -> selectedMirrorIndex      = index
                    }
                }
            )
        }

        // ── Play FAB ─────────────────────────────────────────────────────────
        FabBarLayout(
            text    = "Play",
            onClick = onPlayClick,
            modifier = Modifier.fillMaxSize(),
            icon = {
                Icon(
                    imageVector        = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Play",
                    tint               = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier           = Modifier.size(24.dp),
                )
            }
        )
    }
}

@Composable
fun SegmentedListItem(
    item: DisplayTaskItem,
    displayState: DisplayTaskState,
    shape: Shape,
    modifier: Modifier = Modifier,
    onSelectionChanged: (DisplayTaskItem, Int) -> Unit = { _, _ -> },
) {
    // null = user never selected anything
    var selectedOption by remember(
        displayState.taskId,
        item.id
    ) { mutableStateOf(displayState.selectedOption(item)) }
    var menuExpanded by remember { mutableStateOf(false) }

    val leadingPainter: Painter = painterResource(item.leadingIconRes)

    Surface(
        modifier = modifier.fillMaxWidth(),
        onClick  = { menuExpanded = true },
        shape    = shape,
        color    = MaterialTheme.colorScheme.surface,
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier          = Modifier.fillMaxWidth().height(64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {

                // ── Leading icon ─────────────────────────────

                BoxWithConstraints(
                    modifier           = Modifier.fillMaxHeight().aspectRatio(48f / 64f),
                    contentAlignment   = Alignment.Center,
                ) {
                    Box(
                        modifier         = Modifier.fillMaxHeight().aspectRatio(48f / 28f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier         = Modifier.fillMaxHeight().aspectRatio(20f / 28f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter            = leadingPainter,
                                contentDescription = item.title,
                                tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier           = Modifier.fillMaxHeight().aspectRatio(1f),
                            )
                        }
                    }
                }

                // ── Content ──────────────────────────────────

                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier             = Modifier.fillMaxWidth(),
                        verticalArrangement  = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text     = item.title,
                            style    = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color    = MaterialTheme.colorScheme.onSurface,
                        )

                        // null → hint   |   value → persisted user selection
                        Text(
                            text     = selectedOption ?: item.description,
                            style    = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── More menu ────────────────────────────────

                BoxWithConstraints(
                    modifier         = Modifier.fillMaxHeight().aspectRatio(48f / 64f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier         = Modifier.fillMaxHeight().aspectRatio(48f / 28f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(modifier = Modifier.fillMaxHeight().aspectRatio(20f / 28f)) {
                            IconButton(
                                onClick  = { menuExpanded = true },
                                modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                            ) {
                                Icon(
                                    imageVector        = Icons.Rounded.MoreVert,
                                    contentDescription = "More options",
                                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier           = Modifier.fillMaxSize(),
                                )
                            }

                            DropdownMenu(
                                expanded        = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                item.options.forEachIndexed { index, option ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text     = option,
                                                fontSize = fontSize(18.dp),
                                                style    = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        onClick = {
                                            selectedOption = option
                                            displayState.setSelection(
                                                item        = item,
                                                optionIndex = index
                                            )
                                            onSelectionChanged(item, index)
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
    items: List<DisplayTaskItem>,
    displayState: DisplayTaskState,
    modifier: Modifier = Modifier,
    onSelectionChanged: (DisplayTaskItem, Int) -> Unit = { _, _ -> },
) {
    LazyColumn(
        modifier        = modifier.fillMaxSize(),
        contentPadding  = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(items = items, key = { _, item -> item.id }) { index, item ->
            val itemShape = when {
                items.size == 1 -> RoundedCornerShape(28.dp)
                index == 0 -> RoundedCornerShape(
                    topStart    = 28.dp,
                    topEnd      = 28.dp,
                    bottomStart = 8.dp,
                    bottomEnd   = 8.dp
                )
                index == items.lastIndex -> RoundedCornerShape(
                    topStart    = 8.dp,
                    topEnd      = 8.dp,
                    bottomStart = 28.dp,
                    bottomEnd   = 28.dp
                )
                else -> RoundedCornerShape(8.dp)
            }
            SegmentedListItem(
                item               = item,
                displayState       = displayState,
                shape              = itemShape,
                onSelectionChanged = onSelectionChanged,
            )
        }
    }
}

// ── DisplayTaskList data ───────────────────────────────────────────────────────

val DisplayTaskList: List<DisplayTaskItem> = listOf(
    DisplayTaskItem(
        1,
        Res.drawable.ic_text_size,
        "Text Size",
        "Small, normal, large, huge, massive",
        listOf("Small", "Normal", "Large", "Huge", "Massive"),
        "Normal"
    ),
    DisplayTaskItem(
        2,
        Res.drawable.ic_orientation,
        "Orientation",
        "Vertical, horizontal",
        listOf("Vertical", "Horizontal"),
        "Vertical"
    ),
    DisplayTaskItem(
        3,
        Res.drawable.ic_speed,
        "Speed",
        "Slow, normal, fast",
        listOf("Slow", "Normal", "Fast"),
        "Slow"
    ),
    DisplayTaskItem(
        4,
        Res.drawable.ic_animation,
        "Animation",
        "Frame, scroll, inline",
        listOf("Frame", "Scroll", "Inline"),
        "Frame"
    ),
    DisplayTaskItem(
        5,
        Res.drawable.ic_transition,
        "Transition",
        "None, fade, print",
        listOf("None", "Fade", "Print"),
        "None"
    ),
    DisplayTaskItem(
        6,
        Res.drawable.ic_distortion,
        "Distortion",
        "0°, 45°, 70°",
        listOf("0°", "45°", "70°"),
        "0°"
    ),
    DisplayTaskItem(
        7,
        Res.drawable.ic_mirror,
        "Mirror",
        "Disabled, enabled",
        listOf("Disabled", "Enabled"),
        "Disabled"
    ),
    DisplayTaskItem(
        8,
        Res.drawable.ic_overlay,
        "Overlay",
        "Disabled, enabled",
        listOf("Disabled", "Enabled"),
        "Disabled"
    ),
)

// ── dynamic — body only ───────────────────────────────────────────────────────

// ── previews ──────────────────────────────────────────────────────────────────

private val previewTask1 = Task(
    1,
    "Buy groceries",
    "Milk, Eggs, Bread, Coffee. Pick up from the store on the way home. Don't forget almond milk.",
    LeadingShapeType.HEART
)
private val previewTask2 = Task(
    2,
    "KMP Project",
    "Sync repository and update dependencies. Run all tests before merging the feature branch.",
    LeadingShapeType.COOKIE_6
)

@Composable
private fun DisplayScreenPreview(task: Task) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DisplayScreenStatic(onBack = {})
        DisplayScreenBody(task = task, onPlayClick = {}, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "DisplayScreen – 412dp light", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFull() {
    AppTheme(darkTheme = false) { DisplayScreenPreview(previewTask1) }
}

@Preview(name = "DisplayScreen – 412dp dark", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFullDark() {
    AppTheme(darkTheme = true) { DisplayScreenPreview(previewTask1) }
}

@Preview(name = "DisplayScreen – 320dp (compact)", showBackground = true, widthDp = 320)
@Composable
private fun PreviewCompact() {
    AppTheme(darkTheme = false) { DisplayScreenPreview(previewTask2) }
}

// ── TextPlayer isolated previews ─────────────────────────────────────────────
//
// One preview per AnimationMode × TransitionMode combination.
// Each is sized to match the actual preview surface (412 × 180).
// TextFitCalculator is invisible and drives result; TextPlayer renders once
// result is available.
// ─────────────────────────────────────────────────────────────────────────────

private const val PLAYER_PREVIEW_TEXT =
    "Milk, Eggs, Bread, Coffee. Pick up from the store on the way home. Don't forget almond milk."

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerPreviewSurface(
    animationMode:  AnimationMode,
    transitionMode: TransitionMode,
) {
    AppTheme {
        val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
            fontSize   = fontSize(24.dp),
            lineHeight = fontSize(27.dp),
        )
        val padding = 10.dp
        val wpm     = WPM_NORMAL
        var result  by remember { mutableStateOf<TextFitResult?>(null) }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            TextFitCalculator(
                text         = PLAYER_PREVIEW_TEXT,
                fontSize     = textStyle.fontSize,
                lineHeight   = textStyle.lineHeight,
                padding      = padding,
                isHorizontal = false,
                modifier     = Modifier.fillMaxSize(),
                onResult     = { result = it },
            )
            result?.let {
                TextFitPlayer(
                    result         = it,
                    pages          = remember(it.pagesText) { it.pagesText.take(2) },
                    wpm            = wpm,
                    textStyle      = textStyle,
                    padding        = padding,
                    isHorizontal   = false,
                    animationMode  = animationMode,
                    transitionMode = transitionMode,
                    modifier       = Modifier.fillMaxSize(),
                )
            }
        }
    }
}