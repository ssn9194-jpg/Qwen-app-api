package com.aistudio.superapp.ui.vision

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aistudio.superapp.domain.model.ChatAttachment
import com.aistudio.superapp.domain.model.ModelConfig
import com.aistudio.superapp.domain.repository.AiRepository
import com.aistudio.superapp.ui.components.RichMessageText
import com.aistudio.superapp.ui.i18n.StudioStrings
import com.aistudio.superapp.utils.createCameraUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisionScreen(
    strings: StudioStrings,
    models: List<ModelConfig>,
    aiRepo: AiRepository
) {
    val vm: VisionViewModel =
        viewModel(
            factory = VisionViewModel.factory(aiRepo)
        )

    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val visionModels =
        models.filter {
            it.supportsVision
        }

    var selectedId by rememberSaveable {
        mutableStateOf(
            visionModels
                .firstOrNull()
                ?.id
                .orEmpty()
        )
    }

    var prompt by rememberSaveable {
        mutableStateOf("")
    }

    var image by remember {
        mutableStateOf<ChatAttachment?>(null)
    }

    var cameraUri by remember {
        mutableStateOf<Uri?>(null)
    }

    val picker =
        rememberLauncherForActivityResult(
            ActivityResultContracts.PickVisualMedia()
        ) { uri ->

            uri?.let {
                image =
                    ChatAttachment(
                        uri = it.toString(),
                        mimeType =
                            context.contentResolver
                                .getType(it)
                                ?: "image/jpeg",
                        displayName = "vision"
                    )
            }
        }

    val camera =
        rememberLauncherForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { ok ->

            if (ok) {
                cameraUri?.let {
                    image =
                        ChatAttachment(
                            uri = it.toString(),
                            mimeType = "image/jpeg",
                            displayName = "camera"
                        )
                }
            }
        }

    val model =
        visionModels.firstOrNull {
            it.id == selectedId
        } ?: visionModels.firstOrNull()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(strings.vision)
                }
            )
        }
    ) { padding ->

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(14.dp)
                    .verticalScroll(
                        rememberScrollState()
                    ),

            verticalArrangement =
                Arrangement.spacedBy(12.dp)
        ) {

            Row(
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {

                OutlinedButton(
                    onClick = {
                        picker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts
                                    .PickVisualMedia
                                    .ImageOnly
                            )
                        )
                    }
                ) {

                    Icon(
                        Icons.Default.Image,
                        contentDescription = null
                    )

                    Text(strings.chooseImage)
                }

                OutlinedButton(
                    onClick = {
                        cameraUri =
                            createCameraUri(context)

                        camera.launch(
                            cameraUri!!
                        )
                    }
                ) {

                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null
                    )

                    Text(strings.camera)
                }
            }

            image?.let { selectedImage ->

                AsyncImage(
                    model = selectedImage.uri,
                    contentDescription = null,

                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(
                                max = 360.dp
                            )
                )
            }

            OutlinedTextField(
                value = prompt,

                onValueChange = {
                    prompt = it
                },

                label = {
                    Text(strings.visionHint)
                },

                modifier =
                    Modifier.fillMaxWidth(),

                minLines = 3
            )

            VisionModelSelector(
                models = visionModels,
                selected = model?.id,
                fallbackLabel =
                    strings.visionModel,
                onSelect = {
                    selectedId = it
                }
            )

            Button(
                onClick = {

                    val selectedImage =
                        image

                    val selectedModel =
                        model

                    if (
                        selectedImage != null &&
                        selectedModel != null
                    ) {
                        vm.analyze(
                            prompt =
                                prompt.ifBlank {
                                    "Analyze this image in detail."
                                },

                            attachment =
                                selectedImage,

                            model =
                                selectedModel
                        )
                    }
                },

                enabled =
                    image != null &&
                        model != null &&
                        !state.loading,

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                if (state.loading) {

                    CircularProgressIndicator(
                        modifier =
                            Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )

                } else {

                    Icon(
                        Icons.Default.Visibility,
                        contentDescription = null
                    )
                }

                Spacer(
                    Modifier.width(8.dp)
                )

                Text(strings.analyze)
            }

            if (
                state.result
                    .isNotBlank()
            ) {

                Surface(
                    tonalElevation = 2.dp,
                    shape =
                        MaterialTheme
                            .shapes
                            .large
                ) {

                    Box(
                        Modifier.padding(14.dp)
                    ) {

                        RichMessageText(
                            state.result
                        )
                    }
                }
            }
        }
    }

    state.error?.let { error ->

        AlertDialog(
            onDismissRequest =
                vm::clearError,

            confirmButton = {

                TextButton(
                    onClick =
                        vm::clearError
                ) {
                    Text(strings.ok)
                }
            },

            title = {
                Text(strings.error)
            },

            text = {
                Text(error)
            }
        )
    }
}

@Composable
private fun VisionModelSelector(
    models: List<ModelConfig>,
    selected: String?,
    fallbackLabel: String,
    onSelect: (String) -> Unit
) {
    var open by remember {
        mutableStateOf(false)
    }

    val selectedModel =
        models.firstOrNull {
            it.id == selected
        }

    Box {

        OutlinedButton(
            onClick = {
                open = true
            }
        ) {

            Text(
                selectedModel
                    ?.displayName
                    ?: fallbackLabel
            )
        }

        DropdownMenu(
            expanded = open,
            onDismissRequest = {
                open = false
            }
        ) {

            models.forEach { model ->

                DropdownMenuItem(
                    text = {
                        Text(
                            model.displayName
                        )
                    },

                    onClick = {
                        onSelect(model.id)
                        open = false
                    }
                )
            }
        }
    }
}    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { image = ChatAttachment(it.toString(), context.contentResolver.getType(it) ?: "image/jpeg", "vision") }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { image = ChatAttachment(it.toString(), "image/jpeg", "camera") } }
    val model = visionModels.firstOrNull { it.id == selectedId } ?: visionModels.firstOrNull()

    Scaffold(topBar = { TopAppBar(title = { Text(strings.vision) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(14.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Default.Image, null); Text(strings.chooseImage) }
                OutlinedButton(onClick = { cameraUri = createCameraUri(context); camera.launch(cameraUri!!) }) { Icon(Icons.Default.PhotoCamera, null); Text(strings.camera) }
            }
            image?.let { AsyncImage(it.uri, null, Modifier.fillMaxWidth().heightIn(max = 360.dp)) }
            OutlinedTextField(prompt, { prompt = it }, label = { Text(strings.visionHint) }, modifier = Modifier.fillMaxWidth(), minLines = 3)
            VisionModelSelector(visionModels, model?.id, strings.visionModel) { selectedId = it }
            Button(
                onClick = { if (image != null && model != null) vm.analyze(prompt.ifBlank { "Analyze this image in detail." }, image!!, model) },
                enabled = image != null && model != null && !state.loading, modifier = Modifier.fillMaxWidth(),
            ) { if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.Visibility, null); Spacer(Modifier.width(8.dp)); Text(strings.analyze) }
            if (state.result.isNotBlank()) Surface(tonalElevation = 2.dp, shape = MaterialTheme.shapes.large) { Box(Modifier.padding(14.dp)) { RichMessageText(state.result) } }
        }
    }
    state.error?.let { AlertDialog(onDismissRequest = vm::clearError, confirmButton = { TextButton(onClick = vm::clearError) { Text(strings.ok) } }, title = { Text(strings.error) }, text = { Text(it) }) }
}

@Composable
private fun VisionModelSelector(models: List<ModelConfig>, selected: String?, fallbackLabel: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text(models.firstOrNull { it.id == selected }?.displayName ?: fallbackLabel) }; DropdownMenu(open, { open = false }) { models.forEach { DropdownMenuItem({ Text(it.displayName) }, onClick = { onSelect(it.id); open = false }) } } }
}
