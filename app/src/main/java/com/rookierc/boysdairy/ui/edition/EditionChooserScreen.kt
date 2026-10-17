package com.rookierc.boysdairy.ui.edition

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.R
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.Edition
import com.rookierc.boysdairy.data.SettingsRepository
import com.rookierc.boysdairy.ui.common.ChoiceCard
import com.rookierc.boysdairy.ui.common.ChunkyButton
import com.rookierc.boysdairy.ui.common.EmojiText
import com.rookierc.boysdairy.ui.common.ScreenTitle
import com.rookierc.boysdairy.ui.common.emoji
import com.rookierc.boysdairy.ui.common.labelRes
import com.rookierc.boysdairy.ui.common.stringResource
import com.rookierc.boysdairy.ui.common.taglineRes
import com.rookierc.boysdairy.ui.theme.FunTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditionChooserUiState(
    /** Picked but not confirmed yet; `null` = nothing picked. */
    val selected: Edition? = null,
    val saving: Boolean = false,
) {
    val canConfirm: Boolean get() = selected != null && !saving
}

/**
 * Backs [EditionChooserScreen]. Lives in the unlocked session's store like every other screen's
 * ViewModel, so a half-made choice is gone after a lock (and is never put into saved state).
 */
class EditionChooserViewModel(private val settings: SettingsRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(EditionChooserUiState())
    val uiState: StateFlow<EditionChooserUiState> = _uiState.asStateFlow()

    fun select(edition: Edition) {
        _uiState.update { if (it.saving) it else it.copy(selected = edition) }
    }

    /** Stores the choice; the root then swaps this screen for the main UI by itself. */
    fun confirm() {
        val state = _uiState.value
        val choice = state.selected ?: return
        if (state.saving) return
        _uiState.update { it.copy(saving = true) }
        viewModelScope.launch {
            try {
                settings.setEdition(choice)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Nothing is logged; the button simply comes back for another try.
                _uiState.update { it.copy(saving = false) }
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer { EditionChooserViewModel(appContainer().settingsRepository) }
        }
    }
}

/**
 * Full-screen "boy or girl edition?" question, shown right after unlocking while no edition has
 * been chosen (new users after setting their PIN, existing users after updating). Never shown on
 * the lock screen, which stays neutral.
 */
@Composable
fun EditionChooserScreen(
    modifier: Modifier = Modifier,
    viewModel: EditionChooserViewModel = viewModel(factory = EditionChooserViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val colors = FunTheme.colors

    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        ScreenTitle(
            emoji = "🚻",
            title = stringResource(R.string.edition_chooser_title),
            subtitle = stringResource(R.string.edition_chooser_subtitle),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Max),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Edition.entries.forEach { option ->
                ChoiceCard(
                    selected = option == state.selected,
                    onClick = { viewModel.select(option) },
                    selectedColor = colors.pink,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                ) {
                    EmojiText(text = option.emoji(), fontSize = 52.sp)
                    Text(
                        text = stringResource(option.labelRes()),
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        text = stringResource(option.taglineRes()),
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.edition_chooser_note),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.textSecondary,
        )

        ChunkyButton(
            text = stringResource(R.string.edition_chooser_confirm),
            emoji = "✅",
            onClick = viewModel::confirm,
            enabled = state.canConfirm,
            modifier = Modifier.fillMaxWidth(),
            minHeight = 58.dp,
        )
    }
}
