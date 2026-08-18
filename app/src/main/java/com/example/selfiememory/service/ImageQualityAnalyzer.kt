package com.example.selfiememory.service

import android.graphics.BitmapFactory
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max

data class ImageQuality(
    val accepted: Boolean,
    val averageLuma: Double,
    val darkPixelRatio: Double
)

@Singleton
class ImageQualityAnalyzer @Inject constructor() {
    companion object {
        private const val TAG = "ImageQualityAnalyzer"
        private const val TARGET_SIZE = 256
    }

    fun analyze(jpeg: ByteArray): ImageQuality {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, bounds)
        var sampleSize = 1
        while (max(bounds.outWidth, bounds.outHeight) / sampleSize > TARGET_SIZE) sampleSize *= 2
        val bitmap = BitmapFactory.decodeByteArray(
            jpeg,
            0,
            jpeg.size,
            BitmapFactory.Options().apply { inSampleSize = sampleSize }
        ) ?: return ImageQuality(false, 0.0, 1.0)

        return try {
            val pixels = IntArray(bitmap.width * bitmap.height)
            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
            var lumaSum = 0.0
            var darkPixels = 0
            pixels.forEach { color ->
                val red = color shr 16 and 0xff
                val green = color shr 8 and 0xff
                val blue = color and 0xff
                val luma = 0.2126 * red + 0.7152 * green + 0.0722 * blue
                lumaSum += luma
                if (luma < 32.0) darkPixels++
            }
            val average = lumaSum / pixels.size.coerceAtLeast(1)
            val darkRatio = darkPixels.toDouble() / pixels.size.coerceAtLeast(1)
            // Deliberately conservative: reject only frames that are overwhelmingly black.
            val accepted = average >= 18.0 && darkRatio < 0.94
            ImageQuality(accepted, average, darkRatio).also {
                Log.i(TAG, "accepted=${it.accepted}, luma=${it.averageLuma}, dark=${it.darkPixelRatio}")
            }
        } finally {
            bitmap.recycle()
        }
    }
}
