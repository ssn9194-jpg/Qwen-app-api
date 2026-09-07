package com.aistudio.superapp.ui.imagegen

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aistudio.superapp.domain.model.ModelConfig
import com.aistudio.superapp.domain.repository.AiRepository
import com.aistudio.superapp.domain.repository.GalleryRepository
import com.aistudio.superapp.ui.i18n.StudioStrings
import com.aistudio.superapp.utils.saveImageToGallery
import com.aistudio.superapp.utils.shareUri
import kotlinx.coroutines.launch

data class PromptTemplate(val name: String, val category: String, val modifier: String, val full: String)

private val templates = listOf(
    PromptTemplate("Cinematic Lighting", "Photography", ", volumetric lighting, dramatic shadows, 35mm lens, depth of field, 8k", "A cinematic portrait in a rain-soaked city, volumetric lighting, dramatic shadows, 35mm lens, shallow depth of field, 8k"),
    PromptTemplate("Digital Art", "Illustration", ", vibrant colors, trending on ArtStation, detailed concept art illustration", "A futuristic floating city, vibrant colors, trending on ArtStation, detailed concept art illustration"),
    PromptTemplate("Photorealistic", "Photography", ", 8k UHD, Hasselblad RAW photograph, hyperrealistic textures, natural light", "A photorealistic mountain cabin at sunrise, 8k UHD, Hasselblad RAW photograph, hyperrealistic textures"),
    PromptTemplate("Cyberpunk", "Sci-Fi", ", cyberpunk, neon rain, holographic signage, cinematic atmosphere", "A cyberpunk street market at midnight, neon rain, holographic signage, cinematic atmosphere"),
    PromptTemplate("Anime & Manga", "Illustration", ", anime key visual, expressive line art, cel shading, detailed background", "An anime hero overlooking a coastal city, expressive line art, cel shading, detailed background"),
    PromptTemplate("3D Isometric Render", "3D", ", 3D isometric render, soft global illumination, clean geometry, octane render", "A compact AI research lab, 3D isometric render, soft global illumination, clean geometry"),
    PromptTemplate("Fantasy Concept Art", "Fantasy", ", epic fantasy concept art, atmospheric perspective, intricate details, painterly", "An ancient sky temple above clouds, epic fantasy concept art, atmospheric perspective, intricate details"),
    PromptTemplate("Minimalist Watercolor", "Traditional", ", minimalist watercolor, soft washes, negative space, textured paper", "A quiet Persian garden, minimalist watercolor, soft washes, negative space, textured paper"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageGenScreen(
    strings: StudioStrings,
    models: List<ModelConfig>,
    selectedModelId: String,
    aiRepo: AiRepository,
    galleryRepo: GalleryRepository,
    onSelectModel: (String) -> Unit,
    onSendToChat: (String) -> Unit,
) {
    val vm: ImageGenViewModel = viewModel(factory = ImageGenViewModel.factory(aiRepo, galleryRepo))
    val state by vm.state.collectAsState()
    val images by vm.images.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prompt by rememberSaveable { mutableStateOf("") }
    var aspect by rememberSaveable { mutableStateOf("1:1") }
    var quality by rememberSaveable { mutableStateOf("Standard") }
    var showTemplates by remember { mutableStateOf(false) }
    var zoomSource by remember { mutableStateOf<String?>(null) }
    val imageModels = models.filter { it.supportsImageGeneration }
    val selected = imageModels.firstOrNull { it.id == selectedModelId } ?: imageModels.firstOrNull()

    Scaffold(
        topBar = { TopAppBar(title = { Text(strings.images) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { showTemplates = true }, icon = { Icon(Icons.Default.Palette, null) }, text = { Text(strings.promptTemplates) })
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 6.dp)) {
                items(templates) { t -> SuggestionChip(onClick = { prompt += t.modifier }, label = { Text(t.name) }) }
            }
            OutlinedTextField(prompt, { prompt = it }, label = { Text(strings.prompt) }, modifier = Modifier.fillMaxWidth(), minLines = 3, maxLines = 7)
            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("1:1", "16:9", "9:16", "4:3").forEach { FilterChip(selected = aspect == it, onClick = { aspect = it }, label = { Text(it) }) }
                Spacer(Modifier.width(8.dp))
                listOf("Standard", "HD", "4K").forEach { FilterChip(selected = quality == it, onClick = { quality = it }, label = { Text(it) }) }
            }
            ModelDropDown(imageModels, selected?.id, strings.imageModel, onSelectModel)
            Button(
                onClick = { selected?.let { vm.generate(prompt, aspect, quality, it) } },
                enabled = selected != null && prompt.isNotBlank() && !state.generating,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.generating) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.AutoAwesome, null)
                Spacer(Modifier.width(8.dp)); Text(strings.generate)
            }
            Spacer(Modifier.height(12.dp))
            Text(strings.gallery, style = MaterialTheme.typography.titleMedium)
            LazyRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
                items(images, key = { it.id }) { image ->
                    Card(Modifier.width(260.dp)) {
                        AsyncImage(image.source, null, Modifier.fillMaxWidth().height(220.dp).clickable { zoomSource = image.source })
                        Text(image.prompt, Modifier.padding(10.dp), maxLines = 2)
                        Row(Modifier.fillMaxWidth().padding(4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            IconButton(onClick = { onSendToChat(image.source) }) { Icon(Icons.Default.Send, strings.sendToChat) }
                            IconButton(onClick = { scope.launch { saveImageToGallery(context, aiRepo.downloadBytes(image.source)) } }) { Icon(Icons.Default.Download, strings.download) }
                            IconButton(onClick = {
                                scope.launch {
                                    val uri = saveImageToGallery(context, aiRepo.downloadBytes(image.source))
                                    if (uri != null) shareUri(context, uri)
                                }
                            }) { Icon(Icons.Default.Share, strings.share) }
                        }
                    }
                }
            }
        }
    }

    if (showTemplates) ModalBottomSheet(onDismissRequest = { showTemplates = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(strings.promptTemplates, style = MaterialTheme.typography.headlineSmall)
            templates.groupBy { it.category }.forEach { (category, group) ->
                Text(category, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
                group.forEach { t ->
                    ListItem(
                        headlineContent = { Text(t.name) }, supportingContent = { Text(t.modifier, maxLines = 2) },
                        leadingContent = { Icon(Icons.Default.AutoFixHigh, null) },
                        trailingContent = {
                            Row {
                                TextButton(onClick = { prompt += t.modifier; showTemplates = false }) { Text("+") }
                                TextButton(onClick = { prompt = t.full; showTemplates = false }) { Text(strings.fullPrompt) }
                            }
                        },
                    )
                }
            }
        }
    }

    zoomSource?.let { source ->
        Dialog(onDismissRequest = { zoomSource = null }) {
            Surface(shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth().fillMaxHeight(.85f)) {
                Box { AsyncImage(source, null, Modifier.fillMaxSize()); IconButton(onClick = { zoomSource = null }, Modifier.align(Alignment.TopEnd)) { Icon(Icons.Default.Close, null) } }
            }
        }
    }
    state.error?.let { AlertDialog(onDismissRequest = vm::dismissError, confirmButton = { TextButton(onClick = vm::dismissError) { Text(strings.ok) } }, title = { Text(strings.error) }, text = { Text(it) }) }
}

@Composable
private fun ModelDropDown(models: List<ModelConfig>, selectedId: String?, fallbackLabel: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val model = models.firstOrNull { it.id == selectedId }
    Box {
        OutlinedButton(onClick = { expanded = true }) { Icon(Icons.Default.Tune, null); Spacer(Modifier.width(6.dp)); Text(model?.displayName ?: fallbackLabel) }
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            models.forEach { DropdownMenuItem({ Text(it.displayName) }, onClick = { onSelect(it.id); expanded = false }) }
        }
    }
}
