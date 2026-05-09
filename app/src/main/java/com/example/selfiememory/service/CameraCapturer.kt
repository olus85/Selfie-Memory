package com.example.selfiememory.service

import android.content.Context
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.selfiememory.domain.model.CameraType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.ByteBuffer
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

@Singleton
class CameraCapturer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "CameraCapturer"
    }

    private val cameraExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private var cameraProvider: ProcessCameraProvider? = null

    suspend fun captureImage(
        lifecycleOwner: LifecycleOwner,
        cameraType: CameraType
    ): ByteArray = suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()

                val cameraSelector = when (cameraType) {
                    CameraType.FRONT_ULTRA_WIDE -> CameraSelector.DEFAULT_FRONT_CAMERA
                    CameraType.FRONT_NORMAL -> CameraSelector.DEFAULT_FRONT_CAMERA
                    CameraType.BACK -> CameraSelector.DEFAULT_BACK_CAMERA
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                try {
                    cameraProvider?.unbindAll()
                    cameraProvider?.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageCapture
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind camera", e)
                    continuation.resumeWithException(e)
                    return@addListener
                }

                imageCapture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            image.use {
                                val buffer = it.planes[0].buffer
                                val bytes = ByteArray(buffer.remaining())
                                buffer.get(bytes)
                                Log.i(TAG, "Image captured successfully, size: ${bytes.size}")
                                continuation.resume(bytes)
                            }
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e(TAG, "Capture failed", exception)
                            cameraProvider?.unbindAll()
                            continuation.resumeWithException(exception)
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Camera setup failed", e)
                cameraProvider?.unbindAll()
                continuation.resumeWithException(e)
            }
        }, cameraExecutor)
    }

    fun shutdown() {
        cameraProvider?.unbindAll()
        cameraProvider = null
        cameraExecutor.shutdown()
    }
}
