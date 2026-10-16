package com.rookierc.boysdairy.ui.lock

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.security.PinHasher
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.FunSurface
import com.rookierc.boysdairy.ui.theme.FunShapes
import com.rookierc.boysdairy.ui.theme.FunTheme

private val keypadRows = listOf(
    listOf('1', '2', '3'),
    listOf('4', '5', '6'),
    listOf('7', '8', '9'),
)

/**
 * Stateless PIN entry layout: optional hero emoji, title, subtitle, 4 dots, message line and a
 * chunky 3x4 keypad. Shared by the lock screen and the change-PIN screen.
 *
 * Whenever [shakeTrigger] changes to a value > 0 the dots and the message wiggle (wrong PIN).
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
    subtitle: String? = null,
    heroEmoji: String? = null,
    shakeTrigger: Int = 0,
) {
    val colors = FunTheme.colors

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val keySize = when {
            maxHeight < 560.dp -> 56.dp
            maxHeight < 680.dp -> 64.dp
            else -> 72.dp
        }
        val showHero = heroEmoji != null && maxHeight >= 640.dp

        val shake = remember { Animatable(0f) }
        val shakeDistancePx = with(LocalDensity.current) { 12.dp.toPx() }
        LaunchedEffect(shakeTrigger) {
            if (shakeTrigger > 0) {
                repeat(3) {
                    shake.animateTo(shakeDistancePx, tween(45))
                    shake.animateTo(-shakeDistancePx, tween(45))
                }
                shake.animateTo(0f, tween(45))
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (showHero && heroEmoji != null) {
                FunSurface(
                    modifier = Modifier
                        .size(88.dp)
                        .rotate(-6f),
                    shape = CircleShape,
                    containerColor = colors.lemon,
                    contentColor = colors.onAccent,
                    shadowOffset = 4.dp,
                    contentAlignment = Alignment.Center,
                ) {
                    EmojiText(text = heroEmoji, fontSize = 42.sp)
                }
                Spacer(Modifier.height(16.dp))
            }

            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = colors.textPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            if (subtitle != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            }
            Spacer(Modifier.height(20.dp))

            Column(
                modifier = Modifier.graphicsLayer { translationX = shake.value },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PinDots(filled = enteredCount)

                Box(
                    modifier = Modifier
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (messageText != null) {
                        Text(
                            text = messageText,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                keypadRows.forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        row.forEach { digit ->
                            DigitKey(digit = digit, size = keySize, enabled = enabled, onDigit = onDigit)
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Spacer(Modifier.size(keySize))
                    DigitKey(digit = '0', size = keySize, enabled = enabled, onDigit = onDigit)
                    BackspaceKey(size = keySize, enabled = enabled, onBackspace = onBackspace)
                }
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int) {
    val colors = FunTheme.colors
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(PinHasher.PIN_LENGTH) { index ->
            val isFilled = index < filled
            FunSurface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                containerColor = if (isFilled) colors.pink else colors.surface,
                shadowOffset = 2.dp,
                showShadow = isFilled,
            ) {}
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
    FunSurface(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f),
        shape = FunShapes.key,
        shadowOffset = 3.dp,
        onClick = { onDigit(digit) },
        enabled = enabled,
        role = Role.Button,
        contentAlignment = Alignment.Center,
    ) {
        Text(text = digit.toString(), style = MaterialTheme.typography.headlineMedium)
    }
}

@Composable
private fun BackspaceKey(
    size: Dp,
    enabled: Boolean,
    onBackspace: () -> Unit,
) {
    val colors = FunTheme.colors
    FunSurface(
        modifier = Modifier
            .size(size)
            .alpha(if (enabled) 1f else 0.4f),
        shape = FunShapes.key,
        containerColor = colors.lemon,
        contentColor = colors.onAccent,
        shadowOffset = 3.dp,
        onClick = onBackspace,
        enabled = enabled,
        role = Role.Button,
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Backspace,
            contentDescription = stringResource(R.string.lock_backspace),
            modifier = Modifier.size(28.dp),
        )
    }
}
