package com.aistudio.superapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aistudio.superapp.domain.model.*
import com.aistudio.superapp.domain.repository.AiRepository
import com.aistudio.superapp.domain.repository.ChatRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

data class ChatUiState(
    val currentSessionId: String? = null,
    val isStreaming: Boolean = false,
    val error: String? = null,
    val searchHits: List<SearchHit> = emptyList(),
    val searching: Boolean = false,
    val summary: SessionSummary? = null,
    val speechDraft: String? = null,
)

class ChatViewModel(
    private val repo: ChatRepository,
    private val ai: AiRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state = _state.asStateFlow()
    val sessions = repo.sessions.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val messages = _state.map { it.currentSessionId }.distinctUntilChanged().filterNotNull()
        .flatMapLatest(repo::observeMessages)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val existing = repo.sessions.first().firstOrNull()?.id
            val id = existing ?: repo.createSession()
            _state.update { it.copy(currentSessionId = id) }
        }
    }

    fun selectSession(id: String) = _state.update { it.copy(currentSessionId = id, error = null) }
    fun newSession() = viewModelScope.launch { selectSession(repo.createSession()) }

    fun send(text: String, attachment: ChatAttachment?, model: ModelConfig) {
        val sessionId = state.value.currentSessionId ?: return
        if (text.isBlank() && attachment == null || state.value.isStreaming) return
        viewModelScope.launch {
            _state.update { it.copy(isStreaming = true, error = null) }
            runCatching { repo.sendMessage(sessionId, text.ifBlank { "Describe this image." }, attachment, model).collect() }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
            _state.update { it.copy(isStreaming = false) }
        }
    }

    fun clear() {
        val id = state.value.currentSessionId ?: return
        viewModelScope.launch { repo.clearSession(id) }
    }

    fun search(query: String) = viewModelScope.launch {
        _state.update { it.copy(searching = true) }
        val hits = runCatching { repo.search(query) }.getOrElse {
            _state.update { s -> s.copy(error = it.message) }; emptyList()
        }
        _state.update { it.copy(searchHits = hits, searching = false) }
    }

    fun summarize(model: ModelConfig) {
        val id = state.value.currentSessionId ?: return
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            runCatching { repo.summarizeSession(id, model) }
                .onSuccess { result -> _state.update { it.copy(summary = result) } }
                .onFailure { t -> _state.update { it.copy(error = t.message) } }
        }
    }

    fun dismissSummary() = _state.update { it.copy(summary = null) }
    fun renameFromSummary() {
        val id = state.value.currentSessionId ?: return
        val summary = state.value.summary ?: return
        viewModelScope.launch { repo.renameSession(id, summary.suggestedTitle); dismissSummary() }
    }
    fun insertSummary() {
        val id = state.value.currentSessionId ?: return
        val summary = state.value.summary ?: return
        viewModelScope.launch { repo.addAssistantNote(id, "${summary.suggestedTitle}\n\n${summary.summary}"); dismissSummary() }
    }

    fun transcribe(file: File, model: ModelConfig) = viewModelScope.launch {
        _state.update { it.copy(error = null) }
        runCatching { ai.transcribe(file, model) }
            .onSuccess { text -> _state.update { it.copy(speechDraft = text) } }
            .onFailure { t -> _state.update { it.copy(error = t.message) } }
    }
    fun consumeSpeechDraft() = _state.update { it.copy(speechDraft = null) }
    fun dismissError() = _state.update { it.copy(error = null) }

    suspend fun markdown(): String = state.value.currentSessionId?.let { repo.exportMarkdown(it) }.orEmpty()
    suspend fun json(): String = state.value.currentSessionId?.let { repo.exportJson(it) }.orEmpty()

    companion object {
        fun factory(repo: ChatRepository, ai: AiRepository) = viewModelFactory {
            initializer { ChatViewModel(repo, ai) }
        }
    }
}
