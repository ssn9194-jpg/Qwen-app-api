package com.aistudio.superapp.data.repository

import android.graphics.Bitmap
import com.aistudio.superapp.data.network.KtorAiGateway
import com.aistudio.superapp.domain.model.*
import com.aistudio.superapp.domain.repository.AiRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import java.io.File

class AiRepositoryImpl(private val gateway: KtorAiGateway) : AiRepository {
    override fun streamChat(messages: List<ChatMessage>, model: ModelConfig, attachment: ChatAttachment?): Flow<String> =
        gateway.streamChat(messages, model, attachment)

    override suspend fun completeText(messages: List<ChatMessage>, model: ModelConfig): String =
        gateway.streamChat(messages, model, null).toList().joinToString("")

    override suspend fun generateImage(prompt: String, aspectRatio: String, quality: String, model: ModelConfig): String =
        gateway.generateImage(prompt, aspectRatio, quality, model)

    override suspend fun analyzeVision(prompt: String, attachment: ChatAttachment, model: ModelConfig): String {
        require(model.supportsVision) { "Selected model does not advertise vision support." }
        val messages = listOf(ChatMessage(sessionId = "vision", role = "user", content = prompt))
        return gateway.streamChat(messages, model, attachment).toList().joinToString("")
    }

    override suspend fun transcribe(file: File, model: ModelConfig): String = gateway.transcribe(file, model)
    override suspend fun editImage(image: Bitmap, mask: Bitmap?, prompt: String, model: ModelConfig): String =
        gateway.editImage(image, mask, prompt, model)
    override suspend fun ping(model: ModelConfig): PingResult = gateway.ping(model)
    override suspend fun downloadBytes(source: String): ByteArray = gateway.downloadBytes(source)
}
