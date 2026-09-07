package com.aistudio.superapp.di

import android.content.Context
import com.aistudio.superapp.data.local.AppDatabase
import com.aistudio.superapp.data.network.KtorAiGateway
import com.aistudio.superapp.data.repository.*
import com.aistudio.superapp.data.settings.SettingsRepositoryImpl
import com.aistudio.superapp.domain.repository.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

interface AppContainer {
    val settingsRepository: SettingsRepository
    val modelRegistry: ModelRegistryRepository
    val aiRepository: AiRepository
    val chatRepository: ChatRepository
    val galleryRepository: GalleryRepository
}

class DefaultAppContainer(context: Context) : AppContainer {
    private val appContext = context.applicationContext
    private val database = AppDatabase.create(appContext)
    override val settingsRepository: SettingsRepository = SettingsRepositoryImpl(appContext)
    override val modelRegistry: ModelRegistryRepository = ModelRegistryRepositoryImpl(database.modelDao())
    private val gateway = KtorAiGateway(appContext, settingsRepository)
    override val aiRepository: AiRepository = AiRepositoryImpl(gateway)
    override val chatRepository: ChatRepository = ChatRepositoryImpl(database.sessionDao(), database.messageDao(), aiRepository)
    override val galleryRepository: GalleryRepository = GalleryRepositoryImpl(database.galleryDao())

    init {
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { modelRegistry.ensureDefaults() }
    }
}
