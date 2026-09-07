package com.aistudio.superapp.utils

import android.content.Context
import android.graphics.*
import android.media.MediaRecorder
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File

fun createCameraUri(context: Context): Uri {
    val dir = File(context.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "capture_${System.currentTimeMillis()}.jpg")
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

class AudioRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    var outputFile: File? = null
        private set

    @Suppress("DEPRECATION")
    fun start(): File {
        val dir = File(context.cacheDir, "audio").apply { mkdirs() }
        val file = File(dir, "recording_${System.currentTimeMillis()}.m4a")
        val r = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
        r.setAudioSource(MediaRecorder.AudioSource.MIC)
        r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        r.setAudioEncodingBitRate(128_000)
        r.setAudioSamplingRate(44_100)
        r.setOutputFile(file.absolutePath)
        r.prepare(); r.start()
        recorder = r; outputFile = file
        return file
    }

    fun amplitude(): Float = runCatching { (recorder?.maxAmplitude ?: 0) / 32767f }.getOrDefault(0f)

    fun stop(): File? {
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        return outputFile
    }
}

fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap =
    Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(degrees) }, true)

fun centerCropBitmap(source: Bitmap): Bitmap {
    val side = minOf(source.width, source.height)
    val x = (source.width - side) / 2
    val y = (source.height - side) / 2
    return Bitmap.createBitmap(source, x, y, side, side)
}

fun grayscaleBitmap(source: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(out)
    val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) }) }
    canvas.drawBitmap(source, 0f, 0f, paint)
    return out
}

fun sepiaBitmap(source: Bitmap): Bitmap {
    val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
    val matrix = ColorMatrix(floatArrayOf(
        .393f,.769f,.189f,0f,0f,
        .349f,.686f,.168f,0f,0f,
        .272f,.534f,.131f,0f,0f,
        0f,0f,0f,1f,0f,
    ))
    Canvas(out).drawBitmap(source, 0f, 0f, Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) })
    return out
}

@Suppress("DEPRECATION")
fun loadBitmap(context: Context, uri: Uri): Bitmap {
    return if (android.os.Build.VERSION.SDK_INT >= 28) {
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, _, _ -> decoder.isMutableRequired = true }
    } else {
        android.provider.MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
    }
}
