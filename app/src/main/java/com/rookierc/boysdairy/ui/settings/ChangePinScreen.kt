@file:OptIn(ExperimentalMaterial3Api::class)

package com.rookierc.boysdairy.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rookierc.boysdairy.R
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

    val titleRes = when (state.step) {
        ChangePinViewModel.Step.CURRENT -> R.string.change_pin_current
        ChangePinViewModel.Step.NEW -> R.string.change_pin_new
        ChangePinViewModel.Step.CONFIRM -> R.string.change_pin_confirm
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.change_pin_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        PinEntryContent(
            title = stringResource(titleRes),
            enteredCount = state.enteredCount,
            messageText = pinMessageText(state.message, state.lockoutSeconds),
            enabled = state.inputEnabled,
            onDigit = viewModel::onDigit,
            onBackspace = viewModel::onBackspace,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}
