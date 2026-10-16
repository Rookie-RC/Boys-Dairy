package com.rookierc.boysdairy.ui.lock

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R

/** Full-screen PIN screen used for first-run setup and for unlocking. */
@Composable
fun LockScreen(
    modifier: Modifier = Modifier,
    viewModel: LockViewModel = viewModel(factory = LockViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val titleRes = when (state.step) {
        LockViewModel.Step.SETUP_ENTER -> R.string.lock_title_setup
        LockViewModel.Step.SETUP_CONFIRM -> R.string.lock_title_confirm
        LockViewModel.Step.UNLOCK -> R.string.lock_title_unlock
    }

    PinEntryContent(
        title = stringResource(titleRes),
        enteredCount = state.enteredCount,
        messageText = pinMessageText(state.message, state.lockoutSeconds),
        enabled = state.inputEnabled,
        onDigit = viewModel::onDigit,
        onBackspace = viewModel::onBackspace,
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding(),
    )
}
