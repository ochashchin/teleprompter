package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
    modifier: Modifier = Modifier,
    title: String,
    onTrailingClick: () -> Unit = {},
    onLeadingClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
    ) {
        val titleFontSize = fontSize(24.dp)

        Text(
            modifier = Modifier.align(Alignment.Center),
            text = title,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            fontSize = titleFontSize,
            lineHeight = titleFontSize,
            textAlign = TextAlign.Center
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            contentAlignment = Alignment.Center
        ) {

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                if (leadingIcon != null) {
                    IconButton(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxHeight(),
                        onClick = onLeadingClick ?: {}
                    ) {
                        leadingIcon()
                    }
                } else {
                    Spacer(
                        Modifier
                            .aspectRatio(1f)
                            .fillMaxHeight()
                    )
                }

                Spacer(Modifier.weight(1f))

                if (trailingIcon != null) {
                    IconButton(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .fillMaxHeight(),
                        onClick = onTrailingClick
                    ) {
                        trailingIcon()
                    }
                } else {
                    Spacer(
                        Modifier
                            .aspectRatio(1f)
                            .fillMaxHeight()
                    )
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
            Column(modifier = Modifier.fillMaxWidth()) {
                ToolBar(title = "Toolbar")
            }
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
            Column(modifier = Modifier.fillMaxWidth()) {
                ToolBar(title = "Toolbar")
            }
        }
    }