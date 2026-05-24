package com.example.kotlinmultiplatform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme

@Composable
fun FabBarLayout(
    modifier: Modifier = Modifier,
    text: String = "New",
    onNewClick: () -> Unit = {},
) {
    fun percentToBias(percent: Float): Float = (percent * 2f) - 1f

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(115.dp)
                .align(BiasAlignment(0f, percentToBias(1f)))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(216f / 112f)
                    .align(Alignment.CenterEnd)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(216f / 80f)
                        .align(Alignment.CenterStart)
                ) {
                    val fontSize = fontSize(24.dp)
                    ExtendedFloatingActionButton(
                        onClick = onNewClick,
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(end = 40.dp)
                            .align(Alignment.CenterEnd),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(25),
                        icon = {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .size(26.dp)
                            )
                        },
                        text = {
                            Text(
                                text = text,
                                fontSize = fontSize,
                                lineHeight = fontSize,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "FabBarLayout – 412dp (design width)",
    showBackground = true,
    widthDp = 412,
)
@Composable
private fun PreviewFull() {
    AppTheme(darkTheme = false) {
        FabBarLayout(modifier = Modifier.fillMaxWidth())
    }
}

@Preview(
    name = "FabBarLayout – 320dp (compact)",
    showBackground = true,
    widthDp = 320,
)
@Composable
private fun PreviewCompact() {
    AppTheme(darkTheme = false) {
        FabBarLayout(modifier = Modifier.fillMaxWidth())
    }
}
