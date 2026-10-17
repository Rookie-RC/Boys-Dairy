package com.rookierc.boysdairy.ui.common

import android.content.Context
import android.content.ContextWrapper
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import javax.crypto.Cipher

/** How a system fingerprint prompt ended. */
sealed interface BiometricOutcome {
    /** [cipher] is the CryptoObject's cipher, now authorised for one operation (null if missing). */
    class Succeeded(val cipher: Cipher?) : BiometricOutcome

    /** Closed by the user ("use code", back, tap outside), by the system, or timed out. */
    data object Cancelled : BiometricOutcome

    /** Too many failed fingerprint tries; the system blocks fingerprints for a while. */
    data object LockedOut : BiometricOutcome

    /** Anything else (hardware trouble, could not be shown). */
    data object Failed : BiometricOutcome
}

/**
 * A prompt a view model wants shown; [cipher] goes into its CryptoObject. Compared by identity, so
 * every request is shown once.
 */
class BiometricPromptRequest(val cipher: Cipher)

/** Maps a [BiometricPrompt] `onAuthenticationError` code. */
fun biometricOutcomeOfError(errorCode: Int): BiometricOutcome = when (errorCode) {
    BiometricPrompt.ERROR_NEGATIVE_BUTTON,
    BiometricPrompt.ERROR_USER_CANCELED,
    BiometricPrompt.ERROR_CANCELED,
    BiometricPrompt.ERROR_TIMEOUT,
    -> BiometricOutcome.Cancelled

    BiometricPrompt.ERROR_LOCKOUT,
    BiometricPrompt.ERROR_LOCKOUT_PERMANENT,
    -> BiometricOutcome.LockedOut

    else -> BiometricOutcome.Failed
}

/** Texts of the system prompt. Keep them neutral: the dialog is drawn by the system over the disguise. */
class BiometricPromptTexts(
    val title: String,
    val negativeButton: String,
    val subtitle: String? = null,
)

/**
 * Shows the system fingerprint prompt (strong biometrics only, bound to a Keystore cipher). A new
 * [BiometricPrompt] is built for every launch so the result always reaches the caller that asked
 * (androidx keeps one callback per activity). The prompt is a system / in-app dialog, not an
 * activity, so showing it does not take the app to the background.
 */
@Stable
class BiometricPromptLauncher internal constructor(private val activity: FragmentActivity?) {

    /**
     * Returns false (and never calls [onOutcome]) when the prompt cannot be shown right now, e.g. no
     * activity or its state is already saved.
     */
    fun launch(
        cipher: Cipher,
        texts: BiometricPromptTexts,
        confirmationRequired: Boolean,
        onOutcome: (BiometricOutcome) -> Unit,
    ): Boolean {
        val host = activity ?: return false
        if (host.isFinishing || host.supportFragmentManager.isStateSaved) return false

        var delivered = false
        val deliver: (BiometricOutcome) -> Unit = { outcome ->
            if (!delivered) {
                delivered = true
                onOutcome(outcome)
            }
        }
        val prompt = BiometricPrompt(
            host,
            ContextCompat.getMainExecutor(host),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    deliver(BiometricOutcome.Succeeded(result.cryptoObject?.cipher))
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    deliver(biometricOutcomeOfError(errorCode))
                }

                // onAuthenticationFailed (a finger that does not match): the prompt stays open and
                // says so itself; nothing to do here.
            },
        )
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(texts.title)
            .apply { texts.subtitle?.let(::setSubtitle) }
            .setNegativeButtonText(texts.negativeButton)
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setConfirmationRequired(confirmationRequired)
            .build()
        return try {
            prompt.authenticate(info, BiometricPrompt.CryptoObject(cipher))
            true
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
    }
}

/** A [BiometricPromptLauncher] for the hosting activity (unwrapped from the locale-wrapped context). */
@Composable
fun rememberBiometricPromptLauncher(): BiometricPromptLauncher {
    val context = LocalContext.current
    return remember(context) { BiometricPromptLauncher(context.findFragmentActivity()) }
}

private tailrec fun Context.findFragmentActivity(): FragmentActivity? = when (this) {
    is FragmentActivity -> this
    is ContextWrapper -> baseContext.findFragmentActivity()
    else -> null
}
