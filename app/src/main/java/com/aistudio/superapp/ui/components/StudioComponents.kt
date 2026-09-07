package com.aistudio.superapp.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun AiBrainLogo(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.size(38.dp)) {
        val r = size.minDimension * .34f
        drawCircle(color.copy(alpha = .18f), r * 1.45f, center)
        drawCircle(color, r, center)
        val c = center
        drawLine(Color.White, Offset(c.x - r * .55f, c.y), Offset(c.x + r * .55f, c.y), strokeWidth = 3f)
        drawLine(Color.White, Offset(c.x, c.y - r * .55f), Offset(c.x, c.y + r * .55f), strokeWidth = 3f)
        drawCircle(Color.White, r * .15f, Offset(c.x - r * .55f, c.y))
        drawCircle(Color.White, r * .15f, Offset(c.x + r * .55f, c.y))
        drawCircle(Color.White, r * .15f, Offset(c.x, c.y - r * .55f))
        drawCircle(Color.White, r * .15f, Offset(c.x, c.y + r * .55f))
    }
}

@Composable
fun TypingIndicator(modelName: String, label: String, modifier: Modifier = Modifier) {
    val dotColor = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "typing")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Restart), label = "phase")
    Row(modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
        AssistChip(onClick = {}, label = { Text(modelName) })
        Spacer(Modifier.width(10.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.width(8.dp))
        Canvas(Modifier.width(34.dp).height(14.dp)) {
            repeat(3) { i ->
                val local = ((phase + i * .22f) % 1f)
                val y = size.height / 2f - kotlin.math.sin(local * Math.PI).toFloat() * 4f
                drawCircle(dotColor, 3.4f, Offset(6f + i * 11f, y))
            }
        }
    }
}

@Composable
fun Waveform(amplitudes: List<Float>, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier.height(54.dp).fillMaxWidth()) {
        if (amplitudes.isEmpty()) return@Canvas
        val step = size.width / amplitudes.size.coerceAtLeast(1)
        amplitudes.takeLast(64).forEachIndexed { i, amp ->
            val h = (amp.coerceIn(0f, 1f) * size.height).coerceAtLeast(2f)
            drawLine(color, Offset(i * step, (size.height - h) / 2), Offset(i * step, (size.height + h) / 2), strokeWidth = step * .55f)
        }
    }
}

@Composable
fun CodeBlock(code: String, language: String?) {
    val context = LocalContext.current
    Surface(shape = RoundedCornerShape(12.dp), tonalElevation = 4.dp, modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(language.orEmpty().ifBlank { "code" }, style = MaterialTheme.typography.labelSmall)
                IconButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("code", code))
                }) { Icon(Icons.Default.ContentCopy, contentDescription = "Copy") }
            }
            val colors = MaterialTheme.colorScheme
            Text(
                simpleHighlight(code, colors.primary, colors.tertiary, colors.secondary),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(12.dp),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun simpleHighlight(code: String, keywordColor: Color, stringColor: Color, commentColor: Color): AnnotatedString = buildAnnotatedString {
    append(code)
    val keywords = Regex("\\b(class|fun|val|var|if|else|when|for|while|return|import|package|suspend|data|object|interface|override|private|public|internal|try|catch|throw|async|await|const|let|def|from|as|in|is|None|true|false|null)\\b")
    val strings = Regex("\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*'")
    val comments = Regex("(?m)(//.*$|#.*$)")
    keywords.findAll(code).forEach { addStyle(SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold), it.range.first, it.range.last + 1) }
    strings.findAll(code).forEach { addStyle(SpanStyle(color = stringColor), it.range.first, it.range.last + 1) }
    comments.findAll(code).forEach { addStyle(SpanStyle(color = commentColor), it.range.first, it.range.last + 1) }
}

@Composable
fun RichMessageText(text: String) {
    val parts = remember(text) { Regex("```([\\w+-]*)\\n([\\s\\S]*?)```").findAll(text).toList() }
    if (parts.isEmpty()) { Text(text); return }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var cursor = 0
        parts.forEach { m ->
            if (m.range.first > cursor) Text(text.substring(cursor, m.range.first))
            CodeBlock(m.groupValues[2].trimEnd(), m.groupValues[1])
            cursor = m.range.last + 1
        }
        if (cursor < text.length) Text(text.substring(cursor))
    }
}
