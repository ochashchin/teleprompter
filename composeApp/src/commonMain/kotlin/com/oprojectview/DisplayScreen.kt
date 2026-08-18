package com.oprojectview

import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.oprojectview.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.cd_back
import kotlinmultiplatform.composeapp.generated.resources.cd_help
import kotlinmultiplatform.composeapp.generated.resources.cd_more_options
import kotlinmultiplatform.composeapp.generated.resources.cd_play
import kotlinmultiplatform.composeapp.generated.resources.display_animation
import kotlinmultiplatform.composeapp.generated.resources.display_animation_desc
import kotlinmultiplatform.composeapp.generated.resources.display_distortion
import kotlinmultiplatform.composeapp.generated.resources.display_distortion_desc
import kotlinmultiplatform.composeapp.generated.resources.display_mirror
import kotlinmultiplatform.composeapp.generated.resources.display_mirror_desc
import kotlinmultiplatform.composeapp.generated.resources.display_orientation
import kotlinmultiplatform.composeapp.generated.resources.display_orientation_desc
import kotlinmultiplatform.composeapp.generated.resources.display_overlay
import kotlinmultiplatform.composeapp.generated.resources.display_overlay_desc
import kotlinmultiplatform.composeapp.generated.resources.display_speed
import kotlinmultiplatform.composeapp.generated.resources.display_speed_desc
import kotlinmultiplatform.composeapp.generated.resources.display_text_size
import kotlinmultiplatform.composeapp.generated.resources.display_text_size_desc
import kotlinmultiplatform.composeapp.generated.resources.display_transition
import kotlinmultiplatform.composeapp.generated.resources.display_transition_desc
import kotlinmultiplatform.composeapp.generated.resources.fab_play
import kotlinmultiplatform.composeapp.generated.resources.ic_animation
import kotlinmultiplatform.composeapp.generated.resources.ic_distortion
import kotlinmultiplatform.composeapp.generated.resources.ic_mirror
import kotlinmultiplatform.composeapp.generated.resources.ic_orientation
import kotlinmultiplatform.composeapp.generated.resources.ic_overlay
import kotlinmultiplatform.composeapp.generated.resources.ic_speed
import kotlinmultiplatform.composeapp.generated.resources.ic_text_size
import kotlinmultiplatform.composeapp.generated.resources.ic_help
import kotlinmultiplatform.composeapp.generated.resources.ic_loop
import kotlinmultiplatform.composeapp.generated.resources.ic_transition
import kotlinmultiplatform.composeapp.generated.resources.option_disabled
import kotlinmultiplatform.composeapp.generated.resources.option_distortion_0
import kotlinmultiplatform.composeapp.generated.resources.option_distortion_45
import kotlinmultiplatform.composeapp.generated.resources.option_distortion_70
import kotlinmultiplatform.composeapp.generated.resources.option_enabled
import kotlinmultiplatform.composeapp.generated.resources.option_fade
import kotlinmultiplatform.composeapp.generated.resources.option_fast
import kotlinmultiplatform.composeapp.generated.resources.option_frame
import kotlinmultiplatform.composeapp.generated.resources.option_horizontal
import kotlinmultiplatform.composeapp.generated.resources.option_huge
import kotlinmultiplatform.composeapp.generated.resources.option_inline
import kotlinmultiplatform.composeapp.generated.resources.option_large
import kotlinmultiplatform.composeapp.generated.resources.option_massive
import kotlinmultiplatform.composeapp.generated.resources.option_maximize
import kotlinmultiplatform.composeapp.generated.resources.option_none
import kotlinmultiplatform.composeapp.generated.resources.option_overlay_camera
import kotlinmultiplatform.composeapp.generated.resources.option_overlay_none
import kotlinmultiplatform.composeapp.generated.resources.option_overlay_window
import kotlinmultiplatform.composeapp.generated.resources.option_normal
import kotlinmultiplatform.composeapp.generated.resources.option_print
import kotlinmultiplatform.composeapp.generated.resources.option_scroll
import kotlinmultiplatform.composeapp.generated.resources.option_slow
import kotlinmultiplatform.composeapp.generated.resources.option_small
import kotlinmultiplatform.composeapp.generated.resources.option_vertical
import kotlinmultiplatform.composeapp.generated.resources.screen_display
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlinmultiplatform.composeapp.generated.resources.cd_loop

// ── data ──────────────────────────────────────────────────────────────────────

data class DisplayTaskItem(
    val id: Int,
    val leadingIconRes: DrawableResource,
    val titleRes: StringResource,
    val descriptionRes: StringResource,
    val optionRes: List<StringResource>,
    val defaultOptionRes: StringResource,
) {
    /** Index of [defaultOptionRes] within [optionRes]. Falls back to 0 if not found. */
    val defaultIndex: Int get() = optionRes.indexOf(defaultOptionRes).coerceAtLeast(0)
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
    contentPadding: PaddingValues,
    result:              TextFitResult,
    pages:               List<String>,
    textStyle:           TextStyle,
    isHorizontal:        Boolean,
    animationMode:       AnimationMode  = AnimationMode.Frame,
    transitionMode:      TransitionMode = TransitionMode.None,
    styleSpans:          List<StyleSpan> = emptyList(),
    fillColor:           Color          = Color.Transparent,
    fullText:            String         = "",
    preview:             Boolean        = false,
    countdownDone:         Boolean        = false,
    initialScrollFraction: Float          = 0f,
    onScrollFraction:      (Float) -> Unit = {},
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
                    contentPadding = contentPadding,
                    isHorizontal = isHorizontal,
                    wpm = wpm,
                    transitionMode = transitionMode,
                    styleSpans = styleSpans,
                    fillColor         = fillColor,
                    preview = preview,
                    countdownDone = countdownDone,
                    initialScrollFraction = initialScrollFraction,
                    onScrollFraction = onScrollFraction,
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
                    contentPadding = contentPadding,
                    isHorizontal = isHorizontal,
                    transitionMode = transitionMode,
                    styleSpans = styleSpans,
                    fillColor         = fillColor,
                    fullText = fullText,
                    preview = preview,
                    countdownDone = countdownDone,
                    initialScrollFraction = initialScrollFraction,
                    onScrollFraction = onScrollFraction,
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
                    contentPadding = contentPadding,
                    styleSpans = styleSpans,
                    fillColor         = fillColor,
                    preview = preview,
                    countdownDone = countdownDone,
                    initialScrollFraction = initialScrollFraction,
                    onScrollFraction = onScrollFraction,
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
    var showHelpMenu by remember { mutableStateOf(false) }

    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth()
    ) {
        ToolBar(
            title = stringResource(Res.string.screen_display),
            onLeadingClick = onBack,
            leadingIcon = {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = stringResource(Res.string.cd_back),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            onTrailingClick = { showHelpMenu = true },
            trailingIcon = {
                Icon(
                    painter            = painterResource(Res.drawable.ic_help),
                    contentDescription = stringResource(Res.string.cd_help),
                )
            }
        )

        HelpDisplayBottomSheet(
            expanded = showHelpMenu,
            onDismissRequest = { showHelpMenu = false }
        )
    }
}

// ── DisplayScreenBody ─────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DisplayScreenBody(
    task: Task,
    onPlayClick: (overlayEnabled: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    styleSpans: List<StyleSpan> = emptyList(),
    fillColor: Color = Color.Transparent,
) {

    val displayState = rememberDisplayTaskState(task.id)

    val textSizeItem    = DisplayTaskList.first { it.id == 1 }
    val orientationItem = DisplayTaskList.first { it.id == 2 }
    val speedItem       = DisplayTaskList.first { it.id == 3 }
    val animationItem   = DisplayTaskList.first { it.id == 4 }
    val transitionItem  = DisplayTaskList.first { it.id == 5 }
    val distortionItem  = DisplayTaskList.first { it.id == 6 }
    val mirrorItem      = DisplayTaskList.first { it.id == 7 }
    val overlayItem     = DisplayTaskList.first { it.id == 8 }

    var selectedSizeIndex        by remember(task.id) {
        mutableStateOf(
            displayState.selectedIndex(textSizeItem) ?: textSizeItem.defaultIndex
        )
    }

    var selectedOrientationIndex by remember(task.id) {
        mutableStateOf(
            displayState.selectedIndex(orientationItem) ?: orientationItem.defaultIndex
        )
    }

    var selectedSpeedIndex       by remember(task.id) {
        mutableStateOf(
            displayState.selectedIndex(speedItem) ?: speedItem.defaultIndex
        )
    }

    var selectedAnimationIndex   by remember(task.id) {
        mutableStateOf(
            displayState.selectedIndex(animationItem) ?: animationItem.defaultIndex
        )
    }

    var selectedTransitionIndex  by remember(task.id) {
        mutableStateOf(
            displayState.selectedIndex(transitionItem) ?: transitionItem.defaultIndex
        )
    }

    var selectedDistortionIndex  by remember(task.id) {
        mutableStateOf(
            displayState.selectedIndex(distortionItem) ?: distortionItem.defaultIndex
        )
    }

    var selectedMirrorIndex      by remember(task.id) {
        mutableStateOf(
            displayState.selectedIndex(mirrorItem) ?: mirrorItem.defaultIndex
        )
    }

    var selectedOverlayIndex     by remember(task.id) {
        mutableStateOf(
            displayState.selectedIndex(overlayItem) ?: overlayItem.defaultIndex
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

    val previewPaddingH = 16.dp
    val previewPaddingV = 8.dp

    val playButtonFocusRequester = remember { FocusRequester() }
    val lastListItemFocusRequester = remember { FocusRequester() }
    val firstItemFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        delay(100)
        try {
            firstItemFocusRequester.requestFocus()
        } catch (_: Exception) {}
    }

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
                    modifier = Modifier.fillMaxSize(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface,
                ) {
                    DisplayTextBar(
                        task              = task,
                        wpm               = wpm,
                        textStyle         = textStyle,
                        contentPadding    = PaddingValues(horizontal = previewPaddingH, vertical = previewPaddingV),
                        isHorizontal      = isHorizontal,
                        isMirror          = isMirror,
                        distortionMode    = distortionMode,
                        animationMode     = animationMode,
                        transitionMode    = transitionMode,
                        styleSpans        = styleSpans,
                        fillColor         = fillColor,
                        preview           = true,
                        modifier          = Modifier.fillMaxSize(),
                    )
                }
            }
            // ── Settings list ────────────────────────────────────────────────
            SegmentedList(
                items         = DisplayTaskList,
                displayState  = displayState,
                modifier      = Modifier.padding(top = 12.dp),
                firstItemFocusRequester = firstItemFocusRequester,
                playButtonFocusRequester = playButtonFocusRequester,
                lastListItemFocusRequester = lastListItemFocusRequester,
                onSelectionChanged = { item, index ->
                    when (item.id) {
                        1 -> selectedSizeIndex        = index
                        2 -> selectedOrientationIndex = index
                        3 -> selectedSpeedIndex       = index
                        4 -> selectedAnimationIndex   = index
                        5 -> selectedTransitionIndex  = index
                        6 -> selectedDistortionIndex  = index
                        7 -> selectedMirrorIndex      = index
                        8 -> selectedOverlayIndex     = index
                    }
                }
            )
        }

        // ── Play FAB ─────────────────────────────────────────────────────────
        FabBarLayout(
            text    = stringResource(Res.string.fab_play),
            onClick = { onPlayClick(selectedOverlayIndex == 2) },
            focusRequester = playButtonFocusRequester,
            buttonModifier = Modifier.focusProperties {
                up = lastListItemFocusRequester
            },
            modifier = Modifier.fillMaxSize(),
            icon = {
                Icon(
                    imageVector        = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(Res.string.cd_play),
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
    var selectedOptionRes by remember(
        displayState.taskId,
        item.id
    ) { mutableStateOf(displayState.selectedOptionRes(item)) }
    var menuExpanded by remember { mutableStateOf(false) }

    val leadingPainter: Painter = painterResource(item.leadingIconRes)
    var isDpadFocused by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .onFocusChanged { isDpadFocused = it.isFocused }
            .then(
                if (isDpadFocused)
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                else Modifier
            ),
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
                                contentDescription = stringResource(item.titleRes),
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
                            text     = stringResource(item.titleRes),
                            style    = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color    = MaterialTheme.colorScheme.onSurface,
                        )

                        // null → hint   |   value → persisted user selection
                        Text(
                            text     = selectedOptionRes?.let { stringResource(it) } ?: stringResource(item.descriptionRes),
                            style    = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                            color    = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                // ── Loop button ──────────────────────────────

                if (item.id == 4) {
                    val isLoopEnabled = displayState.isAnimationLoopEnabled()
                    val loopAlpha by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isLoopEnabled) 1.0f else 0.2f,
                        animationSpec = androidx.compose.animation.core.tween(200)
                    )

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
                                    onClick  = { displayState.setAnimationLoopEnabled(!isLoopEnabled) },
                                    modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                                ) {
                                    Icon(
                                        painter            = painterResource(Res.drawable.ic_loop),
                                        contentDescription = stringResource(Res.string.cd_loop),
                                        tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = loopAlpha),
                                        modifier           = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
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
                                    contentDescription = stringResource(Res.string.cd_more_options),
                                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier           = Modifier.fillMaxSize(),
                                )
                            }

                            DropdownMenu(
                                expanded        = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                item.optionRes.forEachIndexed { index, optionRes ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text     = stringResource(optionRes),
                                                fontSize = fontSize(18.dp),
                                                style    = MaterialTheme.typography.bodyMedium
                                            )
                                        },
                                        onClick = {
                                            selectedOptionRes = optionRes
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
    firstItemFocusRequester: FocusRequester? = null,
    playButtonFocusRequester: FocusRequester? = null,
    lastListItemFocusRequester: FocusRequester? = null,
    onSelectionChanged: (DisplayTaskItem, Int) -> Unit = { _, _ -> },
) {
    LazyColumn(
        modifier        = modifier.fillMaxSize(),
        contentPadding  = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(items = items, key = { _, item -> item.id }) { index, item ->
            val itemShape = if (items.size == 1) {
                RoundedCornerShape(28.dp)
            } else if (index == 0) {
                RoundedCornerShape(
                    topStart    = 28.dp,
                    topEnd      = 28.dp,
                    bottomStart = 8.dp,
                    bottomEnd   = 8.dp
                )
            } else if (index == items.lastIndex) {
                RoundedCornerShape(
                    topStart    = 8.dp,
                    topEnd      = 8.dp,
                    bottomStart = 28.dp,
                    bottomEnd   = 28.dp
                )
            } else {
                RoundedCornerShape(8.dp)
            }

            val isLastItem = index == items.lastIndex
            var itemModifier = if (playButtonFocusRequester != null) {
                Modifier.focusProperties {
                    right = playButtonFocusRequester
                    if (isLastItem) {
                        down = playButtonFocusRequester
                    }
                }
            } else {
                Modifier
            }

            if (index == 0 && firstItemFocusRequester != null) {
                itemModifier = itemModifier.focusRequester(firstItemFocusRequester)
            }

            if (isLastItem && lastListItemFocusRequester != null) {
                itemModifier = itemModifier.focusRequester(lastListItemFocusRequester)
            }

            SegmentedListItem(
                item               = item,
                displayState       = displayState,
                shape              = itemShape,
                modifier           = itemModifier,
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
        Res.string.display_text_size,
        Res.string.display_text_size_desc,
        listOf(
            Res.string.option_small,
            Res.string.option_normal,
            Res.string.option_large,
            Res.string.option_huge,
            Res.string.option_massive,
            Res.string.option_maximize,
        ),
        Res.string.option_normal,
    ),
    DisplayTaskItem(
        2,
        Res.drawable.ic_orientation,
        Res.string.display_orientation,
        Res.string.display_orientation_desc,
        listOf(
            Res.string.option_vertical,
            Res.string.option_horizontal,
        ),
        Res.string.option_vertical,
    ),
    DisplayTaskItem(
        3,
        Res.drawable.ic_speed,
        Res.string.display_speed,
        Res.string.display_speed_desc,
        listOf(
            Res.string.option_slow,
            Res.string.option_normal,
            Res.string.option_fast,
        ),
        Res.string.option_slow,
    ),
    DisplayTaskItem(
        4,
        Res.drawable.ic_animation,
        Res.string.display_animation,
        Res.string.display_animation_desc,
        listOf(
            Res.string.option_frame,
            Res.string.option_scroll,
            Res.string.option_inline,
        ),
        Res.string.option_frame,
    ),
    DisplayTaskItem(
        5,
        Res.drawable.ic_transition,
        Res.string.display_transition,
        Res.string.display_transition_desc,
        listOf(
            Res.string.option_none,
            Res.string.option_fade,
            Res.string.option_print,
        ),
        Res.string.option_none,
    ),
    DisplayTaskItem(
        6,
        Res.drawable.ic_distortion,
        Res.string.display_distortion,
        Res.string.display_distortion_desc,
        listOf(
            Res.string.option_distortion_0,
            Res.string.option_distortion_45,
            Res.string.option_distortion_70,
        ),
        Res.string.option_distortion_0,
    ),
    DisplayTaskItem(
        7,
        Res.drawable.ic_mirror,
        Res.string.display_mirror,
        Res.string.display_mirror_desc,
        listOf(
            Res.string.option_disabled,
            Res.string.option_enabled,
        ),
        Res.string.option_disabled,
    ),
    DisplayTaskItem(
        8,
        Res.drawable.ic_overlay,
        Res.string.display_overlay,
        Res.string.display_overlay_desc,
        listOf(
            Res.string.option_overlay_none,
            Res.string.option_overlay_camera,
            Res.string.option_overlay_window,
        ),
        Res.string.option_overlay_none,
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
        DisplayScreenBody(task = task, onPlayClick = { _ -> }, modifier = Modifier.fillMaxWidth())
    }
}

@Preview(name = "DisplayScreen – 412dp light", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFull() {
    AppTheme(darkTheme = false) {
        DisplayScreenPreview(
            previewTask1
        )
    }
}

@Preview(name = "DisplayScreen – 412dp dark", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFullDark() {
    AppTheme(darkTheme = true) {
        DisplayScreenPreview(
            previewTask1
        )
    }
}

@Preview(name = "DisplayScreen – 320dp (compact)", showBackground = true, widthDp = 320)
@Composable
private fun PreviewCompact() {
    AppTheme(darkTheme = false) {
        DisplayScreenPreview(
            previewTask2
        )
    }
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
            fontSize = fontSize(24.dp),
            lineHeight = fontSize(27.dp),
        )
        val contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        val wpm = WPM_NORMAL
        var result by remember { mutableStateOf<TextFitResult?>(null) }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            TextFitCalculator(
                text = PLAYER_PREVIEW_TEXT,
                fontSize = textStyle.fontSize,
                lineHeight = textStyle.lineHeight,
                contentPadding = contentPadding,
                isHorizontal = false,
                modifier = Modifier.fillMaxSize(),
                onResult = { result = it },
            )
            result?.let {
                TextFitPlayer(
                    result = it,
                    pages = remember(it.pagesText) { it.pagesText.take(2) },
                    wpm = wpm,
                    textStyle = textStyle,
                    contentPadding = contentPadding,
                    isHorizontal = false,
                    animationMode = animationMode,
                    transitionMode = transitionMode,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}