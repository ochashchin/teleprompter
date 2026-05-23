package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme


@Composable
fun ToolBar(
    title: String,
    onTrailingClick: () -> Unit = {},
    onLeadingClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    fun percentToBias(percent: Float): Float = (percent * 2f) - 1f
    Box(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(64.dp)
                .align(BiasAlignment(0f, percentToBias(0f)))
        ) {
            val W = maxWidth.value
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(W / 64f)
                    .align(Alignment.TopCenter)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(W / 28f)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    val fontSize =
                        with(LocalDensity.current) { maxHeight.toSp() * 0.82f }
                    Text(
                        text = title,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = fontSize,
                        lineHeight = fontSize,
                        textAlign = TextAlign.Start
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(W / 48f)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.CenterEnd
                ) {

                    Row(
                        modifier = Modifier
                            .fillMaxHeight()
                    ) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(16f / 48f)
                                .fillMaxHeight()
                        )

                        if (leadingIcon != null) {
                            IconButton(
                                modifier = Modifier
                                    .aspectRatio(1f / 1f)
                                    .fillMaxHeight(),
                                onClick = onLeadingClick ?: {},
                            ) {
                                leadingIcon()
                            }
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        )

                        if (trailingIcon != null) {
                            IconButton(
                                modifier = Modifier
                                    .aspectRatio(1f / 1f)
                                    .fillMaxHeight(),
                                onClick = onTrailingClick ?: {},
                            ) {
                                trailingIcon()
                            }
                        }

                        Box(
                            modifier = Modifier
                                .aspectRatio(16f / 48f)
                                .fillMaxHeight()
                        )
                    }
                }
            }
        }
    }
}

@Preview(
    name = "ToolBar – 412dp (design width)",
    showBackground = true,
    widthDp = 412,
)
@Composable
private fun PreviewFull() {
    AppTheme(darkTheme = false) {
        ToolBar("Toolbar", { })
    }
}

@Preview(
    name = "ToolBar – 320dp (compact)",
    showBackground = true,
    widthDp = 320,
)
@Composable
private fun PreviewCompact() {
    AppTheme(darkTheme = true) {
        ToolBar("Toolbar", { })
    }
}