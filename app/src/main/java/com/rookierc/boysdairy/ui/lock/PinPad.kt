package com.rookierc.boysdairy.ui.lock

import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.security.PinHasher

private val keypadRows = listOf(
    listOf('1', '2', '3'),
    listOf('4', '5', '6'),
    listOf('7', '8', '9'),
)

/**
 * Stateless PIN entry layout: title, 4 dots, message line and a 3x4 keypad.
 * Shared by the lock screen and the change-PIN screen.
 */
@Composable
fun PinEntryContent(
    title: String,
    enteredCount: Int,
    messageText: String?,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val keySize = if (maxHeight < 600.dp) 64.dp else 76.dp

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            PinDots(filled = enteredCount)

            Box(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .padding(horizontal = 24.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (messageText != null) {
                    Text(
                        text = messageText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                keypadRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        row.forEach { digit ->
                            DigitKey(digit = digit, size = keySize, enabled = enabled, onDigit = onDigit)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Spacer(Modifier.size(keySize))
                    DigitKey(digit = '0', size = keySize, enabled = enabled, onDigit = onDigit)
                    IconButton(
                        onClick = onBackspace,
                        enabled = enabled,
                        modifier = Modifier.size(keySize),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Backspace,
                            contentDescription = stringResource(R.string.lock_backspace),
                            modifier = Modifier.size(28.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        repeat(PinHasher.PIN_LENGTH) { index ->
            val isFilled = index < filled
            val color = if (isFilled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .border(2.dp, color, CircleShape)
                    .background(if (isFilled) color else Color.Transparent, CircleShape),
            )
        }
    }
}

@Composable
private fun DigitKey(
    digit: Char,
    size: Dp,
    enabled: Boolean,
    onDigit: (Char) -> Unit,
) {
    FilledTonalIconButton(
        onClick = { onDigit(digit) },
        enabled = enabled,
        modifier = Modifier.size(size),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Text(text = digit.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}
