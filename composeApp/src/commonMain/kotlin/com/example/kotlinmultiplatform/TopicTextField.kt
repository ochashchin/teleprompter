package com.example.kotlinmultiplatform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldLineLimits
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.example.kotlinmultiplatform.ui.theme.AppTheme

@Composable
fun TopicTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    isError: Boolean = false,
) {
    Box(modifier = modifier.fillMaxWidth()) {

        val fontSize = fontSize(28.dp)

        Row(
            modifier = Modifier
                .height(64.dp)
                .align(Alignment.Center),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(16.dp)
                    .fillMaxHeight()
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
            ) {

                val fieldShape = RoundedCornerShape(6.dp)

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .padding(top = 8.dp)
                        .clip(fieldShape)
                        .background(MaterialTheme.colorScheme.surface)
                )
                OutlinedTextField(
                    state = state,
                    labelPosition = TextFieldLabelPosition.Attached(alwaysMinimize = true),
                    label = { Text("Topic") },
                    shape = fieldShape,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (focusRequester != null)
                                Modifier.focusRequester(focusRequester)
                            else Modifier
                        ),
                    textStyle = TextStyle(
                        fontSize = fontSize,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    placeholder = {
                        Text(
                            text = "Enter topic",
                            fontSize = fontSize
                        )
                    },
                    isError = isError,

                    trailingIcon = if (isError) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Error,
                                contentDescription = "Topic is required",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else null,

                    lineLimits = TextFieldLineLimits.SingleLine,
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
                    .width(16.dp)
                    .fillMaxHeight()
            )
        }
    }

}

@Composable
fun fontSize(dpSize: Dp): TextUnit {
    return with(LocalDensity.current) { (dpSize.toSp() * 0.82f) }
}

@Composable
private fun NewTaskScreenPreview(darkTheme: Boolean) {
    AppTheme(darkTheme = darkTheme) {
        Column(modifier = Modifier.fillMaxWidth()) {
            TopicTextField(state = rememberTextFieldState())
        }
    }
}

@Preview(name = "TaskScreen – 412dp", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFull() = NewTaskScreenPreview(darkTheme = false)

@Preview(name = "TaskScreen – 412dp", showBackground = true, widthDp = 412)
@Composable
private fun PreviewFullN() = NewTaskScreenPreview(darkTheme = true)