package com.aistudio.superapp.ui.photo

import android.graphics.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.aistudio.superapp.domain.model.ModelConfig
import com.aistudio.superapp.domain.repository.AiRepository
import com.aistudio.superapp.ui.i18n.StudioStrings
import com.aistudio.superapp.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoEditorScreen(strings: StudioStrings, models: List<ModelConfig>, aiRepo: AiRepository) {
    val vm: PhotoEditorViewModel = viewModel(factory = PhotoEditorViewModel.factory(aiRepo))
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var original by remember { mutableStateOf<Bitmap?>(null) }
    var edited by remember { mutableStateOf<Bitmap?>(null) }
    var prompt by rememberSaveable { mutableStateOf("") }
    val points = remember { mutableStateListOf<Offset>() }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val imageModels = models.filter { it.supportsImageGeneration }
    var selectedId by rememberSaveable { mutableStateOf(imageModels.firstOrNull()?.id.orEmpty()) }
    val model = imageModels.firstOrNull { it.id == selectedId } ?: imageModels.firstOrNull()

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            scope.launch {
                val bitmap = withContext(Dispatchers.IO) { loadBitmap(context, it) }
                original = bitmap; edited = bitmap.copy(Bitmap.Config.ARGB_8888, true); points.clear()
            }
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text(strings.photo) }) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).padding(12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) { Icon(Icons.Default.AddPhotoAlternate, null); Text(strings.chooseImage) }
            edited?.let { bitmap ->
                Box(Modifier.fillMaxWidth().heightIn(min = 260.dp, max = 460.dp).onSizeChanged { canvasSize = it }) {
                    Canvas(Modifier.matchParentSize()) { drawImage(bitmap.asImageBitmap(), dstSize = androidx.compose.ui.unit.IntSize(size.width.toInt(), size.height.toInt())) }
                    Canvas(
                        Modifier.matchParentSize().pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { points += it },
                                onDrag = { change, _ -> points += change.position },
                            )
                        },
                    ) {
                        points.zipWithNext().forEach { (a, b) -> drawLine(androidx.compose.ui.graphics.Color.Magenta.copy(alpha = .55f), a, b, strokeWidth = 30f) }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AssistChip(onClick = { edited = centerCropBitmap(bitmap); points.clear() }, label = { Text(strings.crop) }, leadingIcon = { Icon(Icons.Default.Crop, null) })
                    AssistChip(onClick = { edited = rotateBitmap(bitmap, 90f); points.clear() }, label = { Text(strings.rotate) }, leadingIcon = { Icon(Icons.Default.RotateRight, null) })
                    AssistChip(onClick = { edited = grayscaleBitmap(bitmap) }, label = { Text(strings.grayscale) })
                    AssistChip(onClick = { edited = sepiaBitmap(bitmap) }, label = { Text(strings.sepia) })
                }
                TextButton(onClick = { original?.let { edited = it.copy(Bitmap.Config.ARGB_8888, true) }; points.clear() }) { Icon(Icons.Default.RestartAlt, null); Text(strings.reset) }
                HorizontalDivider()
                Text(strings.aiModify, style = MaterialTheme.typography.titleMedium)
                Text(strings.paintArea, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(prompt, { prompt = it }, label = { Text(strings.inpaintHint) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                PhotoModelSelector(imageModels, model?.id, strings.imageModel) { selectedId = it }
                Button(
                    onClick = {
                        if (model != null) {
                            val mask = if (points.size > 1 && canvasSize.width > 0) buildInpaintMask(bitmap, points.toList(), canvasSize) else null
                            vm.modify(bitmap, mask, prompt, model)
                        }
                    },
                    enabled = model != null && prompt.isNotBlank() && !state.loading,
                    modifier = Modifier.fillMaxWidth(),
                ) { if (state.loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Icon(Icons.Default.AutoFixHigh, null); Spacer(Modifier.width(8.dp)); Text(strings.aiModify) }
            }
            state.resultSource?.let { src ->
                Text(strings.done, style = MaterialTheme.typography.titleMedium)
                AsyncImage(src, null, Modifier.fillMaxWidth().heightIn(max = 460.dp))
                Row {
                    Button(onClick = { scope.launch { saveImageToGallery(context, aiRepo.downloadBytes(src)) } }) { Icon(Icons.Default.Download, null); Text(strings.download) }
                    Spacer(Modifier.width(8.dp))
                    OutlinedButton(onClick = { scope.launch { saveImageToGallery(context, aiRepo.downloadBytes(src))?.let { shareUri(context, it) } } }) { Icon(Icons.Default.Share, null); Text(strings.share) }
                }
            }
        }
    }
    state.error?.let { AlertDialog(onDismissRequest = vm::clearError, confirmButton = { TextButton(onClick = vm::clearError) { Text(strings.ok) } }, title = { Text(strings.error) }, text = { Text(it) }) }
}

private fun buildInpaintMask(bitmap: Bitmap, points: List<Offset>, viewSize: IntSize): Bitmap {
    val mask = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(mask)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = 42f * bitmap.width / viewSize.width.coerceAtLeast(1)
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
    }
    val sx = bitmap.width.toFloat() / viewSize.width.coerceAtLeast(1)
    val sy = bitmap.height.toFloat() / viewSize.height.coerceAtLeast(1)
    points.zipWithNext().forEach { (a, b) -> canvas.drawLine(a.x * sx, a.y * sy, b.x * sx, b.y * sy, paint) }
    return mask
}

@Composable
private fun PhotoModelSelector(models: List<ModelConfig>, selected: String?, fallbackLabel: String, onSelect: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box { OutlinedButton(onClick = { open = true }) { Text(models.firstOrNull { it.id == selected }?.displayName ?: fallbackLabel) }; DropdownMenu(open, { open = false }) { models.forEach { DropdownMenuItem({ Text(it.displayName) }, onClick = { onSelect(it.id); open = false }) } } }
}
