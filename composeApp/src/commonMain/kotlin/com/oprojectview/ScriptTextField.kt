package com.oprojectview

import com.oprojectview.core.verticalScrollbar
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.OutputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.oprojectview.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.cd_script_required
import kotlinmultiplatform.composeapp.generated.resources.script_label
import kotlinmultiplatform.composeapp.generated.resources.script_placeholder
import org.jetbrains.compose.resources.stringResource

// ── OutputTransformation ──────────────────────────────────────────────────────

/**
 * Renders [StyleSpan]s as [SpanStyle]s on top of the raw text content,
 * turning the plain [TextFieldState] text into visually styled output
 * without altering the underlying string.
 */
private class SpanOutputTransformation(
    private val spans: List<StyleSpan>,
    private val textPalette: List<Color?>
) : OutputTransformation {
    override fun TextFieldBuffer.transformOutput() {
        if (spans.isEmpty()) return
        val len = length
        for (span in spans) {
            val s = span.start.coerceIn(0, len)
            val e = span.end.coerceIn(s, len)
            if (s >= e) continue
            addStyle(span.toSpanStyle(textPalette), s, e)
        }
    }
}

// ── ScriptTextField ───────────────────────────────────────────────────────────

@Composable
fun ScriptTextField(
    state:      TextFieldState,
    modifier:   Modifier             = Modifier,
    styleState: ScriptTextStyleState? = null,
    isError:    Boolean              = false,
) {

    val fontSize = fontSize(23.dp)
    val fontHeight = fontSize(30.dp)

    // Read spans as a local val so the Compose snapshot system registers this
    // composable as an observer of _spans. Any call to applyStyle/persist in
    // ScriptTextStyleState will flip the mutableStateOf, triggering recomposition
    // here and replacing SpanOutputTransformation with a fresh instance.
    val spans = styleState?.spans ?: emptyList()

    // Background: animate fill colour toggle with a 200ms crossfade
    val fillColor = styleState?.resolveActiveFillColor()
    val targetContainerColor = fillColor ?: MaterialTheme.colorScheme.surface
    val containerColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = tween(durationMillis = 200),
        label = "containerColor",
    )

    LaunchedEffect(state, styleState) {
        styleState?.initText(state.text.toString())
        snapshotFlow { state.text.toString() }
            .collect { newText ->
                styleState?.onTextChanged(newText)
            }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment     = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight()
            )

            val fieldShape      = RoundedCornerShape(6.dp)
            val textScrollState = rememberScrollState()

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                // Background shape — switches to fill colour when active
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(top = 8.dp)
                        .clip(fieldShape)
                        .background(containerColor),
                )

                OutlinedTextField(
                    state = state,

                    // Apply rich-text span rendering via OutputTransformation.
                    // Uses the locally-observed `spans` val so recomposition fires
                    // whenever the span list changes and the transformation stays current.
                    outputTransformation = if (styleState != null && spans.isNotEmpty())
                        SpanOutputTransformation(spans, getScriptTextColors())
                    else null,

                    label = { Text(stringResource(Res.string.script_label)) },

                    labelPosition = TextFieldLabelPosition.Attached(
                        alwaysMinimize = true
                    ),

                    shape = fieldShape,

                    isError = isError,

                    trailingIcon = if (isError) {
                        {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .padding(top = 20.dp),
                                contentAlignment = Alignment.TopCenter,
                            ) {
                                Icon(
                                    imageVector        = Icons.Rounded.Error,
                                    contentDescription = stringResource(Res.string.cd_script_required),
                                    tint               = MaterialTheme.colorScheme.error,
                                    modifier           = Modifier.size(20.dp),
                                )
                            }
                        }
                    } else null,

                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScrollbar(textScrollState),

                    textStyle = TextStyle(
                        fontSize   = fontSize,
                        lineHeight = fontHeight,
                    ),

                    placeholder = {
                        Text(
                            text     = stringResource(Res.string.script_placeholder),
                            fontSize = fontSize,
                        )
                    },

                    scrollState = textScrollState,

                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor  = Color.Transparent,
                        focusedContainerColor   = containerColor,
                        unfocusedContainerColor = containerColor,
                        errorBorderColor        = MaterialTheme.colorScheme.error,
                        errorContainerColor     = containerColor,
                    ),
                )
            }

            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight()
            )
        }
    }
}

// ── Previews ──────────────────────────────────────────────────────────────────

@Composable
private fun NewTaskScreenPreview(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScriptTextField(state = rememberTextFieldState())
        }
    }
}

@Preview(name = "TaskScreen – 412dp", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFull() = NewTaskScreenPreview(darkTheme = false)
