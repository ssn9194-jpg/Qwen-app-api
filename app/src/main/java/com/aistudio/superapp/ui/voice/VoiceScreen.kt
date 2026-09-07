package com.aistudio.superapp.ui.voice

import android.Manifest
import android.content.pm.PackageManager
import android.speech.tts.TextToSpeech
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aistudio.superapp.domain.model.ModelConfig
import com.aistudio.superapp.domain.repository.AiRepository
import com.aistudio.superapp.ui.components.Waveform
import com.aistudio.superapp.ui.i18n.StudioStrings
import com.aistudio.superapp.utils.AudioRecorder
import kotlinx.coroutines.delay
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoiceScreen(strings: StudioStrings, models: List<ModelConfig>, aiRepo: AiRepository, persian: Boolean) {
    val vm: VoiceViewModel = viewModel(factory = VoiceViewModel.factory(aiRepo))
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    val amplitudes = remember { mutableStateListOf<Float>() }
    val audioModels = models.filter { it.supportsAudio }
    var selectedId by rememberSaveable { mutableStateOf(audioModels.firstOrNull()?.id.orEmpty()) }
    val selected = audioModels.firstOrNull { it.id == selectedId } ?: audioModels.firstOrNull()
    var lastFile by remember { mutableStateOf<java.io.File?>(null) }

    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(Unit) {
        val engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) tts?.language = if (persian) Locale("fa", "IR") else Locale.US
        }
        tts = engine
        onDispose { engine.stop(); engine.shutdown() }
    }

    fun start() { runCatching { recorder.start() }.onSuccess { recording = true; amplitudes.clear() } }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) start() }
    LaunchedEffect(recording) {
        while (recording) { amplitudes += recorder.amplitude(); if (amplitudes.size > 64) amplitudes.removeAt(0); delay(90) }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(strings.voice) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Waveform(amplitudes)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (recording) { recording = false; lastFile = recorder.stop() }
                    else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) start()
                    else permission.launch(Manifest.permission.RECORD_AUDIO)
                }) { Icon(if (recording) Icons.Default.Stop else Icons.Default.Mic, null); Text(if (recording) strings.stopRecording else strings.startRecording) }
                Button(onClick = { val f = lastFile; if (f != null && selected != null) vm.transcribe(f, selected) }, enabled = lastFile != null && selected != null && !state.loading) {
                    if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp) else Icon(Icons.Default.GraphicEq, null)
                    Text(strings.transcribe)
                }
            }
            AudioModelSelector(audioModels, selected?.id, strings.sttModel) { selectedId = it }
            OutlinedTextField(state.transcript, vm::updateTranscript, modifier = Modifier.fillMaxWidth().weight(1f), label = { Text(strings.transcribe) })
            Button(onClick = {
                tts?.language = if (persian) Locale("fa", "IR") else Locale.US
                tts?.speak(state.transcript, TextToSpeech.QUEUE_FLUSH, null, "ai-studio-tts")
            }, enabled = state.transcript.isNotBlank(), modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.VolumeUp, null); Text(strings.speak) }
        }
    }
    state.error?.let { AlertDialog(onDismissRequest = vm::clearError, confirmButton = { TextButton(onClick = vm::clearError) { Text(strings.ok) } }, title = { Text(strings.error) }, text = { Text(it) }) }
}

@Composable
private fun AudioModelSelector(models: List<ModelConfig>, selected: String?, fallbackLabel: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text(models.firstOrNull { it.id == selected }?.displayName ?: fallbackLabel) }; DropdownMenu(open, { open = false }) { models.forEach { DropdownMenuItem({ Text(it.displayName) }, onClick = { onSelect(it.id); open = false }) } } }
}
