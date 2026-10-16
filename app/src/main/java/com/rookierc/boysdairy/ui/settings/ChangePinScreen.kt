package com.rookierc.boysdairy.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.ui.common.FunTopBar
import com.rookierc.boysdairy.ui.lock.PinEntryContent
import com.rookierc.boysdairy.ui.lock.pinMessageText

@Composable
fun ChangePinScreen(
    onBack: () -> Unit,
    onFinished: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChangePinViewModel = viewModel(factory = ChangePinViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val successText = stringResource(R.string.change_pin_success)

    LaunchedEffect(state.finished) {
        if (state.finished) {
            Toast.makeText(context, successText, Toast.LENGTH_SHORT).show()
            onFinished()
        }
    }

    val title = when (state.step) {
        ChangePinViewModel.Step.CURRENT -> stringResource(R.string.change_pin_current)
        ChangePinViewModel.Step.NEW -> stringResource(R.string.change_pin_new)
        ChangePinViewModel.Step.CONFIRM -> stringResource(R.string.change_pin_confirm)
    }
    val subtitle = when (state.step) {
        ChangePinViewModel.Step.CURRENT -> stringResource(R.string.change_pin_current_sub)
        ChangePinViewModel.Step.NEW -> stringResource(R.string.change_pin_new_sub)
        ChangePinViewModel.Step.CONFIRM -> stringResource(R.string.change_pin_confirm_sub)
    }

    Column(modifier = modifier.fillMaxSize()) {
        FunTopBar(
            title = stringResource(R.string.change_pin_title),
            emoji = "🔑",
            onBack = onBack,
        )
        PinEntryContent(
            title = title,
            subtitle = subtitle,
            enteredCount = state.enteredCount,
            messageText = pinMessageText(state.message, state.lockoutSeconds),
            enabled = state.inputEnabled,
            shakeTrigger = state.shake,
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .navigationBarsPadding(),
        )
    }
}
