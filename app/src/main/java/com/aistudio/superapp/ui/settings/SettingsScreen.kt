package com.aistudio.superapp.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.superapp.BuildConfig
import com.aistudio.superapp.domain.model.*
import com.aistudio.superapp.domain.repository.*
import com.aistudio.superapp.ui.i18n.StudioStrings

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    strings: StudioStrings,
    settingsRepo: SettingsRepository,
    modelsRepo: ModelRegistryRepository,
    aiRepo: AiRepository,
) {
    val vm: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(settingsRepo, modelsRepo, aiRepo))
    val state by vm.state.collectAsState()
    val settings by vm.settings.collectAsState()
    val models by vm.models.collectAsState()
    var editing by remember { mutableStateOf<ModelConfig?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(topBar = { TopAppBar(title = { Text(strings.settings) }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                SectionCard(strings.language) {
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(selected = settings.language == AppLanguage.PERSIAN, onClick = { vm.setLanguage(AppLanguage.PERSIAN) }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("فارسی") }
                        SegmentedButton(selected = settings.language == AppLanguage.ENGLISH, onClick = { vm.setLanguage(AppLanguage.ENGLISH) }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("English") }
                    }
                }
            }
            item {
                SectionCard(strings.theme) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode -> FilterChip(selected = settings.theme == mode, onClick = { vm.setTheme(mode) }, label = { Text(themeLabel(mode, strings)) }) }
                    }
                }
            }
            item {
                SectionCard(strings.apiKeys) {
                    Provider.entries.forEach { provider ->
                        OutlinedTextField(
                            state.apiKeys[provider].orEmpty(), { vm.editKey(provider, it) },
                            label = { Text(provider.name.replace('_', ' ')) }, modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = { IconButton(onClick = { vm.saveKey(provider) }) { Icon(Icons.Default.Save, strings.save) } },
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    Text(strings.securityNote, style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(strings.models, style = MaterialTheme.typography.headlineSmall)
                    Button(onClick = { editing = null; showEditor = true }) { Icon(Icons.Default.Add, null); Text(strings.addModel) }
                }
            }
            items(models, key = { it.id }) { model ->
                ModelCard(model, state.pingResults[model.id], state.pinging.contains(model.id), strings,
                    onEdit = { editing = model; showEditor = true }, onDelete = { vm.delete(model) }, onPing = { vm.ping(model) })
            }
        }
    }

    if (showEditor) ModelEditorDialog(strings, editing, onDismiss = { showEditor = false }, onSave = { vm.upsert(it); showEditor = false })
    state.error?.let { AlertDialog(onDismissRequest = vm::clearError, confirmButton = { TextButton(onClick = vm::clearError) { Text(strings.ok) } }, title = { Text(strings.error) }, text = { Text(it) }) }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.fillMaxWidth().padding(14.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); Spacer(Modifier.height(10.dp)); content() } }
}

@Composable
private fun ModelCard(
    model: ModelConfig, ping: PingResult?, pinging: Boolean, strings: StudioStrings,
    onEdit: () -> Unit, onDelete: () -> Unit, onPing: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) { Text(model.displayName, style = MaterialTheme.typography.titleMedium); Text(model.id, style = MaterialTheme.typography.bodySmall); Text(model.baseUrl, style = MaterialTheme.typography.bodySmall) }
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null) }
                if (!model.builtIn) IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null) }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (model.supportsChat) AssistChip({}, { Text(strings.chatCapability) })
                if (model.supportsVision) AssistChip({}, { Text(strings.visionCapability) })
                if (model.supportsImageGeneration) AssistChip({}, { Text(strings.imageCapability) })
                if (model.supportsAudio) AssistChip({}, { Text(strings.audioCapability) })
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                OutlinedButton(onClick = onPing, enabled = !pinging) { if (pinging) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp) else Icon(Icons.Default.NetworkPing, null); Text(strings.test) }
                Spacer(Modifier.width(10.dp))
                ping?.let { Text("${if (it.ok) "●" else "○"} ${it.latencyMs} ms · ${it.message}") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ModelEditorDialog(strings: StudioStrings, existing: ModelConfig?, onDismiss: () -> Unit, onSave: (ModelConfig) -> Unit) {
    var id by remember(existing) { mutableStateOf(existing?.id.orEmpty()) }
    var name by remember(existing) { mutableStateOf(existing?.displayName.orEmpty()) }
    var base by remember(existing) { mutableStateOf(existing?.baseUrl ?: "http://10.0.2.2:11434") }
    var provider by remember(existing) { mutableStateOf(existing?.provider ?: Provider.OPENAI_COMPATIBLE) }
    var chat by remember(existing) { mutableStateOf(existing?.supportsChat ?: true) }
    var vision by remember(existing) { mutableStateOf(existing?.supportsVision ?: false) }
    var image by remember(existing) { mutableStateOf(existing?.supportsImageGeneration ?: false) }
    var audio by remember(existing) { mutableStateOf(existing?.supportsAudio ?: false) }
    var providerOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) strings.addModel else strings.models) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(id, { id = it }, enabled = existing == null, label = { Text(strings.modelId) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(name, { name = it }, label = { Text(strings.modelName) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(base, { base = it }, label = { Text(strings.baseUrl) }, modifier = Modifier.fillMaxWidth())
                Box { OutlinedButton(onClick = { providerOpen = true }) { Text(provider.name) }; DropdownMenu(providerOpen, { providerOpen = false }) { Provider.entries.forEach { p -> DropdownMenuItem({ Text(p.name) }, onClick = { provider = p; providerOpen = false }) } } }
                CapabilitySwitch(strings.chatCapability, chat) { chat = it }
                CapabilitySwitch(strings.visionCapability, vision) { vision = it }
                CapabilitySwitch(strings.imageCapability, image) { image = it }
                CapabilitySwitch(strings.audioCapability, audio) { audio = it }
            }
        },
        confirmButton = { TextButton(onClick = { if (id.isNotBlank() && name.isNotBlank() && base.isNotBlank()) onSave(ModelConfig(id.trim(), name.trim(), provider, base.trimEnd('/'), chat, vision, image, audio, true, existing?.builtIn ?: false)) }) { Text(strings.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(strings.cancel) } },
    )
}

private fun themeLabel(mode: ThemeMode, strings: StudioStrings): String = when (mode) {
    ThemeMode.SYSTEM -> strings.systemTheme
    ThemeMode.LIGHT -> strings.lightTheme
    ThemeMode.DARK -> strings.darkTheme
    ThemeMode.AMOLED -> strings.amoledTheme
    ThemeMode.DYNAMIC -> strings.dynamicTheme
}

@Composable
private fun CapabilitySwitch(label: String, value: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { Text(label); Switch(value, onChange) }
}
