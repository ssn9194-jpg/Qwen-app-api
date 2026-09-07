package com.aistudio.superapp.data.repository

import com.aistudio.superapp.BuildConfig
import com.aistudio.superapp.data.local.ModelConfigEntity
import com.aistudio.superapp.data.local.ModelDao
import com.aistudio.superapp.domain.model.ModelConfig
import com.aistudio.superapp.domain.model.Provider
import com.aistudio.superapp.domain.repository.ModelRegistryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ModelRegistryRepositoryImpl(private val dao: ModelDao) : ModelRegistryRepository {
    override val models: Flow<List<ModelConfig>> = dao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun get(id: String): ModelConfig? = dao.get(id)?.toDomain()
    override suspend fun upsert(model: ModelConfig) = dao.upsert(model.toEntity())
    override suspend fun delete(id: String) = dao.deleteCustom(id)

    override suspend fun ensureDefaults() {
        dao.insertIgnore(
            listOf(
                model("gpt-4o", "GPT-4o", Provider.OPENAI, BuildConfig.DEFAULT_OPENAI_BASE_URL, vision = true),
                model("gpt-4o-mini", "GPT-4o mini", Provider.OPENAI, BuildConfig.DEFAULT_OPENAI_BASE_URL, vision = true),
                model("gpt-image-1", "GPT Image 1", Provider.OPENAI, BuildConfig.DEFAULT_OPENAI_BASE_URL, chat = false, image = true),
                model("whisper-1", "Whisper", Provider.OPENAI, BuildConfig.DEFAULT_OPENAI_BASE_URL, chat = false, audio = true),
                model("gemini-3.8-flash", "Gemini 3.8 Flash", Provider.GEMINI, BuildConfig.DEFAULT_GEMINI_BASE_URL, vision = true),
                model("gemini-3.1-pro-preview", "Gemini 3.1 Pro Preview", Provider.GEMINI, BuildConfig.DEFAULT_GEMINI_BASE_URL, vision = true),
                model("gemini-1.5-pro", "Gemini 1.5 Pro (legacy preset)", Provider.GEMINI, BuildConfig.DEFAULT_GEMINI_BASE_URL, vision = true),
                model("gemini-1.5-flash", "Gemini 1.5 Flash (legacy preset)", Provider.GEMINI, BuildConfig.DEFAULT_GEMINI_BASE_URL, vision = true),
                model("gemini-2.0-flash", "Gemini 2.0 Flash (legacy preset)", Provider.GEMINI, BuildConfig.DEFAULT_GEMINI_BASE_URL, vision = true),
                model("claude-sonnet-5", "Claude Sonnet 5", Provider.ANTHROPIC, BuildConfig.DEFAULT_ANTHROPIC_BASE_URL, vision = true),
                model("claude-sonnet-4-6", "Claude Sonnet 4.6", Provider.ANTHROPIC, BuildConfig.DEFAULT_ANTHROPIC_BASE_URL, vision = true),
            ),
        )
    }

    private fun model(
        id: String,
        name: String,
        provider: Provider,
        baseUrl: String,
        chat: Boolean = true,
        vision: Boolean = false,
        image: Boolean = false,
        audio: Boolean = false,
    ) = ModelConfigEntity(id, name, provider.name, baseUrl, chat, vision, image, audio, true, true)

    private fun ModelConfigEntity.toDomain() = ModelConfig(
        id, displayName, Provider.valueOf(provider), baseUrl, supportsChat, supportsVision,
        supportsImageGeneration, supportsAudio, enabled, builtIn,
    )

    private fun ModelConfig.toEntity() = ModelConfigEntity(
        id, displayName, provider.name, baseUrl.trimEnd('/'), supportsChat, supportsVision,
        supportsImageGeneration, supportsAudio, enabled, builtIn,
    )
}
