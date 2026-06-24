package com.oprojectview

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
) {
    val countdownDurationMs = 10000L
    val progress = remember(showCountdown, selectedIndex) { Animatable(if (showCountdown) 1f else 0f) }

    LaunchedEffect(showCountdown, selectedIndex) {
        if (!showCountdown) return@LaunchedEffect
        // Reset progress on new selection
        progress.snapTo(1f)
        progress.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = countdownDurationMs.toInt(), easing = LinearEasing)
        )
        if (progress.value == 0f) {
            onCountdownFinished()
        }
    }

    val baseColor = fillColor ?: MaterialTheme.colorScheme.surface
    val surfaceColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.5f)
        .compositeOver(baseColor)
    
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(Res.string.label_up_next).uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val hasMoreThan4 = tasks.size >= 4
        Box(modifier = Modifier.fillMaxWidth().heightIn(max = 336.dp)) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = 0.99f }
                    .drawWithContent {
                        drawContent()
                        if (hasMoreThan4) {
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
                    
                    UpNextListItem(
                        task = task,
                        shape = itemShape,
                        surfaceColor = surfaceColor,
                        isSelected = isSelected,
                        showProgress = isSelected && showCountdown,
                        progressProvider = { progress.value },
                        onClick = { onItemSelected(index) }
                    )
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
    surfaceColor: Color,
    isSelected: Boolean,
    showProgress: Boolean,
    progressProvider: () -> Float,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        onClick = onClick,
        color = surfaceColor,
    ) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(78.dp)
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
                        color = MaterialTheme.colorScheme.onSurface,
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
