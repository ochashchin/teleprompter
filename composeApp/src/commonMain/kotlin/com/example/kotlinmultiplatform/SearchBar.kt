package com.example.kotlinmultiplatform

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme

@Composable
fun SearchBar(
    hint: String,
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    fun percentToBias(percent: Float): Float = (percent * 2f) - 1f
    Box(modifier = Modifier.fillMaxWidth()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(BiasAlignment(0f, percentToBias(0f)))
        ) {
            val W = maxWidth.value
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(W / 64f)
                    .align(Alignment.TopCenter)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(W / 48f)
                        .align(Alignment.Center)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .aspectRatio(16f / 48f)
                                .fillMaxHeight()
                        )

                        IconButton(
                            modifier = Modifier
                                .aspectRatio(1f / 1f)
                                .fillMaxHeight(),
                            onClick = onBack,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(maxWidth.value / 24f)
                                    .align(Alignment.Center),
                                contentAlignment = Alignment.Center,
                            ) {
                                val fontSize =
                                    with(LocalDensity.current) { maxHeight.toSp() * 0.82f }

                                BasicTextField(
                                    value = query,
                                    onValueChange = onQueryChange,
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = fontSize,
                                        lineHeight = fontSize,
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.onSurface),
                                    keyboardOptions = KeyboardOptions(
                                        imeAction = ImeAction.Search,
                                    ),
                                    keyboardActions = KeyboardActions(
                                        onSearch = { keyboardController?.hide() }
                                    ),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .focusRequester(focusRequester),
                                    decorationBox = { innerTextField ->
                                        Box(contentAlignment = Alignment.CenterStart) {
                                            if (query.isEmpty()) {
                                                Text(
                                                    text = hint,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    fontSize = fontSize,
                                                    maxLines = 1,
                                                )
                                            }
                                            innerTextField()
                                        }
                                    }
                                )
                            }
                        }

                        AnimatedVisibility(
                            visible = query.isNotEmpty(),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            IconButton(
                                modifier = Modifier
                                    .aspectRatio(1f / 1f)
                                    .fillMaxHeight(),
                                onClick = onClear,
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
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
    name = "SearchBar – 412dp (design width)",
    showBackground = true,
    widthDp = 412,
)
@Composable
private fun PreviewFull() {
    AppTheme(darkTheme = false) {
        SearchBar(
            hint = "Search tasks…",
            query = "",
            onQueryChange = { },
            onClear = { },
            onBack = { }
        )
    }
}

@Preview(
    name = "SearchBar – 320dp (compact)",
    showBackground = true,
    widthDp = 320,
)
@Composable
private fun PreviewCompact() {
    AppTheme(darkTheme = true) {
        SearchBar(
            hint = "Search tasks…",
            query = "",
            onQueryChange = { },
            onClear = { },
            onBack = { }
        )
    }
}