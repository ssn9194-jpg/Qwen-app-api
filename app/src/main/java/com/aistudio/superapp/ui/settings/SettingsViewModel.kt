package com.aistudio.superapp.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aistudio.superapp.domain.model.*
import com.aistudio.superapp.domain.repository.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class SettingsUiState(
    val apiKeys: Map<Provider, String> = emptyMap(),
    val pingResults: Map<String, PingResult> = emptyMap(),
    val pinging: Set<String> = emptySet(),
    val error: String? = null,
)

class SettingsViewModel(
    private val settingsRepo: SettingsRepository,
    private val modelsRepo: ModelRegistryRepository,
    private val ai: AiRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state = _state.asStateFlow()
    val settings = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())
    val models = modelsRepo.models.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val map = Provider.entries.associateWith { settingsRepo.getApiKey(it) }
            _state.update { it.copy(apiKeys = map) }
        }
    }

    fun setLanguage(v: AppLanguage) = viewModelScope.launch { settingsRepo.updateLanguage(v) }
    fun setTheme(v: ThemeMode) = viewModelScope.launch { settingsRepo.updateTheme(v) }
    fun editKey(provider: Provider, value: String) = _state.update { it.copy(apiKeys = it.apiKeys + (provider to value)) }
    fun saveKey(provider: Provider) = viewModelScope.launch { settingsRepo.setApiKey(provider, state.value.apiKeys[provider].orEmpty()) }
    fun upsert(model: ModelConfig) = viewModelScope.launch { modelsRepo.upsert(model) }
    fun delete(model: ModelConfig) = viewModelScope.launch { if (!model.builtIn) modelsRepo.delete(model.id) }

    fun ping(model: ModelConfig) = viewModelScope.launch {
        _state.update { it.copy(pinging = it.pinging + model.id, error = null) }
        runCatching { ai.ping(model) }
            .onSuccess { result -> _state.update { it.copy(pinging = it.pinging - model.id, pingResults = it.pingResults + (model.id to result)) } }
            .onFailure { t -> _state.update { it.copy(pinging = it.pinging - model.id, error = t.message) } }
    }
    fun clearError() = _state.update { it.copy(error = null) }

    companion object {
        fun factory(settings: SettingsRepository, models: ModelRegistryRepository, ai: AiRepository) = viewModelFactory {
            initializer { SettingsViewModel(settings, models, ai) }
        }
    }
}
