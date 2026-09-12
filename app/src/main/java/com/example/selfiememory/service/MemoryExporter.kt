package com.example.selfiememory.service

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.example.selfiememory.domain.model.Selfie
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MemoryExporter @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val VIDEO_WIDTH = 360
        private const val VIDEO_HEIGHT = 640
        private const val VIDEO_FPS = 10
        private const val SECONDS_PER_PHOTO = 2
    }

    suspend fun collage(a: Selfie, b: Selfie): Uri = withContext(Dispatchers.Default) {
        val width = 1080
        val height = 720
        val output = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output).apply { drawColor(Color.BLACK) }
        drawFit(canvas, loadRotated(a), Rect(0, 0, width / 2, height))
        drawFit(canvas, loadRotated(b), Rect(width / 2, 0, width, height))
        val uri = createImage("vergleich_${stamp()}.jpg")
        context.contentResolver.openOutputStream(uri, "w")!!.use {
            check(output.compress(Bitmap.CompressFormat.JPEG, 92, it))
        }
        finishImage(uri)
        output.recycle()
        uri
    }

    suspend fun monthlyVideo(items: List<Selfie>): Uri = withContext(Dispatchers.Default) {
        require(items.isNotEmpty()) { "Keine Fotos im Zeitraum" }
        val chosen = items
            .groupBy { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(it.timestamp)) }
            .values
            .map { day -> day.firstOrNull(Selfie::favorite) ?: day.first() }
            .take(31)
            .reversed()

        val sharedDir = File(context.cacheDir, "shared").apply { mkdirs() }
        sharedDir.listFiles()
            ?.filter { it.name.startsWith("selfie_memory_") }
            ?.forEach { it.delete() }
        val baseName = "selfie_memory_${stamp()}"
        val temporary = File(sharedDir, "$baseName.tmp")
        val completed = File(sharedDir, "$baseName.mp4")

        val format = MediaFormat.createVideoFormat(
            MediaFormat.MIMETYPE_VIDEO_AVC,
            VIDEO_WIDTH,
            VIDEO_HEIGHT
        ).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible)
            setInteger(MediaFormat.KEY_BIT_RATE, 1_200_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, VIDEO_FPS)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel3)
        }

        val codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        try {
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start()
            muxer = MediaMuxer(temporary.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val info = MediaCodec.BufferInfo()
            var track = -1

            fun drain(waitForEnd: Boolean): Boolean {
                var idleAttempts = 0
                while (true) {
                    when (val index = codec.dequeueOutputBuffer(info, if (waitForEnd) 10_000 else 0)) {
                        MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!waitForEnd) return false
                            check(++idleAttempts < 300) { "Video-Encoder reagiert nicht" }
                        }
                        MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            check(!muxerStarted) { "Encoderformat wurde doppelt gemeldet" }
                            track = muxer!!.addTrack(codec.outputFormat)
                            muxer!!.start()
                            muxerStarted = true
                        }
                        else -> if (index >= 0) {
                            val output = codec.getOutputBuffer(index)
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                            if (info.size > 0) {
                                check(muxerStarted) { "Videospur wurde nicht initialisiert" }
                                output!!.position(info.offset)
                                output.limit(info.offset + info.size)
                                muxer!!.writeSampleData(track, output, info)
                            }
                            val ended = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                            codec.releaseOutputBuffer(index, false)
                            if (ended) return true
                        }
                    }
                }
            }

            fun queue(bytes: ByteArray, timestampUs: Long, flags: Int = 0) {
                var attempts = 0
                while (true) {
                    val index = codec.dequeueInputBuffer(10_000)
                    if (index >= 0) {
                        codec.getInputBuffer(index)!!.apply {
                            clear()
                            check(capacity() >= bytes.size) { "Encoderpuffer ist zu klein" }
                            put(bytes)
                        }
                        codec.queueInputBuffer(index, 0, bytes.size, timestampUs, flags)
                        return
                    }
                    drain(false)
                    check(++attempts < 300) { "Kein freier Video-Encoderpuffer" }
                }
            }

            var frame = 0L
            chosen.forEach { selfie ->
                val rendered = renderFrame(loadRotated(selfie), selfie.timestamp)
                val yuv = toI420(rendered)
                rendered.recycle()
                repeat(VIDEO_FPS * SECONDS_PER_PHOTO) {
                    queue(yuv, frame * 1_000_000L / VIDEO_FPS)
                    frame++
                    drain(false)
                }
            }
            queue(ByteArray(0), frame * 1_000_000L / VIDEO_FPS, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            check(drain(true)) { "Video wurde nicht abgeschlossen" }
            check(muxerStarted) { "Video enthält keine Spur" }
            muxer.stop()
            muxerStarted = false
            muxer.release()
            muxer = null
            check(temporary.length() > 1_024L) { "Erzeugte Videodatei ist leer" }
            check(temporary.renameTo(completed)) { "Videodatei konnte nicht fertiggestellt werden" }
            FileProvider.getUriForFile(context, "${context.packageName}.files", completed)
        } catch (error: Throwable) {
            if (muxerStarted) runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
            temporary.delete()
            completed.delete()
            throw error
        } finally {
            runCatching { codec.stop() }
            codec.release()
        }
    }

    private fun loadRotated(selfie: Selfie): Bitmap {
        val stream = selfie.mediaUri
            ?.let { context.contentResolver.openInputStream(Uri.parse(it)) }
            ?: File(selfie.filePath).inputStream()
        val source = stream.use { BitmapFactory.decodeStream(it) } ?: error("Foto nicht lesbar")
        val rotated = Bitmap.createBitmap(
            source,
            0,
            0,
            source.width,
            source.height,
            Matrix().apply { postRotate(90f) },
            true
        )
        if (rotated !== source) source.recycle()
        return rotated
    }

    private fun renderFrame(source: Bitmap, timestamp: Long): Bitmap {
        val output = Bitmap.createBitmap(VIDEO_WIDTH, VIDEO_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output).apply { drawColor(Color.BLACK) }
        drawFit(canvas, source, Rect(0, 0, VIDEO_WIDTH, VIDEO_HEIGHT))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = 22f
            setShadowLayer(4f, 0f, 2f, Color.BLACK)
        }
        canvas.drawText(
            SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(Date(timestamp)),
            16f,
            VIDEO_HEIGHT - 22f,
            paint
        )
        return output
    }

    private fun drawFit(canvas: Canvas, bitmap: Bitmap, destination: Rect) {
        val scale = maxOf(
            destination.width().toFloat() / bitmap.width,
            destination.height().toFloat() / bitmap.height
        )
        val sourceWidth = (destination.width() / scale).toInt()
        val sourceHeight = (destination.height() / scale).toInt()
        val source = Rect(
            (bitmap.width - sourceWidth) / 2,
            (bitmap.height - sourceHeight) / 2,
            (bitmap.width + sourceWidth) / 2,
            (bitmap.height + sourceHeight) / 2
        )
        canvas.drawBitmap(bitmap, source, destination, Paint(Paint.ANTI_ALIAS_FLAG))
        bitmap.recycle()
    }

    private fun toI420(bitmap: Bitmap): ByteArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val output = ByteArray(width * height * 3 / 2)
        var yIndex = 0
        var uIndex = width * height
        var vIndex = uIndex + width * height / 4
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = pixels[y * width + x]
                val red = Color.red(color)
                val green = Color.green(color)
                val blue = Color.blue(color)
                output[yIndex++] = (((66 * red + 129 * green + 25 * blue + 128) shr 8) + 16)
                    .coerceIn(0, 255).toByte()
                if (y % 2 == 0 && x % 2 == 0) {
                    output[uIndex++] = (((-38 * red - 74 * green + 112 * blue + 128) shr 8) + 128)
                        .coerceIn(0, 255).toByte()
                    output[vIndex++] = (((112 * red - 94 * green - 18 * blue + 128) shr 8) + 128)
                        .coerceIn(0, 255).toByte()
                }
            }
        }
        return output
    }

    private fun createImage(name: String): Uri = context.contentResolver.insert(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= 29) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Selfie Memory")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
    )!!

    private fun finishImage(uri: Uri) {
        if (Build.VERSION.SDK_INT >= 29) {
            context.contentResolver.update(
                uri,
                ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                null,
                null
            )
        }
    }

    private fun stamp(): String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
}
