package com.example.selfiememory.ui.viewer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ViewerScreen(
    selfieId: Int,
    onNavigateBack: () -> Unit,
    viewModel: ViewerViewModel = hiltViewModel()
) {
    LaunchedEffect(selfieId) { viewModel.setSelfieId(selfieId) }
    val selfie by viewModel.selfie.collectAsState()
    val selfies by viewModel.selfies.collectAsState()
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    val transformState = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 5f)
        if (scale > 1f) {
            offsetX += pan.x
            offsetY += pan.y
        } else {
            offsetX = 0f
            offsetY = 0f
        }
    }
    val pagerState = rememberPagerState(pageCount = { selfies.size })
    var pagerInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(selfies, selfieId) {
        val index = selfies.indexOfFirst { it.id == selfieId }
        if (index >= 0) {
            pagerInitialized = false
            pagerState.scrollToPage(index)
            viewModel.setSelfieId(selfieId)
            pagerInitialized = true
        }
    }
    LaunchedEffect(pagerState.currentPage, selfies, pagerInitialized) {
        if (pagerInitialized) {
            selfies.getOrNull(pagerState.currentPage)?.let { viewModel.setSelfieId(it.id) }
            scale = 1f
            offsetX = 0f
            offsetY = 0f
        }
    }

    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = Color.White)
                    }
                },
                actions = {
                    selfie?.mediaUri?.let { uriString ->
                        IconButton(onClick = { openExternally(context, uriString) }) {
                            Icon(Icons.AutoMirrored.Filled.OpenInNew, "In Foto-App öffnen", tint = Color.White)
                        }
                        IconButton(onClick = { share(context, uriString) }) {
                            Icon(Icons.Default.Share, "Teilen", tint = Color.White)
                        }
                    }
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Löschen", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black.copy(alpha = .72f))
            )
        }
    ) { padding ->
        selfie?.let { current ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                    selfies.getOrNull(page)?.let { pageSelfie ->
                        val source: Any = pageSelfie.mediaUri?.let(Uri::parse) ?: File(pageSelfie.filePath)
                        AsyncImage(
                            model = source,
                            contentDescription = "Selfie",
                            modifier = Modifier.fillMaxSize().graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY
                            ).transformable(transformState),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(Color.Black.copy(alpha = .62f)).padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        SimpleDateFormat("EEEE, d. MMMM yyyy · HH:mm", Locale.getDefault()).format(Date(current.timestamp)),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        if (current.latitude != null && current.longitude != null) {
                            Text("📍 %.4f, %.4f".format(current.latitude, current.longitude), color = Color.LightGray)
                        }
                        if (current.mediaUri != null) Text("${pagerState.currentPage + 1}/${selfies.size} · In Fotogalerie gesichert", color = Color.LightGray)
                    }
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Erinnerung löschen?") },
            text = { Text("Das entfernt auch die Kopie aus deiner Foto-App.") },
            confirmButton = {
                TextButton(onClick = {
                    selfie?.let(viewModel::deleteSelfie)
                    showDeleteDialog = false
                    onNavigateBack()
                }) { Text("Löschen") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Abbrechen") } }
        )
    }
}

private fun openExternally(context: android.content.Context, uriString: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(Uri.parse(uriString), "image/jpeg")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "Öffnen mit")) }
}

private fun share(context: android.content.Context, uriString: String) {
    val uri = Uri.parse(uriString)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    context.startActivity(Intent.createChooser(intent, "Erinnerung teilen"))
}
