package com.aistudio.superapp.data.local

import androidx.room.*

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "messages", indices = [Index("sessionId"), Index("createdAt")])
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: String,
    val role: String,
    val content: String,
    val attachmentsJson: String = "[]",
    val createdAt: Long = System.currentTimeMillis(),
    val isNote: Boolean = false,
)

@Fts4(contentEntity = MessageEntity::class, tokenizer = FtsOptions.TOKENIZER_UNICODE61)
@Entity(tableName = "message_fts")
data class MessageFts(
    val content: String,
)

@Entity(tableName = "generated_images", indices = [Index("createdAt")])
data class GeneratedImageEntity(
    @PrimaryKey val id: String,
    val source: String,
    val prompt: String,
    val modelId: String,
    val createdAt: Long,
)

@Entity(tableName = "models")
data class ModelConfigEntity(
    @PrimaryKey val id: String,
    val displayName: String,
    val provider: String,
    val baseUrl: String,
    val supportsChat: Boolean,
    val supportsVision: Boolean,
    val supportsImageGeneration: Boolean,
    val supportsAudio: Boolean,
    val enabled: Boolean,
    val builtIn: Boolean,
)

data class SearchHitDb(
    val messageId: Long,
    val sessionId: String,
    val sessionTitle: String?,
    val role: String,
    val content: String,
    val createdAt: Long,
)
