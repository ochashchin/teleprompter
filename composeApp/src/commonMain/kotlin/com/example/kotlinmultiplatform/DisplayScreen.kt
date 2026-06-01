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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
)

// ── Speed → WPM mapping ───────────────────────────────────────────────────────

private const val WPM_SLOW = 100
private const val WPM_NORMAL = 130
private const val WPM_FAST = 150

fun speedLabelToWpm(label: String?): Int = when {
    label.equals("Fast", ignoreCase = true) -> WPM_FAST
    label.equals("Normal", ignoreCase = true) -> WPM_NORMAL
    else -> WPM_SLOW   // "Slow" or unset
}


// ── AnimationMode ─────────────────────────────────────────────────────────────

enum class AnimationMode { Frame, Scroll, Inline }
enum class TransitionMode { None, Fade, Print }

private fun animationModeOf(label: String?): AnimationMode = when {
    label.equals("Scroll", ignoreCase = true) -> AnimationMode.Scroll
    label.equals("Inline", ignoreCase = true) -> AnimationMode.Inline
    else -> AnimationMode.Frame
}

private fun transitionModeOf(label: String?): TransitionMode = when {
    label.equals("Fade", ignoreCase = true) -> TransitionMode.Fade
    label.equals("Print", ignoreCase = true) -> TransitionMode.Print
    else -> TransitionMode.None
}

// ── Distortion → scaleY multiplier ───────────────────────────────────────────
// 0° = 1.0  (no-op),  45° = 1.4,  70° = 2.9

fun distortionValueOf(label: String?): Float = when {
    label.equals("45°", ignoreCase = true) -> 1.4f
    label.equals("70°", ignoreCase = true) -> 2.9f
    else -> 1f   // "0°" or unset
}

// ── PreviewPlayer — switches between Frame / Scroll / Inline ─────────────────

@Composable
fun PreviewPlayer(
    wpm: Int,
    padding: Dp,
    result: TextFitResult,
    textStyle: TextStyle,
    isHorizontal: Boolean,
    animationMode: AnimationMode = AnimationMode.Frame,
    transitionMode: TransitionMode = TransitionMode.None,
    modifier: Modifier = Modifier,
) {
    val previewPages = remember(result.pagesText) { result.pagesText.take(2) }
    var alpha        by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(isHorizontal, animationMode, transitionMode){
        alpha = 1f
    }

    DisposableEffect(isHorizontal, animationMode, transitionMode) {
        onDispose {
            alpha = 0f
        }
    }

    Box(modifier = modifier.graphicsLayer { this.alpha = alpha }) {

        when (animationMode) {

            // ── Frame: page cycling + transitionMode handled inside TextFitBox ────
            AnimationMode.Frame -> {
                TextFitBox(
                    pages = previewPages,
                    linesPerParent = result.linesPerParent,
                    textStyle = textStyle,
                    padding = padding,
                    isHorizontal = isHorizontal,
                    wpm = wpm,
                    transitionMode = transitionMode,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── Scroll: centre-scroll with transitionMode letter-alpha ────────────
            AnimationMode.Scroll -> {
                TextCentreVerticalScrollBox(
                    pages = previewPages,
                    wpm = wpm,
                    textStyle = textStyle,
                    padding = padding,
                    isHorizontal = isHorizontal,
                    transitionMode = transitionMode,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // ── Inline: horizontal auto-scroll ────────────────────────────────────
            AnimationMode.Inline -> {

                val page            = calculatePageDurationMs(previewPages[0], wpm)
                val frameDurationMs = previewPages.sumOf { calculatePageDurationMs(it, wpm) }
                val totalDurationMs = frameDurationMs + page * 2

                TextHorizontalScrollBox(
                    totalDurationMs = totalDurationMs,
                    text = remember(previewPages) {
                        previewPages
                            .joinToString(separator = " ")
                            .replace("\r", "")
                            .replace("\n", "")
                    },
                    textStyle = textStyle,
                    transitionMode = transitionMode,
                    isHorizontal = isHorizontal,
                    padding = padding,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

// ── static — toolbar ──────────────────────────────────────────────────────────

@Composable
fun DisplayScreenStatic(onBack: () -> Unit) {
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

private fun textSizeTriple(label: String?) =
    NORMAL_SIZES.firstOrNull { it.first.equals(label, ignoreCase = true) }
        ?: NORMAL_SIZES[1]   // fallback: "Normal"

// ── DisplayScreenBody ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DisplayScreenBody(task: Task, modifier: Modifier = Modifier) {

    val displayState = rememberDisplayTaskState(task.id)

    val textSizeItem    = DisplayTaskList.first { it.id == 1 }
    val orientationItem = DisplayTaskList.first { it.id == 2 }
    val speedItem       = DisplayTaskList.first { it.id == 3 }
    val animationItem   = DisplayTaskList.first { it.id == 4 }
    val transitionItem  = DisplayTaskList.first { it.id == 5 }
    val distortionItem  = DisplayTaskList.first { it.id == 6 }

    var selectedSizeLabel by remember {
        mutableStateOf(
            displayState.selectedOption(textSizeItem)
                ?: textSizeItem.defaultOption
        )
    }

    var selectedOrientationLabel by remember {
        mutableStateOf(
            displayState.selectedOption(orientationItem)
                ?: orientationItem.defaultOption
        )
    }

    var selectedSpeedLabel by remember {
        mutableStateOf(
            displayState.selectedOption(speedItem)
                ?: speedItem.defaultOption
        )
    }

    var selectedAnimationLabel by remember {
        mutableStateOf(
            displayState.selectedOption(animationItem)
                ?: animationItem.defaultOption
        )
    }

    var selectedTransitionLabel by remember {
        mutableStateOf(
            displayState.selectedOption(transitionItem)
                ?: transitionItem.defaultOption
        )
    }

    var selectedDistortionLabel by remember {
        mutableStateOf(
            displayState.selectedOption(distortionItem)
                ?: distortionItem.defaultOption
        )
    }

    val isHorizontal    = selectedOrientationLabel.equals("Horizontal", ignoreCase = true)
    val animationMode   = animationModeOf(selectedAnimationLabel)
    val transitionMode  = transitionModeOf(selectedTransitionLabel)
    val distortionMode = distortionValueOf(selectedDistortionLabel)

    val (_, fontSizeDp, lineHeightDp) = textSizeTriple(selectedSizeLabel)

    val textStyle = MaterialTheme.typography.bodyMediumEmphasized.copy(
        fontSize   = fontSize1(fontSizeDp),
        lineHeight = fontSize1(lineHeightDp),
        color = MaterialTheme.colorScheme.onSurface
    )

    val wpm = speedLabelToWpm(selectedSpeedLabel)
    var result by remember { mutableStateOf<TextFitResult?>(null) }

    val previewPadding = 10.dp

    Column(
        modifier = modifier.fillMaxWidth(),
    ) {
        // ── Preview surface ──────────────────────────────────────────────────
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
                BoxWithConstraints(
                    modifier = Modifier
                        .graphicsLayer {
                            scaleX = if (isHorizontal) distortionMode else 1f
                            scaleY = if (isHorizontal) 1f else distortionMode
                        }
                        .fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    val playerSize = Modifier
                        .width(if (isHorizontal) maxWidth / distortionMode else maxWidth)
                        .height(if (isHorizontal) maxHeight else maxHeight / distortionMode)

                    TextFitCalculator(
                        text = task.description,
                        fontSize = textStyle.fontSize,
                        lineHeight = textStyle.lineHeight,
                        padding = previewPadding,
                        isHorizontal = isHorizontal,
                        modifier = playerSize,
                        onResult = { result = it },
                    )

                    result?.let {
                        PreviewPlayer(
                            result = result!!,
                            wpm = wpm,
                            textStyle = textStyle,
                            padding = previewPadding,
                            isHorizontal = isHorizontal,
                            animationMode = animationMode,
                            transitionMode = transitionMode,
                            modifier = playerSize,
                        )
                    }
                }
            }
        }
        // ── Settings list ────────────────────────────────────────────────────
        SegmentedList(
            items         = DisplayTaskList,
            displayState  = displayState,
            modifier      = modifier.padding(top = 8.dp),
            onSelectionChanged = { item, option ->
                if (item.id == 1) selectedSizeLabel        = option
                if (item.id == 2) selectedOrientationLabel = option
                if (item.id == 3) selectedSpeedLabel       = option
                if (item.id == 4) selectedAnimationLabel   = option
                if (item.id == 5) selectedTransitionLabel  = option
                if (item.id == 6) selectedDistortionLabel  = option
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
    onSelectionChanged: (DisplayTaskItem, String) -> Unit = { _, _ -> },
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
                                                fontSize = fontSize(20.dp),
                                                style    = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        onClick = {
                                            selectedOption = option
                                            displayState.setSelection(
                                                item        = item,
                                                optionIndex = index
                                            )
                                            onSelectionChanged(item, option)
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
    onSelectionChanged: (DisplayTaskItem, String) -> Unit = { _, _ -> },
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
        DisplayScreenBody(task = task, modifier = Modifier.fillMaxWidth())
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

// ── PreviewPlayer isolated previews ──────────────────────────────────────────
//
// One preview per AnimationMode × TransitionMode combination.
// Each is sized to match the actual preview surface (412 × 180).
// TextFitCalculator is invisible and drives result; PreviewPlayer renders once
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
            fontSize   = fontSize1(24.dp),
            lineHeight = fontSize1(27.dp),
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
                PreviewPlayer(
                    result         = it,
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
