package com.aistudio.superapp.ui.voice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aistudio.superapp.domain.model.ModelConfig
import com.aistudio.superapp.domain.repository.AiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class VoiceUiState(val loading: Boolean = false, val transcript: String = "", val error: String? = null)
class VoiceViewModel(private val ai: AiRepository) : ViewModel() {
    private val _state = MutableStateFlow(VoiceUiState())
    val state = _state.asStateFlow()
    fun transcribe(file: File, model: ModelConfig) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { ai.transcribe(file, model) }
            .onSuccess { text -> _state.update { it.copy(loading = false, transcript = text) } }
            .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
    }
    fun updateTranscript(text: String) = _state.update { it.copy(transcript = text) }
    fun clearError() = _state.update { it.copy(error = null) }
    companion object { fun factory(ai: AiRepository) = viewModelFactory { initializer { VoiceViewModel(ai) } } }
}
