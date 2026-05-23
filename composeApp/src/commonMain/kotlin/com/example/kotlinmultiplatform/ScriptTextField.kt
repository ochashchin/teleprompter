package com.example.kotlinmultiplatform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelPosition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme

@Composable
fun ScriptTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
) {
    var P by remember { mutableStateOf(0f) }
    var H by remember { mutableStateOf(0.dp) }
    var W by remember { mutableStateOf(0f) }

    Box(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            W = maxWidth.value
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(W / 64f)
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.Center,
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(W / 28f)
                        .align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) { H = maxHeight }

                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier
                            .aspectRatio(16f / 64f)
                            .fillMaxHeight()
                    ) { P = maxWidth.value }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight())

                    Box(
                        modifier = Modifier
                            .aspectRatio(16f / 64f)
                            .fillMaxHeight()
                    )
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
        ) {
            var H1 = maxHeight.value







                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(W / H1)
                            .align(Alignment.TopCenter),
                        contentAlignment = Alignment.Center,
                    ) {

                        Row(
                            modifier = Modifier
                                .fillMaxHeight()
                                .align(Alignment.Center),
                        ) {

                            Box(
                                modifier = Modifier
                                    .aspectRatio(P / H1)
                                    .fillMaxHeight()
                            )

                            val fontSize = with(LocalDensity.current) { H.toSp() * 0.82f }
                            val fieldShape = RoundedCornerShape(6.dp)
                            val textScrollState = rememberScrollState()

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                            ) {

                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .padding(top = 8.dp)
                                        .clip(fieldShape)
                                        .background(MaterialTheme.colorScheme.surface)
                                )

                                OutlinedTextField(
                                    state = state,

                                    label = { Text("Script") },

                                    labelPosition = TextFieldLabelPosition.Attached(
                                        alwaysMinimize = true
                                    ),

                                    shape = fieldShape,

                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(H1.dp),

                                    textStyle = TextStyle(
                                        fontSize = fontSize,
                                        lineHeight = fontSize,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    ),

                                    placeholder = {
                                        Text(
                                            text = "Enter or paste your script",
                                            fontSize = fontSize,
                                        )
                                    },

                                    scrollState = textScrollState,

                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                                        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                    ),
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .aspectRatio(P / H1)
                                    .fillMaxHeight()
                            )
                        }
                    }
                }

    }
}

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
