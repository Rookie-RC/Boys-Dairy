package com.rookierc.boysdairy.ui.common

import androidx.biometric.BiometricPrompt
import org.junit.Assert.assertEquals
import org.junit.Test

/** How the system prompt's error codes are treated (cancel = quietly back to the PIN pad). */
class BiometricOutcomeTest {

    @Test
    fun closingThePromptIsACancelNotAnError() {
        for (code in listOf(
            BiometricPrompt.ERROR_NEGATIVE_BUTTON, // "use code"
            BiometricPrompt.ERROR_USER_CANCELED,
            BiometricPrompt.ERROR_CANCELED, // e.g. the app went to the background
            BiometricPrompt.ERROR_TIMEOUT,
        )) {
            assertEquals("code $code", BiometricOutcome.Cancelled, biometricOutcomeOfError(code))
        }
    }

    @Test
    fun tooManyTriesIsALockout() {
        assertEquals(BiometricOutcome.LockedOut, biometricOutcomeOfError(BiometricPrompt.ERROR_LOCKOUT))
        assertEquals(BiometricOutcome.LockedOut, biometricOutcomeOfError(BiometricPrompt.ERROR_LOCKOUT_PERMANENT))
    }

    @Test
    fun everythingElseIsAFailure() {
        for (code in listOf(
            BiometricPrompt.ERROR_HW_UNAVAILABLE,
            BiometricPrompt.ERROR_UNABLE_TO_PROCESS,
            BiometricPrompt.ERROR_NO_SPACE,
            BiometricPrompt.ERROR_VENDOR,
            BiometricPrompt.ERROR_NO_BIOMETRICS,
            BiometricPrompt.ERROR_HW_NOT_PRESENT,
            BiometricPrompt.ERROR_NO_DEVICE_CREDENTIAL,
            BiometricPrompt.ERROR_SECURITY_UPDATE_REQUIRED,
            999,
        )) {
            assertEquals("code $code", BiometricOutcome.Failed, biometricOutcomeOfError(code))
        }
    }
}
