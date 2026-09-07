package com.aistudio.superapp.domain.repository

import android.graphics.Bitmap
import com.aistudio.superapp.domain.model.*
import kotlinx.coroutines.flow.Flow
import java.io.File

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun updateLanguage(language: AppLanguage)
    suspend fun updateTheme(theme: ThemeMode)
    suspend fun setSelectedChatModel(id: String)
    suspend fun setSelectedImageModel(id: String)
    suspend fun setApiKey(provider: Provider, value: String)
    suspend fun getApiKey(provider: Provider): String
}

interface ModelRegistryRepository {
    val models: Flow<List<ModelConfig>>
    suspend fun get(id: String): ModelConfig?
    suspend fun upsert(model: ModelConfig)
    suspend fun delete(id: String)
    suspend fun ensureDefaults()
}

interface AiRepository {
    fun streamChat(messages: List<ChatMessage>, model: ModelConfig, attachment: ChatAttachment? = null): Flow<String>
    suspend fun completeText(messages: List<ChatMessage>, model: ModelConfig): String
    suspend fun generateImage(prompt: String, aspectRatio: String, quality: String, model: ModelConfig): String
    suspend fun analyzeVision(prompt: String, attachment: ChatAttachment, model: ModelConfig): String
    suspend fun transcribe(file: File, model: ModelConfig): String
    suspend fun editImage(image: Bitmap, mask: Bitmap?, prompt: String, model: ModelConfig): String
    suspend fun ping(model: ModelConfig): PingResult
    suspend fun downloadBytes(source: String): ByteArray
}

interface ChatRepository {
    val sessions: Flow<List<ChatSession>>
    fun observeMessages(sessionId: String): Flow<List<ChatMessage>>
    suspend fun createSession(title: String = "New chat"): String
    suspend fun renameSession(sessionId: String, title: String)
    suspend fun clearSession(sessionId: String)
    fun sendMessage(sessionId: String, text: String, attachment: ChatAttachment?, model: ModelConfig): Flow<String>
    suspend fun addAssistantNote(sessionId: String, text: String)
    suspend fun summarizeSession(sessionId: String, model: ModelConfig): SessionSummary
    suspend fun search(query: String): List<SearchHit>
    suspend fun exportMarkdown(sessionId: String): String
    suspend fun exportJson(sessionId: String): String
}

interface GalleryRepository {
    val images: Flow<List<GeneratedImage>>
    suspend fun add(source: String, prompt: String, modelId: String): GeneratedImage
    suspend fun delete(id: String)
}
