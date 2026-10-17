package com.rookierc.boysdairy.ui.lock

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.ui.common.BiometricOutcome
import com.rookierc.boysdairy.ui.common.BiometricPromptTexts
import com.rookierc.boysdairy.ui.common.rememberBiometricPromptLauncher
import com.rookierc.boysdairy.ui.common.stringArrayResource
import com.rookierc.boysdairy.ui.common.stringResource
import kotlin.random.Random

/**
 * Full-screen PIN screen used for first-run setup and for unlocking (plus the optional 👆
 * fingerprint key). It sits outside the edition provider and is part of the disguise: every
 * text here, including the system fingerprint prompt's, stays neutral.
 */
@Composable
fun LockScreen(
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = viewModel(factory = LockViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val unlockSubtitles = stringArrayResource(R.array.lock_unlock_subtitles)
    val subtitleSeed = remember { Random.nextInt(1_000) }

    BiometricPromptEffect(viewModel)
    LockScreenVisibilityEffect(viewModel)

    val title = when (state.step) {
        LockViewModel.Step.SETUP_ENTER -> stringResource(R.string.lock_title_setup)
        LockViewModel.Step.SETUP_CONFIRM -> stringResource(R.string.lock_title_confirm)
        LockViewModel.Step.UNLOCK -> stringResource(R.string.lock_title_unlock)
    }
    val subtitle = when (state.step) {
        LockViewModel.Step.SETUP_ENTER -> stringResource(R.string.lock_sub_setup)
        LockViewModel.Step.SETUP_CONFIRM -> stringResource(R.string.lock_sub_confirm)
        LockViewModel.Step.UNLOCK -> unlockSubtitles[subtitleSeed % unlockSubtitles.size]
    }
    val hero = when (state.step) {
        LockViewModel.Step.SETUP_ENTER -> "🔐"
        LockViewModel.Step.SETUP_CONFIRM -> "🔁"
        LockViewModel.Step.UNLOCK -> "🧮"
    }
    val showBiometricKey = state.step == LockViewModel.Step.UNLOCK && state.biometricKey

    PinEntryContent(
        title = title,
        subtitle = subtitle,
        heroEmoji = hero,
        enteredCount = state.enteredCount,
        // PIN feedback (lockout countdown first) wins over the fingerprint note.
        messageText = pinMessageText(state.message, state.lockoutSeconds)
            ?: biometricNoticeText(state.biometricNotice),
        enabled = state.inputEnabled,
        shakeTrigger = state.shake,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        biometricKeyLabel = if (showBiometricKey) stringResource(R.string.lock_biometric_key_cd) else null,
        onBiometricKey = if (showBiometricKey) viewModel::onBiometricKey else null,
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
    )
}

@Composable
private fun biometricNoticeText(notice: BiometricNotice?): String? = when (notice) {
    BiometricNotice.KEY_CHANGED -> stringResource(R.string.lock_biometric_key_changed)
    BiometricNotice.LOCKED_OUT -> stringResource(R.string.lock_biometric_locked_out)
    BiometricNotice.UNAVAILABLE -> stringResource(R.string.lock_biometric_unavailable)
    null -> null
}

/** Shows the system fingerprint prompt whenever the view model asks for one. */
@Composable
private fun BiometricPromptEffect(viewModel: LockViewModel) {
    val request by viewModel.promptRequest.collectAsStateWithLifecycle()
    val launcher = rememberBiometricPromptLauncher()
    val texts = BiometricPromptTexts(
        title = stringResource(R.string.lock_biometric_prompt_title),
        negativeButton = stringResource(R.string.lock_biometric_prompt_use_code),
    )
    LaunchedEffect(request) {
        val pending = request ?: return@LaunchedEffect
        viewModel.onPromptRequestHandled(pending)
        val shown = launcher.launch(
            cipher = pending.cipher,
            texts = texts,
            confirmationRequired = false,
            onOutcome = viewModel::onPromptOutcome,
        )
        if (!shown) viewModel.onPromptOutcome(BiometricOutcome.Failed)
    }
}

/**
 * Tells the view model when the lock screen shows up and goes away. Adding the observer to an
 * already started lifecycle delivers ON_START right away, so composing the screen counts too.
 */
@Composable
private fun LockScreenVisibilityEffect(viewModel: LockViewModel) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = LocalContext.current.findActivity()
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.onScreenStarted()
                Lifecycle.Event.ON_STOP -> viewModel.onScreenStopped(activity?.isChangingConfigurations == true)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.onScreenStopped(activity?.isChangingConfigurations == true)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
