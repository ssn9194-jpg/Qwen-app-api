package com.aistudio.superapp.ui.vision

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aistudio.superapp.domain.model.ChatAttachment
import com.aistudio.superapp.domain.model.ModelConfig
import com.aistudio.superapp.domain.repository.AiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VisionUiState(val loading: Boolean = false, val result: String = "", val error: String? = null)
class VisionViewModel(private val ai: AiRepository) : ViewModel() {
    private val _state = MutableStateFlow(VisionUiState())
    val state = _state.asStateFlow()
    fun analyze(prompt: String, image: ChatAttachment, model: ModelConfig) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { ai.analyzeVision(prompt, image, model) }
            .onSuccess { result -> _state.update { it.copy(loading = false, result = result) } }
            .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
    }
    fun clearError() = _state.update { it.copy(error = null) }
    companion object { fun factory(ai: AiRepository) = viewModelFactory { initializer { VisionViewModel(ai) } } }
}
