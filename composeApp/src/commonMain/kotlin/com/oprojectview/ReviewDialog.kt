package com.oprojectview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.oprojectview.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.rate_us_later
import kotlinmultiplatform.composeapp.generated.resources.rate_us_rate_now
import kotlinmultiplatform.composeapp.generated.resources.rate_us_supporting_text
import kotlinmultiplatform.composeapp.generated.resources.rate_us_title
import kotlinmultiplatform.composeapp.generated.resources.trending_up
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ReviewDialog(
    onDismissRequest: () -> Unit,
    onRateNow: () -> Unit,
    onLater: () -> Unit,
) {
    fun percentToBias(percent: Float): Float = (percent * 2f) - 1f

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(
            dismissOnClickOutside = false,
        ),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHighest,
            tonalElevation = 6.dp,
            modifier = Modifier
                .width(380.dp)
                .aspectRatio(380f / 320f) // Adjusted aspect ratio to fit the content
        ) {
            val H = 28.dp
            val fontSize = fontSize(H)

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(top = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.trending_up),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = stringResource(Res.string.rate_us_title),
                        fontSize = fontSize(24.dp),
                        lineHeight = fontSize(28.dp),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = stringResource(Res.string.rate_us_supporting_text),
                        fontSize = fontSize(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                Row(
                    modifier = Modifier
                        .height(56.dp)
                        .padding(end = fontSize.value.dp)
                        .align(BiasAlignment(percentToBias(.5f), percentToBias(.9f)))
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(
                        modifier = Modifier.fillMaxHeight(),
                        onClick = {
                            onDismissRequest()
                            onLater()
                        }
                    ) {
                        Text(
                            fontSize = fontSize(18.dp),
                            text = stringResource(Res.string.rate_us_later)
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    RateNowButton(
                        modifier = Modifier.fillMaxHeight(),
                        fontSize = fontSize(18.dp),
                        hovered = true,
                        onRateNow = {
                            onRateNow()
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun RateNowButton(
    modifier: Modifier,
    fontSize: TextUnit,
    hovered: Boolean = true,
    onRateNow: () -> Unit
) {
    TextButton(
        modifier = modifier,
        onClick = onRateNow,
        shape = MaterialTheme.shapes.extraLarge,
        colors = ButtonDefaults.textButtonColors(
            containerColor =
                if (hovered)
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                else
                    Color.Transparent,

            contentColor =
                if (hovered)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.primary
        )
    ) {
        Text(
            fontSize = fontSize,
            text = stringResource(Res.string.rate_us_rate_now),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Previews
// ─────────────────────────────────────────────────────────────────────────────

@Preview(
    name = "ReviewDialog – light",
    showBackground = true,
)
@Composable
private fun PreviewReviewDialog() {
    AppTheme {
        ReviewDialog(
            onDismissRequest = {},
            onRateNow = {},
            onLater = {},
        )
    }
}
