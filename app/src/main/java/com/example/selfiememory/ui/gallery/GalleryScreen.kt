package com.example.selfiememory.ui.gallery

import android.content.ClipData
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import android.content.Intent
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilterChip
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.selfiememory.domain.model.Selfie
import com.example.selfiememory.ui.common.RotatedSelfieImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToViewer: (Int) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val selfies by viewModel.selfies.collectAsState()
    val exporting by viewModel.exporting.collectAsState()
    val settings by viewModel.settings.collectAsState();val context=LocalContext.current
    var query by remember{mutableStateOf("")};var favorites by remember{mutableStateOf(false)};var trash by remember{mutableStateOf(false)};var exportMessage by remember{mutableStateOf<String?>(null)}
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Erinnerungen", fontWeight = FontWeight.SemiBold)
                        if (selfies.isNotEmpty()) {
                            Text(
                                "${selfies.size} Momente · ${selfies.count { it.mediaUri != null }} in der Fotogalerie",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(enabled = !exporting, onClick = {
                        exportMessage = null
                        viewModel.monthlyVideo { uri, error ->
                            exportMessage = error
                            if (uri != null) {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "video/mp4"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    putExtra(Intent.EXTRA_TITLE, "Selfie-Memory-Monatsrückblick.mp4")
                                    clipData = ClipData.newUri(context.contentResolver, "Selfie-Memory-Monatsrückblick.mp4", uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Monatsrückblick teilen"))
                            }
                        }
                    }) {
                        if (exporting) CircularProgressIndicator()
                        else Icon(Icons.Default.Movie, "Monatsrückblick")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)){
            Card(Modifier.fillMaxWidth().padding(horizontal=8.dp,vertical=4.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(12.dp)){Text(if(settings.enabled)"Automatik aktiv" else "Automatik ausgeschaltet",fontWeight=FontWeight.SemiBold);Text(settings.lastStatus,style=MaterialTheme.typography.bodySmall)}}
            Row(Modifier.fillMaxWidth().padding(horizontal=8.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(query,{query=it;viewModel.search(it)},Modifier.weight(1f),singleLine=true,label={Text("Tags oder Notizen suchen")});FilterChip(favorites,{favorites=!favorites;viewModel.favorites(favorites)},{Icon(Icons.Default.Star,"Favoriten")});FilterChip(trash,{trash=!trash;viewModel.trash(trash)},{Text("Papierkorb")})}
            exportMessage?.let{Text(it,Modifier.padding(8.dp),color=MaterialTheme.colorScheme.error)}
        if (selfies.isEmpty()) {
            EmptyGallery(Modifier.fillMaxSize())
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(112.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 32.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                selfies.groupBy { dateKey(it.timestamp) }.forEach { (day, daySelfies) ->
                    item(key = "header_$day", span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = dateHeader(day),
                            modifier = Modifier.padding(start = 6.dp, top = 14.dp, bottom = 5.dp),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    items(daySelfies, key = { it.id }) { selfie ->
                        MemoryTile(selfie, { if(trash)viewModel.restore(selfie.id) else onNavigateToViewer(selfie.id) })
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun EmptyGallery(modifier: Modifier) {
    Box(modifier = modifier.padding(24.dp), contentAlignment = Alignment.Center) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text("Dein nächster echter Moment landet hier.", style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                Text(
                    "Beim Entsperren prüft Selfie Memory Netzwerk, Wartezeit und Hosentasche – schwarze Bilder werden verworfen.",
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun MemoryTile(selfie: Selfie, onClick: () -> Unit) {
    val source: Any = selfie.mediaUri?.let(Uri::parse) ?: File(selfie.filePath)
    Card(
        modifier = Modifier.aspectRatio(0.82f).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Box(Modifier.fillMaxSize()) {
            RotatedSelfieImage(source, "Selfie", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            Box(
                Modifier.fillMaxWidth().align(Alignment.BottomCenter).background(
                    Brush.verticalGradient(listOf(androidx.compose.ui.graphics.Color.Transparent, androidx.compose.ui.graphics.Color.Black.copy(alpha = .68f)))
                ).padding(top = 28.dp, start = 9.dp, end = 9.dp, bottom = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(selfie.timestamp)),
                        color = androidx.compose.ui.graphics.Color.White,
                        style = MaterialTheme.typography.labelLarge,
                        modifier = Modifier.weight(1f)
                    )
                    if (selfie.mediaUri != null) Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = "In Fotogalerie verfügbar",
                        tint = androidx.compose.ui.graphics.Color.White,
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                    )
                    if(selfie.favorite) Icon(Icons.Default.Star,"Favorit",tint=androidx.compose.ui.graphics.Color.Yellow)
                }
            }
        }
    }
}

private fun dateKey(timestamp: Long) = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(Date(timestamp))

private fun dateHeader(key: String): String = runCatching {
    val date = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(key)!!
    val target = Calendar.getInstance().apply { time = date }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    when {
        sameDay(target, today) -> "Heute"
        sameDay(target, yesterday) -> "Gestern"
        else -> SimpleDateFormat("EEEE, d. MMMM", Locale.getDefault()).format(date)
    }
}.getOrDefault(key)

private fun sameDay(a: Calendar, b: Calendar) =
    a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
