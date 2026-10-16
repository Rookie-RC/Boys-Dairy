package com.rookierc.boysdairy.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.ai.AiAnalysisService
import com.rookierc.boysdairy.ai.AiError
import com.rookierc.boysdairy.ai.AiOptions
import com.rookierc.boysdairy.ai.AiPreview
import com.rookierc.boysdairy.ai.AiProvider
import com.rookierc.boysdairy.ai.AiResult
import com.rookierc.boysdairy.ai.AiSelection
import com.rookierc.boysdairy.ai.ApiKeyStore
import com.rookierc.boysdairy.ai.RoastLevel
import com.rookierc.boysdairy.ai.aiSelectionFlow
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** What the "see what will be sent" section currently shows. */
sealed interface PreviewState {
    data object Idle : PreviewState
    data object Loading : PreviewState

    /** [preview] is `null` when there is nothing to send (no entries yet). */
    data class Ready(val preview: AiPreview?) : PreviewState
}

/** Where the analysis currently is. */
sealed interface AiPhase {
    data object Idle : AiPhase
    data object Loading : AiPhase
    data class Result(val text: String, val model: String) : AiPhase

    /** [provider] is the one that was asked, so the message names the right service even if the selection changes later. */
    data class Failed(val error: AiError, val provider: AiProvider) : AiPhase
}

data class AiUiState(
    val roastLevel: RoastLevel = RoastLevel.SPICY,
    /** Defaults to OFF: notes are the most personal part of the diary. */
    val includeNotes: Boolean = false,
    val previewExpanded: Boolean = false,
    val preview: PreviewState = PreviewState.Idle,
    val phase: AiPhase = AiPhase.Idle,
)

/**
 * Lives in the per-unlock session store, so a roast that is on screen disappears when the app
 * locks and any request still in flight is cancelled. Nothing is sent before [analyze] is called.
 */
class AiViewModel(
    private val service: AiAnalysisService,
    private val settings: SettingsRepository,
    apiKeyStore: ApiKeyStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AiUiState())
    val uiState: StateFlow<AiUiState> = _uiState.asStateFlow()

    /**
     * The provider / model / effort the next analysis will use and whether its key is set; `null`
     * until the first read, so the UI does not flash a "no key" hint.
     */
    val selection: StateFlow<AiSelection?> = aiSelectionFlow(settings, apiKeyStore).stateIn<AiSelection?>(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    private var previewJob: Job? = null
    private var analyzeJob: Job? = null

    /** Same setting as in Settings, which stays the source of truth. */
    fun setProvider(provider: AiProvider) {
        viewModelScope.launch { settings.setAiProvider(provider) }
    }

    fun setRoastLevel(level: RoastLevel) = _uiState.update { it.copy(roastLevel = level) }

    fun setIncludeNotes(include: Boolean) = _uiState.update { it.copy(includeNotes = include) }

    fun setPreviewExpanded(expanded: Boolean) = _uiState.update { it.copy(previewExpanded = expanded) }

    /** Asks the service what it would send (nothing leaves the device here). */
    fun refreshPreview(languageTag: String) {
        previewJob?.cancel()
        val options = currentOptions(languageTag)
        _uiState.update { it.copy(preview = PreviewState.Loading) }
        previewJob = viewModelScope.launch {
            val preview = try {
                service.previewPayload(options)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                null
            }
            _uiState.update { it.copy(preview = PreviewState.Ready(preview)) }
        }
    }

    /** The only place where data is actually sent: called when the user taps the big button. */
    fun analyze(languageTag: String) {
        if (_uiState.value.phase is AiPhase.Loading) return
        val options = currentOptions(languageTag)
        val provider = selection.value?.provider ?: AiProvider.DEFAULT
        _uiState.update { it.copy(phase = AiPhase.Loading) }
        analyzeJob = viewModelScope.launch {
            val result = try {
                service.analyze(options)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                AiResult.Failure(AiError.UNKNOWN)
            }
            val phase = when (result) {
                is AiResult.Success -> AiPhase.Result(text = result.text, model = result.model)
                is AiResult.Failure -> AiPhase.Failed(result.error, provider)
            }
            _uiState.update { it.copy(phase = phase) }
        }
    }

    /** Stops a running request and goes back to the start. */
    fun cancel() {
        analyzeJob?.cancel()
        analyzeJob = null
        _uiState.update { it.copy(phase = AiPhase.Idle) }
    }

    /** Clears a finished result / error. */
    fun reset() {
        _uiState.update { it.copy(phase = AiPhase.Idle) }
    }

    private fun currentOptions(languageTag: String): AiOptions {
        val state = _uiState.value
        return AiOptions(
            includeNotes = state.includeNotes,
            languageTag = languageTag,
            roastLevel = state.roastLevel,
        )
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                AiViewModel(container.aiAnalysisService, container.settingsRepository, container.apiKeyStore)
            }
        }
    }
}
