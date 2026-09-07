package com.aistudio.superapp.ui.chat

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aistudio.superapp.domain.model.*
import com.aistudio.superapp.domain.repository.AiRepository
import com.aistudio.superapp.domain.repository.ChatRepository
import com.aistudio.superapp.ui.components.*
import com.aistudio.superapp.ui.i18n.StudioStrings
import com.aistudio.superapp.utils.AudioRecorder
import com.aistudio.superapp.utils.createCameraUri
import com.aistudio.superapp.utils.writeTextUri
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    strings: StudioStrings,
    models: List<ModelConfig>,
    selectedModelId: String,
    chatRepo: ChatRepository,
    aiRepo: AiRepository,
    onSelectModel: (String) -> Unit,
    pendingRemoteImage: String?,
    onConsumedRemoteImage: () -> Unit,
) {
    val vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(chatRepo, aiRepo))
    val state by vm.state.collectAsState()
    val messages by vm.messages.collectAsState()
    val sessions by vm.sessions.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val focusRequester = remember { FocusRequester() }
    var text by rememberSaveable { mutableStateOf("") }
    var attachment by remember { mutableStateOf<ChatAttachment?>(null) }
    var showSessions by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var menuExpanded by remember { mutableStateOf(false) }
    var exportContent by remember { mutableStateOf<String?>(null) }
    var exportMime by remember { mutableStateOf("text/markdown") }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    val selectedModel = models.firstOrNull { it.id == selectedModelId && it.supportsChat }
        ?: models.firstOrNull { it.supportsChat }

    val createDocument = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(exportMime)) { uri ->
        if (uri != null) exportContent?.let { writeTextUri(context, uri, it) }
        exportContent = null
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            val mime = context.contentResolver.getType(it) ?: "text/plain"
            attachment = ChatAttachment(it.toString(), mime, it.lastPathSegment ?: "document")
        }
    }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            runCatching { context.contentResolver.takePersistableUriPermission(it, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            attachment = ChatAttachment(it.toString(), context.contentResolver.getType(it) ?: "image/jpeg", "photo")
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) cameraUri?.let { attachment = ChatAttachment(it.toString(), "image/jpeg", "camera") }
    }

    val recorder = remember { AudioRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    val amplitudes = remember { mutableStateListOf<Float>() }
    fun beginRecording() {
        runCatching { recorder.start() }.onSuccess { recording = true }.onFailure { }
    }
    val audioPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> if (granted) beginRecording() }

    LaunchedEffect(recording) {
        while (recording) {
            amplitudes += recorder.amplitude()
            if (amplitudes.size > 64) amplitudes.removeAt(0)
            delay(90)
        }
    }
    LaunchedEffect(state.speechDraft) {
        state.speechDraft?.let { text = if (text.isBlank()) it else "$text $it"; vm.consumeSpeechDraft() }
    }
    LaunchedEffect(pendingRemoteImage) {
        if (!pendingRemoteImage.isNullOrBlank()) {
            attachment = ChatAttachment(pendingRemoteImage, "image/png", "generated", remote = true)
            onConsumedRemoteImage()
        }
    }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density)
    LaunchedEffect(messages.size, messages.lastOrNull()?.content, imeBottom) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun doSend() {
        val model = selectedModel ?: return
        vm.send(text, attachment, model)
        text = ""; attachment = null
    }

    Box(
        Modifier.fillMaxSize().onPreviewKeyEvent { event ->
            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val cmd = event.isCtrlPressed || event.isMetaPressed
            when {
                cmd && event.key == Key.Enter -> { doSend(); true }
                cmd && event.key == Key.K -> { vm.clear(); Toast.makeText(context, strings.clear, Toast.LENGTH_SHORT).show(); true }
                else -> false
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AiBrainLogo(); Spacer(Modifier.width(8.dp)); Text(strings.chat)
                        }
                    },
                    navigationIcon = { IconButton(onClick = { showSessions = true }) { Icon(Icons.Default.Menu, strings.sessions) } },
                    actions = {
                        IconButton(onClick = { showSearch = true }) { Icon(Icons.Default.Search, strings.search) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, strings.more) }
                            DropdownMenu(menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                DropdownMenuItem({ Text(strings.summarize) }, onClick = { selectedModel?.let(vm::summarize); menuExpanded = false }, leadingIcon = { Icon(Icons.Default.Summarize, null) })
                                DropdownMenuItem({ Text(strings.clear) }, onClick = { vm.clear(); menuExpanded = false }, leadingIcon = { Icon(Icons.Default.DeleteSweep, null) })
                                DropdownMenuItem({ Text(strings.exportMarkdown) }, onClick = {
                                    menuExpanded = false; scope.launch { exportMime = "text/markdown"; exportContent = vm.markdown(); createDocument.launch("ai-studio-chat.md") }
                                }, leadingIcon = { Icon(Icons.Default.Description, null) })
                                DropdownMenuItem({ Text(strings.exportJson) }, onClick = {
                                    menuExpanded = false; scope.launch { exportMime = "application/json"; exportContent = vm.json(); createDocument.launch("ai-studio-chat.json") }
                                }, leadingIcon = { Icon(Icons.Default.DataObject, null) })
                            }
                        }
                    },
                )
            },
        ) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                ModelSelector(models.filter { it.supportsChat }, selectedModel?.id, strings.model, onSelectModel)
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(messages, key = { it.id }) { MessageBubble(it) }
                    if (state.isStreaming) item { TypingIndicator(selectedModel?.displayName ?: "AI", strings.typing) }
                }
                if (recording) Waveform(amplitudes, Modifier.padding(horizontal = 16.dp))
                attachment?.let { att ->
                    Surface(tonalElevation = 2.dp, modifier = Modifier.padding(horizontal = 12.dp)) {
                        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (att.mimeType.startsWith("image/")) AsyncImage(att.uri, contentDescription = null, modifier = Modifier.size(54.dp))
                            else Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(42.dp))
                            Spacer(Modifier.width(8.dp)); Text(att.displayName, Modifier.weight(1f))
                            IconButton(onClick = { attachment = null }) { Icon(Icons.Default.Close, null) }
                        }
                    }
                }
                Composer(
                    text = text, onTextChange = { text = it }, strings = strings, focusRequester = focusRequester,
                    streaming = state.isStreaming, onSend = ::doSend,
                    onPhoto = { photoPicker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    onDocument = { documentPicker.launch(arrayOf("text/plain", "text/markdown", "text/csv", "application/json")) },
                    onCamera = { cameraUri = createCameraUri(context); camera.launch(cameraUri!!) },
                    recording = recording,
                    onMic = {
                        if (recording) {
                            recording = false
                            val file = recorder.stop()
                            val audioModel = models.firstOrNull { it.supportsAudio }
                            if (file != null && audioModel != null) vm.transcribe(file, audioModel)
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginRecording()
                        else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    },
                )
            }
        }

        state.error?.let { err ->
            Snackbar(Modifier.align(Alignment.BottomCenter).padding(16.dp), action = { TextButton(onClick = vm::dismissError) { Text(strings.ok) } }) { Text(err) }
        }
    }

    if (showSessions) ModalBottomSheet(onDismissRequest = { showSessions = false }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(strings.sessions, style = MaterialTheme.typography.titleLarge)
            FilledTonalButton(onClick = { vm.newSession(); showSessions = false }) { Icon(Icons.Default.Add, null); Text(strings.newChat) }
        }
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            items(sessions, key = { it.id }) { s ->
                ListItem(headlineContent = { Text(s.title) }, modifier = Modifier.clickable { vm.selectSession(s.id); showSessions = false }, leadingContent = { Icon(Icons.Default.ChatBubbleOutline, null) })
            }
        }
    }

    if (showSearch) ModalBottomSheet(onDismissRequest = { showSearch = false }) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            OutlinedTextField(searchText, { searchText = it; vm.search(it) }, label = { Text(strings.searchAllChats) }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Default.Search, null) })
            Spacer(Modifier.height(8.dp))
            if (state.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                if (state.searchHits.isEmpty() && searchText.isNotBlank()) item { Text(strings.noResults, Modifier.padding(20.dp)) }
                items(state.searchHits, key = { it.messageId }) { hit ->
                    ListItem(
                        headlineContent = { Text(hit.sessionTitle) },
                        supportingContent = { HighlightedText(hit.content, searchText) },
                        modifier = Modifier.clickable { vm.selectSession(hit.sessionId); showSearch = false },
                    )
                }
            }
        }
    }

    state.summary?.let { summary ->
        AlertDialog(
            onDismissRequest = vm::dismissSummary,
            title = { Text(summary.suggestedTitle) },
            text = { Text(summary.summary) },
            confirmButton = { TextButton(onClick = vm::renameFromSummary) { Text(strings.rename) } },
            dismissButton = {
                Row {
                    TextButton(onClick = vm::insertSummary) { Text(strings.insertSummary) }
                    TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("summary", "${summary.suggestedTitle}\n\n${summary.summary}"))
                    }) { Text(strings.copy) }
                }
            },
        )
    }
}

@Composable
private fun ModelSelector(models: List<ModelConfig>, selectedId: String?, fallbackLabel: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val selected = models.firstOrNull { it.id == selectedId }
    Box(Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
        AssistChip(onClick = { expanded = true }, label = { Text(selected?.displayName ?: fallbackLabel) }, leadingIcon = { Icon(Icons.Default.AutoAwesome, null) })
        DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
            models.forEach { model -> DropdownMenuItem({ Text(model.displayName) }, onClick = { onSelect(model.id); expanded = false }) }
        }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage) {
    val user = message.role == "user"
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = if (user) MaterialTheme.colorScheme.primaryContainer else if (message.isNote) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.widthIn(max = 680.dp).fillMaxWidth(.92f),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                message.attachments.forEach { attachment ->
                    if (attachment.mimeType.startsWith("image/")) {
                        AsyncImage(attachment.uri, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp))
                    } else {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                            Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Description, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text(attachment.displayName, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
                if (message.content.isNotBlank()) RichMessageText(message.content)
            }
        }
    }
}

@Composable
private fun Composer(
    text: String, onTextChange: (String) -> Unit, strings: StudioStrings, focusRequester: FocusRequester,
    streaming: Boolean, onSend: () -> Unit, onPhoto: () -> Unit, onDocument: () -> Unit, onCamera: () -> Unit,
    recording: Boolean, onMic: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = onPhoto) { Icon(Icons.Default.Image, strings.gallery) }
                IconButton(onClick = onDocument) { Icon(Icons.Default.AttachFile, strings.attach) }
                IconButton(onClick = onCamera) { Icon(Icons.Default.PhotoCamera, strings.camera) }
                IconButton(onClick = onMic) { Icon(if (recording) Icons.Default.StopCircle else Icons.Default.Mic, strings.recording) }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                OutlinedTextField(
                    value = text, onValueChange = onTextChange, modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    placeholder = { Text(strings.messageHint) }, maxLines = 7,
                    keyboardActions = KeyboardActions(onSend = { onSend() }),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = onSend, enabled = !streaming) { Icon(Icons.Default.Send, strings.send) }
            }
        }
    }
}

@Composable
private fun HighlightedText(text: String, query: String) {
    if (query.isBlank()) { Text(text.take(220)); return }
    val snippet = text.take(300)
    val highlight = MaterialTheme.colorScheme.secondaryContainer
    val terms = query.trim().split(Regex("\\s+")).filter { it.isNotBlank() }.distinct()
    val annotated = buildAnnotatedString {
        append(snippet)
        terms.forEach { term ->
            Regex(Regex.escape(term), RegexOption.IGNORE_CASE).findAll(snippet).forEach { match ->
                addStyle(SpanStyle(background = highlight), match.range.first, match.range.last + 1)
            }
        }
    }
    Text(annotated, maxLines = 3)
}
