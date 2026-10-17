package com.rookierc.boysdairy.security

import androidx.biometric.BiometricManager

/** What the device offers for strong (Class 3) biometrics, the only kind that can unlock the app. */
enum class BiometricCapability {
    /** No usable strong biometric hardware (missing, unavailable, unsupported, unknown). */
    UNSUPPORTED,

    /** The hardware is there but no fingerprint is enrolled in the system settings yet. */
    NOT_ENROLLED,

    AVAILABLE,
}

/**
 * Maps a `BiometricManager.canAuthenticate(BIOMETRIC_STRONG)` result. Anything that is not clearly
 * "available" or "nothing enrolled" (no / unavailable hardware, unsupported, security update
 * required, unknown status) counts as unsupported, so the feature stays out of sight.
 */
fun biometricCapabilityOf(canAuthenticateResult: Int): BiometricCapability = when (canAuthenticateResult) {
    BiometricManager.BIOMETRIC_SUCCESS -> BiometricCapability.AVAILABLE
    BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> BiometricCapability.NOT_ENROLLED
    else -> BiometricCapability.UNSUPPORTED
}

/** How the "Fingerprint unlock" row in Settings looks. */
enum class BiometricSettingRow {
    /** The device cannot do it: the row is not shown at all. */
    HIDDEN,

    /** Shown, but the switch is off and disabled until a fingerprint is enrolled in the system. */
    NEEDS_ENROLLMENT,

    OFF,
    ON,
}

fun biometricSettingRow(capability: BiometricCapability, enabled: Boolean): BiometricSettingRow = when (capability) {
    BiometricCapability.UNSUPPORTED -> BiometricSettingRow.HIDDEN
    BiometricCapability.NOT_ENROLLED -> BiometricSettingRow.NEEDS_ENROLLMENT
    BiometricCapability.AVAILABLE -> if (enabled) BiometricSettingRow.ON else BiometricSettingRow.OFF
}

/**
 * Whether the lock screen offers fingerprint unlock (the 👆 key and the automatic prompt): only
 * when unlocking (never during first-run PIN setup), with the setting on and a fingerprint usable
 * right now. A PIN lockout does not matter here: the fingerprint stays available during it.
 */
fun lockScreenOffersBiometric(
    lockState: LockState,
    capability: BiometricCapability,
    enabled: Boolean,
): Boolean = lockState == LockState.Locked && enabled && capability == BiometricCapability.AVAILABLE
