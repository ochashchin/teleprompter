package com.oprojectview

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oprojectview.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.cd_delete
import kotlinmultiplatform.composeapp.generated.resources.cd_edit
import kotlinmultiplatform.composeapp.generated.resources.cd_open
import kotlinmultiplatform.composeapp.generated.resources.cd_search
import kotlinmultiplatform.composeapp.generated.resources.fab_new
import kotlinmultiplatform.composeapp.generated.resources.screen_tasks
import kotlinmultiplatform.composeapp.generated.resources.search_hint
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
enum class LeadingShapeType {
    ARCH, FAN, ARROW, SLANTED, OVAL, PILL, TRIANGLE, DIAMOND,
    CLAM_SHELL, PENTAGON, GEM, SUNNY, VERY_SUNNY,
    COOKIE_4, COOKIE_6, COOKIE_7, COOKIE_9, COOKIE_12,
    BURST, SOFT_BURST, BOOM, SOFT_BOOM, FLOWER,
    PUFFY, PUFFY_DIAMOND, CIRCLE, HEART,
    GHOSTISH, CLOVER_4, CLOVER_8;

    fun polygon() = when (this) {
        ARCH          -> MaterialShapes.Arch
        FAN           -> MaterialShapes.Fan
        ARROW         -> MaterialShapes.Arrow
        SLANTED       -> MaterialShapes.Slanted
        OVAL          -> MaterialShapes.Oval
        PILL          -> MaterialShapes.Pill
        TRIANGLE      -> MaterialShapes.Triangle
        DIAMOND       -> MaterialShapes.Diamond
        CLAM_SHELL    -> MaterialShapes.ClamShell
        PENTAGON      -> MaterialShapes.Pentagon
        GEM           -> MaterialShapes.Gem
        SUNNY         -> MaterialShapes.Sunny
        VERY_SUNNY    -> MaterialShapes.VerySunny
        COOKIE_4      -> MaterialShapes.Cookie4Sided
        COOKIE_6      -> MaterialShapes.Cookie6Sided
        COOKIE_7      -> MaterialShapes.Cookie7Sided
        COOKIE_9      -> MaterialShapes.Cookie9Sided
        COOKIE_12     -> MaterialShapes.Cookie12Sided
        BURST         -> MaterialShapes.Burst
        SOFT_BURST    -> MaterialShapes.SoftBurst
        BOOM          -> MaterialShapes.Boom
        SOFT_BOOM     -> MaterialShapes.SoftBoom
        FLOWER        -> MaterialShapes.Flower
        PUFFY         -> MaterialShapes.Puffy
        PUFFY_DIAMOND -> MaterialShapes.PuffyDiamond
        CIRCLE        -> MaterialShapes.Circle
        HEART         -> MaterialShapes.Heart
        GHOSTISH      -> MaterialShapes.Ghostish
        CLOVER_4      -> MaterialShapes.Clover4Leaf
        CLOVER_8      -> MaterialShapes.Clover8Leaf
    }

    companion object {
        fun random(): LeadingShapeType = entries.random()
    }
}

data class Task(
    val id          : Int,
    val title       : String,
    val description : String,
    val leadingShape: LeadingShapeType,
)

// ── static — top bar only ─────────────────────────────────────────────────────

@Composable
fun TaskScreenStatic(
    searchActive: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
    onSearchOpen: () -> Unit,
    searchFocusRequester: FocusRequester? = null,
    firstItemFocusRequester: FocusRequester? = null,
) {
    AnimatedContent(
        targetState = searchActive,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "topBarSwitch",
        modifier = Modifier.fillMaxWidth(),
    ) { isSearching ->
        if (isSearching) {
            SearchBar(
                hint = stringResource(Res.string.search_hint),
                query = query,
                onQueryChange = onQueryChange,
                onClear = onClear,
                onBack = onBack,
            )
        } else {
            val trailingMod = if (searchFocusRequester != null) {
                Modifier
                    .focusRequester(searchFocusRequester)
                    .focusProperties {
                        if (firstItemFocusRequester != null) {
                            down = firstItemFocusRequester
                        }
                    }
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown) {
                            if (firstItemFocusRequester != null) {
                                firstItemFocusRequester.requestFocus()
                                true
                            } else false
                        } else false
                    }
            } else Modifier

            ToolBar(
                title = stringResource(Res.string.screen_tasks),
                onTrailingClick = onSearchOpen,
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = stringResource(Res.string.cd_search),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingModifier = trailingMod,
            )
        }
    }
}

// ── dynamic — list only ───────────────────────────────────────────────────────

@Composable
fun TaskScreenBody(
    visibleTasks: List<Task>,
    onDismiss   : (Task) -> Unit,
    onItemClick : (Task) -> Unit,
    onNewClick  : () -> Unit,
    searchFocusRequester: FocusRequester? = null,
    firstItemFocusRequester: FocusRequester? = null,
    lastItemFocusRequester: FocusRequester? = null,
    newButtonFocusRequester: FocusRequester? = null,
    modifier    : Modifier = Modifier,
) {
    var hasFocus by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .padding(top = 64.dp)
            .fillMaxSize()
            .onFocusChanged { hasFocus = it.hasFocus }
            .onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    if (!hasFocus && keyEvent.key == Key.DirectionDown) {
                        if (visibleTasks.isNotEmpty() && firstItemFocusRequester != null) {
                            firstItemFocusRequester.requestFocus()
                            true
                        } else if (newButtonFocusRequester != null) {
                            newButtonFocusRequester.requestFocus()
                            true
                        } else false
                    } else false
                } else false
            }
    ) {
        SegmentedSwipeableList(
            tasks = visibleTasks,
            onDismiss = onDismiss,
            onItemClick = onItemClick,
            searchFocusRequester = searchFocusRequester,
            firstItemFocusRequester = firstItemFocusRequester,
            lastItemFocusRequester = lastItemFocusRequester,
            newButtonFocusRequester = newButtonFocusRequester,
            modifier = modifier,
        )
        FabBarLayout(
            text = stringResource(Res.string.fab_new),
            onClick = onNewClick,
            focusRequester = newButtonFocusRequester,
            buttonModifier = Modifier.focusProperties {
                if (visibleTasks.isNotEmpty() && lastItemFocusRequester != null) {
                    up = lastItemFocusRequester
                } else if (searchFocusRequester != null) {
                    up = searchFocusRequester
                }
            }.onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp) {
                    if (visibleTasks.isNotEmpty() && lastItemFocusRequester != null) {
                        lastItemFocusRequester.requestFocus()
                        true
                    } else if (searchFocusRequester != null) {
                        searchFocusRequester.requestFocus()
                        true
                    } else false
                } else false
            },
            modifier = modifier,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(Res.string.cd_edit),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .size(26.dp)
                )
            }
        )
    }
}

// ── swipeable item ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SwipeableListItem(
    item        : T,
    onDismiss   : (T) -> Unit,
    modifier    : Modifier = Modifier,
    itemModifier: Modifier = Modifier,
    onItemClick : (T) -> Unit = {},
    shape       : Shape = MaterialTheme.shapes.medium,
    content     : @Composable (T) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState(
        initialValue        = SwipeToDismissBoxValue.Settled,
        positionalThreshold = { it * 0.5f },
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
            delay(200L)
            onDismiss(item)
        }
    }

    SwipeToDismissBox(
        state                       = dismissState,
        modifier                    = modifier.fillMaxWidth(),
        enableDismissFromStartToEnd = false,
        backgroundContent           = {
            val isDismissing = dismissState.targetValue == SwipeToDismissBoxValue.EndToStart

            val iconScale by animateFloatAsState(
                targetValue   = if (!isDismissing) 1f else 0.6f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness    = Spring.StiffnessMedium,
                ),
                label = "iconScale",
            )
            val iconAlpha by animateFloatAsState(
                targetValue   = if (!isDismissing) 1f else 0f,
                animationSpec = tween(150),
                label         = "iconAlpha",
            )

            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(84.dp)
            ) {
                val W = maxWidth.value
                Row(
                    modifier              = Modifier
                        .fillMaxWidth()
                        .aspectRatio(W / 77f),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End,
                ) {
                    Box(
                        modifier         = Modifier
                            .fillMaxHeight()
                            .aspectRatio(48f / 77f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier         = Modifier
                                .fillMaxHeight()
                                .aspectRatio(48f / 24f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector        = Icons.Rounded.Delete,
                                contentDescription = stringResource(Res.string.cd_delete),
                                tint               = MaterialTheme.colorScheme.onErrorContainer,
                                modifier           = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(24f / 24f)
                                    .scale(iconScale)
                                    .alpha(iconAlpha),
                            )
                        }
                    }
                }
            }
        },
    ) {
        var isDpadFocused by remember { mutableStateOf(false) }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isDpadFocused = it.isFocused }
                .then(
                    if (isDpadFocused)
                        Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                    else Modifier
                )
                .then(itemModifier),
            shape    = shape,
            onClick  = { onItemClick(item) },
            color    = MaterialTheme.colorScheme.surface,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(78.dp)
                ) {
                    val W = maxWidth.value

                    Row(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .aspectRatio(W / 77f),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val task = item as Task

                        // ── Leading ───────────────────────────────────────
                        BoxWithConstraints(
                            modifier         = Modifier
                                .fillMaxHeight()
                                .aspectRatio(48f / 77f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier         = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(48f / 28f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier         = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(20f / 28f),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .clip(task.leadingShape.polygon().toShape())
                                            .background(MaterialTheme.colorScheme.primary)
                                            .aspectRatio(1f)
                                    )
                                }
                            }
                        }

                        // ── Content ───────────────────────────────────────
                        Box(modifier = Modifier.weight(1f)) {
                            content(item)
                        }

                        // ── Trailing ──────────────────────────────────────
                        BoxWithConstraints(
                            modifier         = Modifier
                                .fillMaxHeight()
                                .aspectRatio(48f / 77f),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier         = Modifier
                                    .fillMaxHeight()
                                    .aspectRatio(48f / 28f),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector        = Icons.AutoMirrored.Rounded.ArrowRight,
                                    contentDescription = stringResource(Res.string.cd_open),
                                    tint               = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier           = Modifier
                                        .fillMaxHeight()
                                        .aspectRatio(28f / 28f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── list ──────────────────────────────────────────────────────────────────────

@Composable
fun SegmentedSwipeableList(
    tasks      : List<Task>,
    onDismiss  : (Task) -> Unit,
    onItemClick: (Task) -> Unit = {},
    searchFocusRequester: FocusRequester? = null,
    firstItemFocusRequester: FocusRequester? = null,
    lastItemFocusRequester: FocusRequester? = null,
    newButtonFocusRequester: FocusRequester? = null,
    modifier   : Modifier = Modifier,
) {
    LazyColumn(
        modifier            = modifier.fillMaxSize(),
        contentPadding      = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            items = tasks,
            key   = { _, item -> item.id },
        ) { index, task ->

            val itemShape = if (tasks.size == 1) {
                RoundedCornerShape(28.dp)
            } else if (index == 0) {
                RoundedCornerShape(
                    topStart    = 28.dp,
                    topEnd      = 28.dp,
                    bottomStart = 8.dp,
                    bottomEnd   = 8.dp,
                )
            } else if (index == tasks.lastIndex) {
                RoundedCornerShape(
                    topStart    = 8.dp,
                    topEnd      = 8.dp,
                    bottomStart = 28.dp,
                    bottomEnd   = 28.dp,
                )
            } else {
                RoundedCornerShape(8.dp)
            }

            val isFirst = index == 0
            val isLast = index == tasks.lastIndex

            var itemModifier = Modifier.focusProperties {
                if (isFirst && searchFocusRequester != null) {
                    up = searchFocusRequester
                }
                if (isLast && newButtonFocusRequester != null) {
                    down = newButtonFocusRequester
                }
            }.onKeyEvent { keyEvent ->
                if (keyEvent.type == KeyEventType.KeyDown) {
                    when (keyEvent.key) {
                        Key.DirectionUp -> {
                            if (isFirst && searchFocusRequester != null) {
                                searchFocusRequester.requestFocus()
                                true
                            } else false
                        }
                        Key.DirectionDown -> {
                            if (isLast && newButtonFocusRequester != null) {
                                newButtonFocusRequester.requestFocus()
                                true
                            } else false
                        }
                        else -> false
                    }
                } else false
            }

            if (isFirst && firstItemFocusRequester != null) {
                itemModifier = itemModifier.focusRequester(firstItemFocusRequester)
            }
            if (isLast && lastItemFocusRequester != null) {
                itemModifier = itemModifier.focusRequester(lastItemFocusRequester)
            }

            SwipeableListItem(
                item        = task,
                onDismiss   = { onDismiss(task) },
                onItemClick = { onItemClick(task) },
                shape       = itemShape,
                itemModifier = itemModifier,
                modifier    = Modifier.animateItem(
                    fadeInSpec     = tween(250),
                    fadeOutSpec    = tween(200),
                    placementSpec  = spring(stiffness = Spring.StiffnessLow),
                ),
            ) { item ->
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text      = item.title,
                        style     = MaterialTheme.typography.titleMedium,
                        maxLines  = 1,
                        softWrap  = false,
                        overflow  = TextOverflow.Ellipsis,
                    )
                    Text(
                        text      = item.description,
                        style     = MaterialTheme.typography.bodyMedium,
                        maxLines  = 1,
                        softWrap  = false,
                        overflow  = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

// ── previews ──────────────────────────────────────────────────────────────────

private val previewTasks = listOf(
    Task(1, "Buy groceries",  "Milk, Eggs, Bread, Coffee",              LeadingShapeType.HEART),
    Task(2, "KMP Project",    "Sync repository and update dependencies", LeadingShapeType.COOKIE_6),
    Task(3, "Gym session",    "Leg day workout at 6 PM",                LeadingShapeType.SUNNY),
    Task(4, "Read book",      "Read 10 pages of Atomic Habits",         LeadingShapeType.DIAMOND),
)

@Composable
private fun TaskScreenPreview(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TaskScreenStatic(
                searchActive = false,
                query = "",
                onQueryChange = {},
                onClear = {},
                onBack = {},
                onSearchOpen = {},
            )
            TaskScreenBody(
                visibleTasks = previewTasks,
                onDismiss = {},
                onItemClick = {},
                onNewClick = {},
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Preview(name = "TaskScreen – 412dp", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFull() = TaskScreenPreview(darkTheme = false)

@Preview(name = "TaskScreen – 320dp (compact)", showBackground = true, widthDp = 320)
@Composable
private fun PreviewCompact() = TaskScreenPreview(darkTheme = true)
