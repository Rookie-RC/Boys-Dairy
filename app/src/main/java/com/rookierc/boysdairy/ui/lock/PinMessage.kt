package com.rookierc.boysdairy.ui.lock

import androidx.compose.runtime.Composable
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.ui.common.quantityString
import com.rookierc.boysdairy.ui.common.stringResource

/** Feedback shown under the PIN dots. Resolved to a string resource only at render time. */
sealed interface PinMessage {
    /** The confirmation PIN did not match the first one. */
    data object Mismatch : PinMessage

    data class WrongPin(val attemptsLeft: Int) : PinMessage
}

/** Text for the line under the dots: lockout countdown wins over any other message. */
@Composable
fun pinMessageText(message: PinMessage?, lockoutSeconds: Int): String? = when {
    lockoutSeconds > 0 -> stringResource(R.string.lock_lockout, lockoutSeconds)
    message is PinMessage.Mismatch -> stringResource(R.string.lock_error_mismatch)
    message is PinMessage.WrongPin -> quantityString(R.plurals.lock_error_wrong, message.attemptsLeft, message.attemptsLeft)
    else -> null
}
