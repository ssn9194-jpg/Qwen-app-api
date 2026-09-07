package com.aistudio.superapp.ui.imagegen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aistudio.superapp.domain.model.GeneratedImage
import com.aistudio.superapp.domain.model.ModelConfig
import com.aistudio.superapp.domain.repository.AiRepository
import com.aistudio.superapp.domain.repository.GalleryRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ImageGenUiState(val generating: Boolean = false, val error: String? = null, val lastImage: GeneratedImage? = null)

class ImageGenViewModel(private val ai: AiRepository, private val gallery: GalleryRepository) : ViewModel() {
    private val _state = MutableStateFlow(ImageGenUiState())
    val state = _state.asStateFlow()
    val images = gallery.images.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun generate(prompt: String, aspect: String, quality: String, model: ModelConfig) {
        if (prompt.isBlank() || state.value.generating) return
        viewModelScope.launch {
            _state.update { it.copy(generating = true, error = null) }
            runCatching { ai.generateImage(prompt, aspect, quality, model) }
                .onSuccess { source ->
                    val image = gallery.add(source, prompt, model.id)
                    _state.update { it.copy(generating = false, lastImage = image) }
                }
                .onFailure { t -> _state.update { it.copy(generating = false, error = t.message) } }
        }
    }
    fun dismissError() = _state.update { it.copy(error = null) }

    companion object {
        fun factory(ai: AiRepository, gallery: GalleryRepository) = viewModelFactory { initializer { ImageGenViewModel(ai, gallery) } }
    }
}
