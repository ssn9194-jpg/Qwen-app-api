package com.aistudio.superapp.data.network

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Base64
import com.aistudio.superapp.domain.model.*
import com.aistudio.superapp.domain.repository.SettingsRepository
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.json
import io.ktor.utils.io.readUTF8Line
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.time.measureTimedValue

class KtorAiGateway(
    private val context: Context,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val client = HttpClient(OkHttp) {
        expectSuccess = false
        install(ContentNegotiation) { json(json) }
        install(HttpTimeout) {
            requestTimeoutMillis = 120_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 120_000
        }
    }

    fun streamChat(messages: List<ChatMessage>, model: ModelConfig, attachment: ChatAttachment?): Flow<String> =
        when (model.provider) {
            Provider.OPENAI, Provider.OPENAI_COMPATIBLE -> streamOpenAi(messages, model, attachment)
            Provider.GEMINI -> streamGemini(messages, model, attachment)
            Provider.ANTHROPIC -> streamAnthropic(messages, model, attachment)
        }

    private fun streamOpenAi(messages: List<ChatMessage>, model: ModelConfig, attachment: ChatAttachment?) = flow {
        val key = settings.getApiKey(model.provider)
        val lastAttachmentUrl = attachment?.takeIf { it.mimeType.startsWith("image/") }?.let { attachmentAsDataUrlOrRemote(it) }
        val lastDocumentText = attachment?.takeUnless { it.mimeType.startsWith("image/") }?.let { readDocumentText(it) }
        val requestMessages = buildJsonArray {
            messages.forEachIndexed { index, message ->
                add(buildJsonObject {
                    put("role", message.role)
                    val isLast = index == messages.lastIndex
                    if (isLast && lastAttachmentUrl != null) {
                        put("content", buildJsonArray {
                            add(buildJsonObject { put("type", "text"); put("text", message.content) })
                            add(buildJsonObject {
                                put("type", "image_url")
                                put("image_url", buildJsonObject { put("url", lastAttachmentUrl) })
                            })
                        })
                    } else if (isLast && lastDocumentText != null) {
                        put("content", message.content + "\n\n[Attached document: ${attachment?.displayName}]\n" + lastDocumentText)
                    } else put("content", message.content)
                })
            }
        }
        val body = buildJsonObject {
            put("model", model.id)
            put("stream", true)
            put("messages", requestMessages)
        }
        val url = endpoint(model.baseUrl, "/v1/chat/completions")
        client.preparePost(url) {
            contentType(ContentType.Application.Json)
            if (key.isNotBlank()) bearerAuth(key)
            setBody(body)
        }.execute { response ->
            ensureSuccess(response)
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                runCatching {
                    val root = json.parseToJsonElement(payload).jsonObject
                    root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("delta")?.jsonObject?.get("content")?.jsonPrimitive?.contentOrNull
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { emit(it) }
            }
        }
    }

    private fun streamGemini(messages: List<ChatMessage>, model: ModelConfig, attachment: ChatAttachment?) = flow {
        val key = settings.getApiKey(Provider.GEMINI)
        require(key.isNotBlank()) { "Gemini API key is missing." }
        val attachmentData = attachment?.takeIf { it.mimeType.startsWith("image/") }?.let { attachmentBytes(it) }
        val documentText = attachment?.takeUnless { it.mimeType.startsWith("image/") }?.let { readDocumentText(it) }
        val contents = buildJsonArray {
            messages.forEachIndexed { index, message ->
                add(buildJsonObject {
                    put("role", if (message.role == "assistant") "model" else "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", message.content) })
                        if (index == messages.lastIndex && documentText != null) {
                            add(buildJsonObject { put("text", "Attached document: ${attachment?.displayName}\n$documentText") })
                        }
                        if (index == messages.lastIndex && attachmentData != null) {
                            val (mime, b64) = attachmentData
                            add(buildJsonObject {
                                put("inline_data", buildJsonObject {
                                    put("mime_type", mime)
                                    put("data", b64)
                                })
                            })
                        }
                    })
                })
            }
        }
        val base = model.baseUrl.trimEnd('/')
        val url = "$base/v1beta/models/${model.id}:streamGenerateContent?alt=sse"
        client.preparePost(url) {
            contentType(ContentType.Application.Json)
            header("x-goog-api-key", key)
            header("x-goog-api-client", "ai-studio-superapp/1.0.0")
            setBody(buildJsonObject { put("contents", contents) })
        }.execute { response ->
            ensureSuccess(response)
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                runCatching {
                    val root = json.parseToJsonElement(payload).jsonObject
                    root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject
                        ?.get("content")?.jsonObject?.get("parts")?.jsonArray
                        ?.joinToString("") { part -> part.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
                }.getOrNull()?.takeIf { it.isNotBlank() }?.let { emit(it) }
            }
        }
    }

    private fun streamAnthropic(messages: List<ChatMessage>, model: ModelConfig, attachment: ChatAttachment?) = flow {
        val key = settings.getApiKey(Provider.ANTHROPIC)
        require(key.isNotBlank()) { "Anthropic API key is missing." }
        val nonSystemMessages = messages.filter { it.role != "system" }
        val attachmentData = attachment?.takeIf { it.mimeType.startsWith("image/") }?.let { attachmentBytes(it) }
        val documentText = attachment?.takeUnless { it.mimeType.startsWith("image/") }?.let { readDocumentText(it) }
        val bodyMessages = buildJsonArray {
            nonSystemMessages.forEachIndexed { index, message ->
                add(buildJsonObject {
                    put("role", if (message.role == "assistant") "assistant" else "user")
                    if (index == nonSystemMessages.lastIndex && attachmentData != null) {
                        val (mime, b64) = attachmentData
                        put("content", buildJsonArray {
                            add(buildJsonObject {
                                put("type", "image")
                                put("source", buildJsonObject {
                                    put("type", "base64")
                                    put("media_type", mime)
                                    put("data", b64)
                                })
                            })
                            add(buildJsonObject { put("type", "text"); put("text", message.content) })
                        })
                    } else if (index == nonSystemMessages.lastIndex && documentText != null) {
                        put("content", message.content + "\n\n[Attached document: ${attachment?.displayName}]\n" + documentText)
                    } else put("content", message.content)
                })
            }
        }
        val systemText = messages.filter { it.role == "system" }.joinToString("\n") { it.content }
        val body = buildJsonObject {
            put("model", model.id)
            put("max_tokens", 4096)
            put("stream", true)
            if (systemText.isNotBlank()) put("system", systemText)
            put("messages", bodyMessages)
        }
        client.preparePost(endpoint(model.baseUrl, "/v1/messages")) {
            contentType(ContentType.Application.Json)
            header("x-api-key", key)
            header("anthropic-version", "2023-06-01")
            setBody(body)
        }.execute { response ->
            ensureSuccess(response)
            val channel = response.bodyAsChannel()
            while (!channel.isClosedForRead) {
                val line = channel.readUTF8Line() ?: break
                if (!line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                runCatching {
                    val root = json.parseToJsonElement(payload).jsonObject
                    root["delta"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { emit(it) }
            }
        }
    }

    suspend fun generateImage(prompt: String, aspectRatio: String, quality: String, model: ModelConfig): String {
        require(model.provider == Provider.OPENAI || model.provider == Provider.OPENAI_COMPATIBLE) {
            "Image generation is available through OpenAI/OpenAI-compatible image endpoints."
        }
        val key = settings.getApiKey(model.provider)
        val size = when (aspectRatio) {
            "16:9" -> "1536x1024"
            "9:16" -> "1024x1536"
            "4:3" -> "1536x1024"
            else -> "1024x1024"
        }
        val body = buildJsonObject {
            put("model", model.id)
            put("prompt", prompt)
            put("size", size)
            put("quality", when (quality) { "4K" -> "high"; "HD" -> "hd"; else -> "standard" })
        }
        val response = client.post(endpoint(model.baseUrl, "/v1/images/generations")) {
            contentType(ContentType.Application.Json)
            if (key.isNotBlank()) bearerAuth(key)
            setBody(body)
        }
        ensureSuccess(response)
        return extractImageSource(response.bodyAsText())
    }

    suspend fun transcribe(file: File, model: ModelConfig): String {
        require(model.provider == Provider.OPENAI || model.provider == Provider.OPENAI_COMPATIBLE) {
            "Whisper/STT requires an OpenAI-compatible audio endpoint."
        }
        val key = settings.getApiKey(model.provider)
        val response = client.post(endpoint(model.baseUrl, "/v1/audio/transcriptions")) {
            if (key.isNotBlank()) bearerAuth(key)
            setBody(MultiPartFormDataContent(formData {
                append("model", model.id)
                append("file", file.readBytes(), Headers.build {
                    append(HttpHeaders.ContentType, "audio/mp4")
                    append(HttpHeaders.ContentDisposition, "filename=recording.m4a")
                })
            }))
        }
        ensureSuccess(response)
        val root = json.parseToJsonElement(response.bodyAsText()).jsonObject
        return root["text"]?.jsonPrimitive?.contentOrNull ?: error("Transcription response had no text.")
    }

    suspend fun editImage(image: Bitmap, mask: Bitmap?, prompt: String, model: ModelConfig): String {
        require(model.provider == Provider.OPENAI || model.provider == Provider.OPENAI_COMPATIBLE) {
            "AI image editing requires an OpenAI-compatible image edits endpoint."
        }
        val key = settings.getApiKey(model.provider)
        val imageBytes = bitmapBytes(image)
        val maskBytes = mask?.let(::bitmapBytes)
        val response = client.post(endpoint(model.baseUrl, "/v1/images/edits")) {
            if (key.isNotBlank()) bearerAuth(key)
            setBody(MultiPartFormDataContent(formData {
                append("model", model.id)
                append("prompt", prompt)
                append("image", imageBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=image.png")
                })
                if (maskBytes != null) append("mask", maskBytes, Headers.build {
                    append(HttpHeaders.ContentType, "image/png")
                    append(HttpHeaders.ContentDisposition, "filename=mask.png")
                })
            }))
        }
        ensureSuccess(response)
        return extractImageSource(response.bodyAsText())
    }

    suspend fun ping(model: ModelConfig): PingResult {
        val timed = measureTimedValue {
            runCatching {
                val key = settings.getApiKey(model.provider)
                val url = when (model.provider) {
                    Provider.GEMINI -> "${model.baseUrl.trimEnd('/')}/v1beta/models"
                    Provider.ANTHROPIC -> endpoint(model.baseUrl, "/v1/models")
                    else -> endpoint(model.baseUrl, "/v1/models")
                }
                client.get(url) {
                    when (model.provider) {
                        Provider.ANTHROPIC -> { if (key.isNotBlank()) header("x-api-key", key); header("anthropic-version", "2023-06-01") }
                        Provider.GEMINI -> { if (key.isNotBlank()) header("x-goog-api-key", key); header("x-goog-api-client", "ai-studio-superapp/1.0.0") }
                        else -> if (key.isNotBlank()) bearerAuth(key)
                    }
                }
            }
        }
        val response = timed.value.getOrNull()
        return if (response != null) {
            PingResult(response.status.value in 200..299, timed.duration.inWholeMilliseconds, "HTTP ${response.status.value}")
        } else PingResult(false, timed.duration.inWholeMilliseconds, timed.value.exceptionOrNull()?.message ?: "Connection failed")
    }

    suspend fun downloadBytes(source: String): ByteArray = when {
        source.startsWith("http://") || source.startsWith("https://") -> client.get(source).body()
        else -> context.contentResolver.openInputStream(Uri.parse(source))?.use { it.readBytes() }
            ?: File(Uri.parse(source).path.orEmpty()).readBytes()
    }

    private suspend fun attachmentAsDataUrlOrRemote(attachment: ChatAttachment): String {
        if (attachment.remote && (attachment.uri.startsWith("http://") || attachment.uri.startsWith("https://"))) return attachment.uri
        val (mime, b64) = attachmentBytes(attachment)
        return "data:$mime;base64,$b64"
    }

    private suspend fun attachmentBytes(attachment: ChatAttachment): Pair<String, String> {
        val bytes = when {
            attachment.remote && attachment.uri.startsWith("http") -> client.get(attachment.uri).body<ByteArray>()
            else -> context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { it.readBytes() }
                ?: error("Cannot open attachment: ${attachment.uri}")
        }
        return attachment.mimeType to Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private suspend fun readDocumentText(attachment: ChatAttachment): String {
        val bytes = when {
            attachment.remote && attachment.uri.startsWith("http") -> client.get(attachment.uri).body<ByteArray>()
            else -> context.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { it.readBytes() }
                ?: error("Cannot open document: ${attachment.uri}")
        }
        return bytes.take(120_000).toByteArray().toString(Charsets.UTF_8)
    }

    private fun endpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trimEnd('/')
        return when {
            path.startsWith("/v1/") && (base.endsWith("/v1") || base.endsWith("/api/v1")) -> base + path.removePrefix("/v1")
            else -> base + path
        }
    }

    private suspend fun ensureSuccess(response: HttpResponse) {
        if (response.status.value !in 200..299) {
            val body = runCatching { response.bodyAsText() }.getOrDefault("")
            error("HTTP ${response.status.value}: ${body.take(500)}")
        }
    }

    private fun extractImageSource(body: String): String {
        val item = json.parseToJsonElement(body).jsonObject["data"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: error("Image API returned no data.")
        item["url"]?.jsonPrimitive?.contentOrNull?.let { return it }
        val b64 = item["b64_json"]?.jsonPrimitive?.contentOrNull ?: error("Image API returned no URL or base64 image.")
        val dir = File(context.cacheDir, "generated").apply { mkdirs() }
        val file = File(dir, "${UUID.randomUUID()}.png").apply { writeBytes(Base64.decode(b64, Base64.DEFAULT)) }
        return Uri.fromFile(file).toString()
    }

    private fun bitmapBytes(bitmap: Bitmap): ByteArray = ByteArrayOutputStream().use { out ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        out.toByteArray()
    }
}
