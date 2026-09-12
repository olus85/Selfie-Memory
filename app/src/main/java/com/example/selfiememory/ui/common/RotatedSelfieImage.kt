package com.example.selfiememory.ui.common

import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Size
import coil.transform.Transformation

private object RotateClockwise90 : Transformation {
    override val cacheKey = "selfie-clockwise-90-v1"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap =
        Bitmap.createBitmap(
            input,
            0,
            0,
            input.width,
            input.height,
            Matrix().apply { postRotate(90f) },
            true
        )
}

@Composable
fun RotatedSelfieImage(
    model: Any,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    val context = LocalContext.current
    val request = remember(model) {
        ImageRequest.Builder(context)
            .data(model)
            .transformations(RotateClockwise90)
            .build()
    }
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale
    )
}
