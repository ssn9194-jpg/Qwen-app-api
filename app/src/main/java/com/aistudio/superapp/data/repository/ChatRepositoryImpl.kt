package com.aistudio.superapp.data.repository

import com.aistudio.superapp.data.local.*
import com.aistudio.superapp.domain.model.*
import com.aistudio.superapp.domain.repository.AiRepository
import com.aistudio.superapp.domain.repository.ChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

class ChatRepositoryImpl(
    private val sessionDao: SessionDao,
    private val messageDao: MessageDao,
    private val ai: AiRepository,
) : ChatRepository {
    private val json = Json { ignoreUnknownKeys = true; prettyPrint = false }

    override val sessions: Flow<List<ChatSession>> = sessionDao.observeAll().map { rows ->
        rows.map { ChatSession(it.id, it.title, it.createdAt, it.updatedAt) }
    }

    override fun observeMessages(sessionId: String): Flow<List<ChatMessage>> =
        messageDao.observeForSession(sessionId).map { rows -> rows.map(::toDomain) }

    override suspend fun createSession(title: String): String {
        val id = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        sessionDao.upsert(SessionEntity(id, title, now, now))
        return id
    }

    override suspend fun renameSession(sessionId: String, title: String) {
        sessionDao.rename(sessionId, title.trim().ifBlank { "Chat" }, System.currentTimeMillis())
    }

    override suspend fun clearSession(sessionId: String) {
        messageDao.clearSession(sessionId)
        sessionDao.touch(sessionId, System.currentTimeMillis())
    }

    override fun sendMessage(
        sessionId: String,
        text: String,
        attachment: ChatAttachment?,
        model: ModelConfig,
    ): Flow<String> = flow {
        if (sessionDao.get(sessionId) == null) {
            val now = System.currentTimeMillis()
            sessionDao.upsert(SessionEntity(sessionId, text.take(42).ifBlank { "New chat" }, now, now))
        }
        val attachments = attachment?.let { listOf(it) }.orEmpty()
        messageDao.insert(
            MessageEntity(
                sessionId = sessionId,
                role = "user",
                content = text,
                attachmentsJson = json.encodeToString(attachments),
            ),
        )
        val requestHistory = messageDao.listForSession(sessionId).map(::toDomain)
        val assistantId = messageDao.insert(MessageEntity(sessionId = sessionId, role = "assistant", content = ""))
        val buffer = StringBuilder()
        try {
            ai.streamChat(requestHistory, model, attachment).collect { chunk ->
                buffer.append(chunk)
                messageDao.updateContent(assistantId, buffer.toString())
                emit(chunk)
            }
            sessionDao.touch(sessionId, System.currentTimeMillis())
        } catch (t: Throwable) {
            val msg = buffer.toString().ifBlank { "⚠ ${t.message ?: "Request failed"}" }
            messageDao.updateContent(assistantId, msg)
            throw t
        }
    }

    override suspend fun addAssistantNote(sessionId: String, text: String) {
        messageDao.insert(MessageEntity(sessionId = sessionId, role = "assistant", content = text, isNote = true))
        sessionDao.touch(sessionId, System.currentTimeMillis())
    }

    override suspend fun summarizeSession(sessionId: String, model: ModelConfig): SessionSummary {
        val history = messageDao.listForSession(sessionId).joinToString("\n") { "${it.role}: ${it.content}" }.takeLast(14_000)
        val prompt = """
            Summarize the following conversation. Return exactly this format:
            TITLE: a concise title, maximum 7 words
            SUMMARY:
            - bullet one
            - bullet two
            - bullet three
            Use the conversation's dominant language.

            $history
        """.trimIndent()
        val raw = ai.completeText(listOf(ChatMessage(sessionId = sessionId, role = "user", content = prompt)), model)
        val title = raw.lineSequence().firstOrNull { it.trim().startsWith("TITLE:", true) }
            ?.substringAfter(":")?.trim()?.take(80).orEmpty().ifBlank { "Conversation summary" }
        val summary = raw.substringAfter("SUMMARY:", raw).trim()
        return SessionSummary(title, summary)
    }

    override suspend fun search(query: String): List<SearchHit> {
        val fts = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.joinToString(" AND ") {
            "\"${it.replace("\"", "\"\"")}\"*"
        }
        if (fts.isBlank()) return emptyList()
        return messageDao.search(fts).map {
            SearchHit(it.messageId, it.sessionId, it.sessionTitle ?: "Chat", it.role, it.content, it.createdAt)
        }
    }

    override suspend fun exportMarkdown(sessionId: String): String {
        val session = sessionDao.get(sessionId)
        val body = messageDao.listForSession(sessionId).joinToString("\n\n") {
            val role = if (it.role == "user") "You" else "Assistant"
            "## $role\n\n${it.content}"
        }
        return "# ${session?.title ?: "AI Studio Chat"}\n\n$body\n"
    }

    override suspend fun exportJson(sessionId: String): String {
        val pretty = Json { prettyPrint = true }
        return pretty.encodeToString(messageDao.listForSession(sessionId).map(::toDomain))
    }

    private fun toDomain(row: MessageEntity): ChatMessage = ChatMessage(
        id = row.id,
        sessionId = row.sessionId,
        role = row.role,
        content = row.content,
        attachments = runCatching { json.decodeFromString<List<ChatAttachment>>(row.attachmentsJson) }.getOrDefault(emptyList()),
        createdAt = row.createdAt,
        isNote = row.isNote,
    )
}
