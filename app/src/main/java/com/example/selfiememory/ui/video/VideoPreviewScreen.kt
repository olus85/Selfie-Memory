package com.example.selfiememory.ui.video

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoPreviewScreen(videoUri: Uri, onNavigateBack: () -> Unit) {
    val context = LocalContext.current
    var videoView by remember { mutableStateOf<VideoView?>(null) }
    var playbackError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { videoView?.stopPlayback() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monatsrückblick") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AndroidView(
                factory = { viewContext ->
                    VideoView(viewContext).also { view ->
                        videoView = view
                        view.tag = videoUri
                        view.setMediaController(MediaController(viewContext).apply { setAnchorView(view) })
                        view.setOnPreparedListener { player ->
                            player.isLooping = true
                            view.start()
                        }
                        view.setOnErrorListener { _, _, _ ->
                            playbackError = "Das Video konnte nicht wiedergegeben werden."
                            true
                        }
                        view.setVideoURI(videoUri)
                    }
                },
                update = { view ->
                    if (view.tag != videoUri) {
                        view.tag = videoUri
                        view.setVideoURI(videoUri)
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(1f)
            )
            playbackError?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }
            Button(
                onClick = { shareVideo(context, videoUri) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Share, null)
                Text(" Video teilen")
            }
        }
    }
}

private fun shareVideo(context: android.content.Context, uri: Uri) {
    val fileName = "Selfie-Memory-Monatsrückblick.mp4"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "video/mp4"
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_TITLE, fileName)
        clipData = ClipData.newUri(context.contentResolver, fileName, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Monatsrückblick teilen"))
}
