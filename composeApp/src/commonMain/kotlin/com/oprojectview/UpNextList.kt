package com.oprojectview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import com.oprojectview.features.player.PlayerTask
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.cd_open
import kotlinmultiplatform.composeapp.generated.resources.label_up_next
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource

@Composable
fun UpNextList(
    tasks: List<PlayerTask>,
    selectedIndex: Int,
    onItemSelected: (Int) -> Unit,
    onCountdownFinished: () -> Unit,
    showCountdown: Boolean,
    modifier: Modifier = Modifier,
    fillColor: Color? = null,
    onFocus: () -> Unit = {},
    firstItemFocusRequester: FocusRequester? = null,
    lastItemFocusRequester: FocusRequester? = null,
    backButtonFocusRequester: FocusRequester? = null,
    closeButtonFocusRequester: FocusRequester? = null,
    playBarFocusRequester: FocusRequester? = null,
    isHorizontal: Boolean = false,
) {
    val countdownDurationMs = 5000L
    val progress = remember(showCountdown, selectedIndex) { Animatable(if (showCountdown) 1f else 0f) }

    LaunchedEffect(showCountdown, selectedIndex) {
        if (!showCountdown) return@LaunchedEffect
        // Reset progress on new selection
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = countdownDurationMs.toInt(), easing = LinearEasing)
        )
        // Note: Actual transition is handled synchronously by PlayerViewModel's background coroutine
        // to ensure it still functions perfectly while the app is backgrounded in PiP mode.
    }

    val itemSurfaceColor = if (fillColor != null) {
        MaterialTheme.colorScheme.surface.copy(alpha = 0.75f).compositeOver(fillColor)
    } else {
        MaterialTheme.colorScheme.surface
    }

    val hasMoreThan3 = tasks.size >= 3

    Box(
        modifier = modifier.aspectRatio(1f),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = stringResource(Res.string.label_up_next).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Box(modifier = Modifier.fillMaxWidth().heightIn(max = 234.dp)) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = 0.99f }
                        .drawWithContent {
                            drawContent()
                            if (hasMoreThan3) {
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(Color.Transparent, Color.Black),
                                        startY = size.height - 82.dp.toPx(),
                                        endY = size.height
                                    ),
                                    blendMode = BlendMode.DstOut
                                )
                            }
                        },
                    contentPadding = PaddingValues(start = 16.dp, top = 0.dp, end = 16.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(
                        items = tasks,
                        key = { _, item -> item.id },
                    ) { index, task ->
                        val itemShape = if (tasks.size == 1) {
                            RoundedCornerShape(28.dp)
                        } else if (index == 0) {
                            RoundedCornerShape(
                                topStart = 28.dp, topEnd = 28.dp,
                                bottomStart = 8.dp, bottomEnd = 8.dp,
                            )
                        } else if (index == tasks.lastIndex) {
                            RoundedCornerShape(
                                topStart = 8.dp, topEnd = 8.dp,
                                bottomStart = 28.dp, bottomEnd = 28.dp,
                            )
                        } else {
                            RoundedCornerShape(8.dp)
                        }

                        val isSelected = index == selectedIndex
                        val isFirst = index == 0
                        val isLast = index == tasks.lastIndex

                        // Mirror TaskScreen.SegmentedSwipeableList exactly:
                        // focusProperties tells the traversal engine where to go at boundaries,
                        // onKeyEvent + requestFocus() is the belt-and-suspenders fallback.
                        // Both are applied to this Box which is the ACTUAL focus target node,
                        // wrapping Surface so our modifiers sit on the focusable layer.
                        var itemModifier: Modifier = Modifier
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused && index != 0) {
                                    onFocus()
                                }
                            }
                            .focusProperties {
                                if (isFirst) {
                                    if (backButtonFocusRequester != null) {
                                        if (isHorizontal) right = backButtonFocusRequester
                                        else up = backButtonFocusRequester
                                    }
                                }
                                if (isLast) {
                                    if (playBarFocusRequester != null) {
                                        if (isHorizontal) left = playBarFocusRequester
                                        else down = playBarFocusRequester
                                    }
                                }
                            }
                            .onKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown) {
                                    when (keyEvent.key) {
                                        Key.DirectionUp -> {
                                            if (isFirst && !isHorizontal && backButtonFocusRequester != null) {
                                                onFocus()
                                                backButtonFocusRequester.requestFocus()
                                                true
                                            } else {
                                                onFocus()
                                                false
                                            }
                                        }
                                        Key.DirectionRight -> {
                                            if (isFirst && isHorizontal && backButtonFocusRequester != null) {
                                                onFocus()
                                                backButtonFocusRequester.requestFocus()
                                                true
                                            } else {
                                                onFocus()
                                                false
                                            }
                                        }
                                        Key.DirectionDown -> {
                                            if (isLast && !isHorizontal && playBarFocusRequester != null) {
                                                onFocus()
                                                playBarFocusRequester.requestFocus()
                                                true
                                            } else {
                                                onFocus()
                                                false
                                            }
                                        }
                                        Key.DirectionLeft -> {
                                            if (isLast && isHorizontal && playBarFocusRequester != null) {
                                                onFocus()
                                                playBarFocusRequester.requestFocus()
                                                true
                                            } else {
                                                onFocus()
                                                false
                                            }
                                        }
                                        else -> false
                                    }
                                } else false
                            }

                        if (isFirst && firstItemFocusRequester != null) {
                            itemModifier = itemModifier.focusRequester(firstItemFocusRequester)
                        }
                        if (isLast && lastItemFocusRequester != null && !isFirst) {
                            itemModifier = itemModifier.focusRequester(lastItemFocusRequester)
                        }

                        UpNextListItem(
                            task = task,
                            shape = itemShape,
                            isSelected = isSelected,
                            showProgress = isSelected && showCountdown,
                            progressProvider = { progress.value },
                            modifier = itemModifier,
                            onClick = {
                                onFocus()
                                onItemSelected(index)
                            }
                        )
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun UpNextListItem(
    task: PlayerTask,
    shape: androidx.compose.ui.graphics.Shape,
    isSelected: Boolean,
    showProgress: Boolean,
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
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
        shape = shape,
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(78.dp)
        ) {
            val W = maxWidth.value
            Row(
                modifier = Modifier.fillMaxWidth().aspectRatio(W / 77f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Leading
                BoxWithConstraints(
                    modifier = Modifier.fillMaxHeight().aspectRatio(48f / 77f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight().aspectRatio(48f / 28f),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = Modifier.fillMaxHeight().aspectRatio(20f / 28f),
                            contentAlignment = Alignment.Center,
                        ) {
                            val shapeType = LeadingShapeType.entries.getOrNull(task.shapeOrdinal) ?: LeadingShapeType.entries.first()
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .clip(shapeType.polygon().toShape())
                                    .background(MaterialTheme.colorScheme.primary)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }

                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = task.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Trailing
                BoxWithConstraints(
                    modifier = Modifier.fillMaxHeight().aspectRatio(48f / 77f),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight().aspectRatio(48f / 28f),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (showProgress) {
                            androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(
                                    progress = progressProvider,
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                    strokeWidth = 4.dp,
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowRight,
                                contentDescription = stringResource(Res.string.cd_open),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxHeight().aspectRatio(28f / 28f)
                            )
                        }
                    }
                }
            }
        }
    }
}
