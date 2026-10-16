package com.rookierc.boysdairy.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.rookierc.boysdairy.ai.AiProvider
import com.rookierc.boysdairy.ai.AiSelection
import com.rookierc.boysdairy.ai.ApiKeyStore
import com.rookierc.boysdairy.ai.aiSelectionFlow
import com.rookierc.boysdairy.appContainer
import com.rookierc.boysdairy.data.SettingsRepository
import com.rookierc.boysdairy.data.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settings: SettingsRepository,
    private val apiKeyStore: ApiKeyStore,
) : ViewModel() {

    val themeMode: StateFlow<ThemeMode> = settings.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeMode.SYSTEM,
    )

    /**
     * The selected AI provider and its key status; `null` until the first read. The key itself
     * never reaches the UI layer.
     */
    val ai: StateFlow<AiSelection?> = aiSelectionFlow(settings, apiKeyStore).stateIn<AiSelection?>(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settings.setThemeMode(mode) }
    }

    fun selectProvider(provider: AiProvider) {
        viewModelScope.launch { settings.setAiProvider(provider) }
    }

    fun saveApiKey(provider: AiProvider, key: String) {
        viewModelScope.launch { apiKeyStore.save(provider, key.trim()) }
    }

    fun clearApiKey(provider: AiProvider) {
        viewModelScope.launch { apiKeyStore.clear(provider) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = appContainer()
                SettingsViewModel(container.settingsRepository, container.apiKeyStore)
            }
        }
    }
}
