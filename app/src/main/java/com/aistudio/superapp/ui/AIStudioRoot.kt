package com.aistudio.superapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

private enum class Tab {
    CHAT,
    IMAGES,
    VISION,
    PHOTO,
    VOICE,
    SETTINGS
}

@Composable
fun AIStudioRoot(container: AppContainer) {
    val settings by container.settingsRepository.settings.collectAsState(
        initial = AppSettings()
    )

    val models by container.modelRegistry.models.collectAsState(
        initial = emptyList()
    )

    val strings = stringsFor(settings.language)
    val scope = rememberCoroutineScope()

    var tab by rememberSaveable {
        mutableStateOf(Tab.CHAT)
    }

    var pendingChatImage by remember {
        mutableStateOf<String?>(null)
    }

    val direction =
        if (settings.language == AppLanguage.PERSIAN) {
            LayoutDirection.Rtl
        } else {
            LayoutDirection.Ltr
        }

    CompositionLocalProvider(
        androidx.compose.ui.platform.LocalLayoutDirection provides direction
    ) {
        StudioTheme(settings.theme) {
            Scaffold(
                bottomBar = {
                    NavigationBar {
                        NavigationBarItem(
                            selected = tab == Tab.CHAT,
                            onClick = { tab = Tab.CHAT },
                            icon = {
                                Icon(
                                    Icons.Default.Chat,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(strings.chat)
                            }
                        )

                        NavigationBarItem(
                            selected = tab == Tab.IMAGES,
                            onClick = { tab = Tab.IMAGES },
                            icon = {
                                Icon(
                                    Icons.Default.Image,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(strings.images)
                            }
                        )

                        NavigationBarItem(
                            selected = tab == Tab.VISION,
                            onClick = { tab = Tab.VISION },
                            icon = {
                                Icon(
                                    Icons.Default.Visibility,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(strings.vision)
                            }
                        )

                        NavigationBarItem(
                            selected = tab == Tab.PHOTO,
                            onClick = { tab = Tab.PHOTO },
                            icon = {
                                Icon(
                                    Icons.Default.PhotoLibrary,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(strings.photo)
                            }
                        )

                        NavigationBarItem(
                            selected = tab == Tab.VOICE,
                            onClick = { tab = Tab.VOICE },
                            icon = {
                                Icon(
                                    Icons.Default.GraphicEq,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(strings.voice)
                            }
                        )

                        NavigationBarItem(
                            selected = tab == Tab.SETTINGS,
                            onClick = { tab = Tab.SETTINGS },
                            icon = {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null
                                )
                            },
                            label = {
                                Text(strings.settings)
                            }
                        )
                    }
                }
            ) { rootPadding ->

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(rootPadding)
                ) {
                    when (tab) {

                        Tab.CHAT -> {
                            ChatScreen(
                                strings = strings,
                                models = models,
                                selectedModelId = settings.selectedChatModelId,
                                chatRepo = container.chatRepository,
                                aiRepo = container.aiRepository,

                                onSelectModel = { modelId ->
                                    scope.launch {
                                        container.settingsRepository
                                            .setSelectedChatModel(modelId)
                                    }
                                },

                                pendingRemoteImage = pendingChatImage,

                                onConsumedRemoteImage = {
                                    pendingChatImage = null
                                }
                            )
                        }

                        Tab.IMAGES -> {
                            ImageGenScreen(
                                strings = strings,
                                models = models,
                                selectedModelId = settings.selectedImageModelId,
                                aiRepo = container.aiRepository,
                                galleryRepo = container.galleryRepository,

                                onSelectModel = { modelId ->
                                    scope.launch {
                                        container.settingsRepository
                                            .setSelectedImageModel(modelId)
                                    }
                                },

                                onSendToChat = { source ->
                                    pendingChatImage = source
                                    tab = Tab.CHAT
                                }
                            )
                        }

                        Tab.VISION -> {
                            VisionScreen(
                                strings,
                                models,
                                container.aiRepository
                            )
                        }

                        Tab.PHOTO -> {
                            PhotoEditorScreen(
                                strings,
                                models,
                                container.aiRepository
                            )
                        }

                        Tab.VOICE -> {
                            VoiceScreen(
                                strings,
                                models,
                                container.aiRepository,
                                settings.language == AppLanguage.PERSIAN
                            )
                        }

                        Tab.SETTINGS -> {
                            SettingsScreen(
                                strings,
                                container.settingsRepository,
                                container.modelRegistry,
                                container.aiRepository
                            )
                        }
                    }
                }
            }
        }
    }
}
