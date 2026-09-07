package com.aistudio.superapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.aistudio.superapp.di.AppContainer
import com.aistudio.superapp.domain.model.AppLanguage
import com.aistudio.superapp.domain.model.AppSettings
import com.aistudio.superapp.ui.chat.ChatScreen
import com.aistudio.superapp.ui.i18n.stringsFor
import com.aistudio.superapp.ui.imagegen.ImageGenScreen
import com.aistudio.superapp.ui.photo.PhotoEditorScreen
import com.aistudio.superapp.ui.settings.SettingsScreen
import com.aistudio.superapp.ui.theme.StudioTheme
import com.aistudio.superapp.ui.vision.VisionScreen
import com.aistudio.superapp.ui.voice.VoiceScreen
import kotlinx.coroutines.launch

private enum class Tab { CHAT, IMAGES, VISION, PHOTO, VOICE, SETTINGS }

@Composable
fun AIStudioRoot(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsState(initial = AppSettings())
    val models by container.modelRegistry.models.collectAsState(initial = emptyList())
    val strings = stringsFor(settings.language)
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(Tab.CHAT) }
    var pendingChatImage by remember { mutableStateOf<String?>(null) }
    val direction = if (settings.language == AppLanguage.PERSIAN) LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides direction) {
        StudioTheme(settings.theme) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(tab == Tab.CHAT, { tab = Tab.CHAT }, icon = { Icon(Icons.Default.Chat, null) }, label = { Text(strings.chat) })
                        NavigationBarItem(tab == Tab.IMAGES, { tab = Tab.IMAGES }, icon = { Icon(Icons.Default.Image, null) }, label = { Text(strings.images) })
                        NavigationBarItem(tab == Tab.VISION, { tab = Tab.VISION }, icon = { Icon(Icons.Default.Visibility, null) }, label = { Text(strings.vision) })
                        NavigationBarItem(tab == Tab.PHOTO, { tab = Tab.PHOTO }, icon = { Icon(Icons.Default.PhotoLibrary, null) }, label = { Text(strings.photo) })
                        NavigationBarItem(tab == Tab.VOICE, { tab = Tab.VOICE }, icon = { Icon(Icons.Default.GraphicEq, null) }, label = { Text(strings.voice) })
                        NavigationBarItem(tab == Tab.SETTINGS, { tab = Tab.SETTINGS }, icon = { Icon(Icons.Default.Settings, null) }, label = { Text(strings.settings) })
                    }
                },
            ) { rootPadding ->
                Box(Modifier.fillMaxSize().padding(rootPadding)) {
                    when (tab) {
                        Tab.CHAT -> ChatScreen(
                            strings, models, settings.selectedChatModelId,
                            container.chatRepository, container.aiRepository,
                            onSelectModel = { scope.launch { container.settingsRepository.setSelectedChatModel(it) } },
                            pendingRemoteImage = pendingChatImage,
                            onConsumedRemoteImage = { pendingChatImage = null },
                        )
                        Tab.IMAGES -> ImageGenScreen(
                            strings, models, settings.selectedImageModelId, container.aiRepository, container.galleryRepository,
                            onSelectModel = { scope.launch { container.settingsRepository.setSelectedImageModel(it) } },
                            onSendToChat = { source -> pendingChatImage = source; tab = Tab.CHAT },
                        )
                        Tab.VISION -> VisionScreen(strings, models, container.aiRepository)
                        Tab.PHOTO -> PhotoEditorScreen(strings, models, container.aiRepository)
                        Tab.VOICE -> VoiceScreen(strings, models, container.aiRepository, settings.language == AppLanguage.PERSIAN)
                        Tab.SETTINGS -> SettingsScreen(strings, container.settingsRepository, container.modelRegistry, container.aiRepository)
                    }
                }
            }
        }
    }
}
