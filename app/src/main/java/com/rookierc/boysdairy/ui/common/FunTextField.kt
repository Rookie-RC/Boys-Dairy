package com.rookierc.boysdairy.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.ui.theme.FunShapes
import com.rookierc.boysdairy.ui.theme.FunTheme

/**
 * Text input drawn inside a [FunSurface] (outline + hard shadow); the outline turns pink while
 * focused and red when [isError].
 */
@Composable
fun FunTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    leadingEmoji: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    isError: Boolean = false,
) {
    val colors = FunTheme.colors
    var focused by remember { mutableStateOf(false) }

    FunSurface(
        modifier = modifier,
        shape = FunShapes.field,
        containerColor = colors.surface,
        borderColor = when {
            isError -> colors.danger
            focused -> colors.pink
            else -> colors.ink
        },
        shadowOffset = 3.dp,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focused = it.isFocused },
            placeholder = placeholder?.let { text -> { Text(text) } },
            leadingIcon = leadingEmoji?.let { emoji -> { EmojiText(text = emoji, fontSize = 20.sp) } },
            trailingIcon = trailing,
            isError = isError,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = FunShapes.field,
            colors = TextFieldDefaults.colors(
                focusedTextColor = colors.textPrimary,
                unfocusedTextColor = colors.textPrimary,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                errorContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                cursorColor = colors.pink,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                focusedPlaceholderColor = colors.textSecondary,
                unfocusedPlaceholderColor = colors.textSecondary,
            ),
        )
    }
}
