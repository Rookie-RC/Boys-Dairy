package com.rookierc.boysdairy.ui.lock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.ui.common.stringArrayResource
import com.rookierc.boysdairy.ui.common.stringResource
import kotlin.random.Random

/** Full-screen PIN screen used for first-run setup and for unlocking. */
@Composable
fun LockScreen(
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = viewModel(factory = LockViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val unlockSubtitles = stringArrayResource(R.array.lock_unlock_subtitles)
    val subtitleSeed = remember { Random.nextInt(1_000) }

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

    PinEntryContent(
        title = title,
        subtitle = subtitle,
        heroEmoji = hero,
        enteredCount = state.enteredCount,
        messageText = pinMessageText(state.message, state.lockoutSeconds),
        enabled = state.inputEnabled,
        shakeTrigger = state.shake,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
    )
}
