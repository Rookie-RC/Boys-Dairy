package com.rookierc.boysdairy.security

import androidx.biometric.BiometricManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device capability + switch → what Settings and the lock screen show. */
class BiometricPolicyTest {

    @Test
    fun capabilityFromBiometricManager() {
        assertEquals(BiometricCapability.AVAILABLE, biometricCapabilityOf(BiometricManager.BIOMETRIC_SUCCESS))
        assertEquals(
            BiometricCapability.NOT_ENROLLED,
            biometricCapabilityOf(BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED),
        )
        for (code in listOf(
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE,
            BiometricManager.BIOMETRIC_ERROR_UNSUPPORTED,
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED,
            BiometricManager.BIOMETRIC_STATUS_UNKNOWN,
            12345,
        )) {
            assertEquals("code $code", BiometricCapability.UNSUPPORTED, biometricCapabilityOf(code))
        }
    }

    @Test
    fun settingsRow() {
        for (enabled in listOf(false, true)) {
            assertEquals(BiometricSettingRow.HIDDEN, biometricSettingRow(BiometricCapability.UNSUPPORTED, enabled))
            // Shown but cannot be switched on; a stored "on" is not shown as on either.
            assertEquals(
                BiometricSettingRow.NEEDS_ENROLLMENT,
                biometricSettingRow(BiometricCapability.NOT_ENROLLED, enabled),
            )
        }
        assertEquals(BiometricSettingRow.OFF, biometricSettingRow(BiometricCapability.AVAILABLE, enabled = false))
        assertEquals(BiometricSettingRow.ON, biometricSettingRow(BiometricCapability.AVAILABLE, enabled = true))
    }

    @Test
    fun lockScreenOnlyOffersItWhenLockedOnAndUsable() {
        assertTrue(lockScreenOffersBiometric(LockState.Locked, BiometricCapability.AVAILABLE, enabled = true))

        val states = listOf(LockState.Loading, LockState.NeedsSetup, LockState.Locked, LockState.Unlocked)
        for (state in states) {
            for (capability in BiometricCapability.entries) {
                for (enabled in listOf(false, true)) {
                    val expected = state == LockState.Locked && capability == BiometricCapability.AVAILABLE && enabled
                    assertEquals(
                        "$state / $capability / enabled=$enabled",
                        expected,
                        lockScreenOffersBiometric(state, capability, enabled),
                    )
                }
            }
        }
    }

    @Test
    fun neverDuringFirstRunSetup() {
        assertFalse(lockScreenOffersBiometric(LockState.NeedsSetup, BiometricCapability.AVAILABLE, enabled = true))
    }
}
