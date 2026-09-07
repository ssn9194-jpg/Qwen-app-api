package com.aistudio.superapp.ui.photo

import android.graphics.Bitmap
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

data class PhotoUiState(val loading: Boolean = false, val resultSource: String? = null, val error: String? = null)
class PhotoEditorViewModel(private val ai: AiRepository) : ViewModel() {
    private val _state = MutableStateFlow(PhotoUiState())
    val state = _state.asStateFlow()
    fun modify(image: Bitmap, mask: Bitmap?, prompt: String, model: ModelConfig) = viewModelScope.launch {
        _state.update { it.copy(loading = true, error = null) }
        runCatching { ai.editImage(image, mask, prompt, model) }
            .onSuccess { src -> _state.update { it.copy(loading = false, resultSource = src) } }
            .onFailure { t -> _state.update { it.copy(loading = false, error = t.message) } }
    }
    fun clearError() = _state.update { it.copy(error = null) }
    companion object { fun factory(ai: AiRepository) = viewModelFactory { initializer { PhotoEditorViewModel(ai) } } }
}
