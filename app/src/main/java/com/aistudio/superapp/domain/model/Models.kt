package com.aistudio.superapp.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class Provider { OPENAI, GEMINI, ANTHROPIC, OPENAI_COMPATIBLE }

@Serializable
enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED, DYNAMIC }

@Serializable
enum class AppLanguage { ENGLISH, PERSIAN }

@Serializable
data class AppSettings(
    val language: AppLanguage = AppLanguage.PERSIAN,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val selectedChatModelId: String = "gpt-4o-mini",
    val selectedImageModelId: String = "gpt-image-1",
)

@Serializable
data class ChatAttachment(
    val uri: String,
    val mimeType: String = "image/jpeg",
    val displayName: String = "attachment",
    val remote: Boolean = false,
)

@Serializable
data class ChatMessage(
    val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val attachments: List<ChatAttachment> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val isNote: Boolean = false,
)

@Serializable
data class ChatSession(
    val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class ModelConfig(
    val id: String,
    val displayName: String,
    val provider: Provider,
    val baseUrl: String,
    val supportsChat: Boolean = true,
    val supportsVision: Boolean = false,
    val supportsImageGeneration: Boolean = false,
    val supportsAudio: Boolean = false,
    val enabled: Boolean = true,
    val builtIn: Boolean = false,
)

data class SearchHit(
    val messageId: Long,
    val sessionId: String,
    val sessionTitle: String,
    val role: String,
    val content: String,
    val createdAt: Long,
)

data class SessionSummary(val suggestedTitle: String, val summary: String)

data class GeneratedImage(
    val id: String,
    val source: String,
    val prompt: String,
    val modelId: String,
    val createdAt: Long,
)

data class PingResult(val ok: Boolean, val latencyMs: Long, val message: String)
