package com.oprojectview

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.oprojectview.theme.AppTheme
import kotlinmultiplatform.composeapp.generated.resources.Res
import kotlinmultiplatform.composeapp.generated.resources.cd_topic_required
import kotlinmultiplatform.composeapp.generated.resources.topic_label
import kotlinmultiplatform.composeapp.generated.resources.topic_placeholder
import org.jetbrains.compose.resources.stringResource

@Composable
fun TopicTextField(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    isError: Boolean = false,
) {
    Box(modifier = modifier.fillMaxWidth()) {

        val fontSize = fontSize(23.dp)

        Row(
            modifier = Modifier
                .height(68.dp)
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
                    .background(MaterialTheme.colorScheme.surfaceContainer)
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
                    label = { Text(stringResource(Res.string.topic_label)) },
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
                            text = stringResource(Res.string.topic_placeholder),
                            fontSize = fontSize
                        )
                    },
                    isError = isError,

                    trailingIcon = if (isError) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Error,
                                contentDescription = stringResource(Res.string.cd_topic_required),
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    } else null,

                    lineLimits = TextFieldLineLimits.SingleLine,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
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