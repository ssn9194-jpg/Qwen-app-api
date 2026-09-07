package com.aistudio.superapp.data.settings

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.aistudio.superapp.domain.model.*
import com.aistudio.superapp.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

private val Context.aiStudioDataStore by preferencesDataStore(name = "ai_studio_settings")

class SettingsRepositoryImpl(
    private val context: Context,
    private val crypto: CryptoManager = CryptoManager(),
) : SettingsRepository {
    private object Keys {
        val language = stringPreferencesKey("language")
        val theme = stringPreferencesKey("theme")
        val chatModel = stringPreferencesKey("chat_model")
        val imageModel = stringPreferencesKey("image_model")
        val openAiKey = stringPreferencesKey("key_openai")
        val geminiKey = stringPreferencesKey("key_gemini")
        val anthropicKey = stringPreferencesKey("key_anthropic")
        val compatibleKey = stringPreferencesKey("key_compatible")
    }

    override val settings: Flow<AppSettings> = context.aiStudioDataStore.data.map { p ->
        AppSettings(
            language = enumValueOrDefault(p[Keys.language], AppLanguage.PERSIAN),
            theme = enumValueOrDefault(p[Keys.theme], ThemeMode.SYSTEM),
            selectedChatModelId = p[Keys.chatModel] ?: "gpt-4o-mini",
            selectedImageModelId = p[Keys.imageModel] ?: "gpt-image-1",
        )
    }

    override suspend fun updateLanguage(language: AppLanguage) {
        context.aiStudioDataStore.edit { it[Keys.language] = language.name }
    }

    override suspend fun updateTheme(theme: ThemeMode) {
        context.aiStudioDataStore.edit { it[Keys.theme] = theme.name }
    }

    override suspend fun setSelectedChatModel(id: String) {
        context.aiStudioDataStore.edit { it[Keys.chatModel] = id }
    }

    override suspend fun setSelectedImageModel(id: String) {
        context.aiStudioDataStore.edit { it[Keys.imageModel] = id }
    }

    override suspend fun setApiKey(provider: Provider, value: String) {
        val key = keyFor(provider)
        context.aiStudioDataStore.edit { it[key] = crypto.encrypt(value.trim()) }
    }

    override suspend fun getApiKey(provider: Provider): String {
        val encrypted = context.aiStudioDataStore.data.first()[keyFor(provider)] ?: ""
        return crypto.decrypt(encrypted)
    }

    private fun keyFor(provider: Provider) = when (provider) {
        Provider.OPENAI -> Keys.openAiKey
        Provider.GEMINI -> Keys.geminiKey
        Provider.ANTHROPIC -> Keys.anthropicKey
        Provider.OPENAI_COMPATIBLE -> Keys.compatibleKey
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String?, default: T): T =
        value?.let { runCatching { enumValueOf<T>(it) }.getOrNull() } ?: default
}
