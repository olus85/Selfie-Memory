package com.example.selfiememory.service

import android.content.Context
import android.util.Log
import android.graphics.BitmapFactory
import android.graphics.Matrix
import java.io.ByteArrayOutputStream
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.selfiememory.domain.model.CameraType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class CameraCapturer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CameraCapturer"
        private const val CAMERA_WARMUP_MS = 1_100L
        private const val CAPTURE_TIMEOUT_MS = 12_000L
    }

    private var cameraProvider: ProcessCameraProvider? = null

    suspend fun captureImage(lifecycleOwner: LifecycleOwner, cameraType: CameraType, mirror: Boolean = true): ByteArray =
        withTimeout(CAPTURE_TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) {
                val provider = awaitCameraProvider()
                cameraProvider = provider
                val selector = when (cameraType) {
                    CameraType.FRONT_ULTRA_WIDE, CameraType.FRONT_NORMAL -> CameraSelector.DEFAULT_FRONT_CAMERA
                    CameraType.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                }
                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setJpegQuality(95)
                    .build()

                try {
                    provider.unbindAll()
                    val camera = provider.bindToLifecycle(lifecycleOwner, selector, imageCapture)
                    val zoom = when (cameraType) {
                        CameraType.FRONT_ULTRA_WIDE -> camera.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
                        CameraType.FRONT_NORMAL -> 1.18f
                        CameraType.BACK -> 1f
                    }.coerceIn(camera.cameraInfo.zoomState.value?.minZoomRatio ?: 1f, camera.cameraInfo.zoomState.value?.maxZoomRatio ?: 1f)
                    camera.cameraControl.setZoomRatio(zoom)
                    // CameraX binding is not exposure readiness. Give 3A time to settle.
                    delay(CAMERA_WARMUP_MS)
                    val jpeg = takePicture(imageCapture)
                    if (mirror && cameraType != CameraType.BACK) mirrorJpeg(jpeg) else jpeg
                } finally {
                    provider.unbindAll()
                }
            }
        }

    private suspend fun mirrorJpeg(bytes: ByteArray): ByteArray = withContext(Dispatchers.Default) {
        val source = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@withContext bytes
        val mirrored = android.graphics.Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { setScale(-1f, 1f) }, true)
        ByteArrayOutputStream().use { out ->
            mirrored.compress(android.graphics.Bitmap.CompressFormat.JPEG, 94, out)
            if (mirrored !== source) mirrored.recycle(); source.recycle(); out.toByteArray()
        }
    }

    private suspend fun awaitCameraProvider(): ProcessCameraProvider = suspendCancellableCoroutine { continuation ->
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            runCatching { future.get() }
                .onSuccess { if (continuation.isActive) continuation.resume(it) }
                .onFailure { if (continuation.isActive) continuation.resumeWithException(it) }
        }, ContextCompat.getMainExecutor(context))
        continuation.invokeOnCancellation { future.cancel(true) }
    }

    private suspend fun takePicture(imageCapture: ImageCapture): ByteArray =
        suspendCancellableCoroutine { continuation ->
            imageCapture.takePicture(
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageCapturedCallback() {
                    override fun onCaptureSuccess(image: ImageProxy) {
                        try {
                            val buffer = image.planes[0].buffer
                            val bytes = ByteArray(buffer.remaining()).also { buffer.get(it) }
                            Log.i(TAG, "Image captured successfully, size=${bytes.size}")
                            if (continuation.isActive) continuation.resume(bytes)
                        } catch (error: Exception) {
                            if (continuation.isActive) continuation.resumeWithException(error)
                        } finally {
                            image.close()
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        if (continuation.isActive) continuation.resumeWithException(exception)
                    }
                }
            )
        }

    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraProvider = null
    }
}
