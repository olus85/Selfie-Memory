package com.example.selfiememory.ui.gallery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.example.selfiememory.domain.model.Selfie
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GalleryScreen(
    onNavigateToSettings: () -> Unit,
    onNavigateToViewer: (Int) -> Unit,
    viewModel: GalleryViewModel = hiltViewModel()
) {
    val selfies by viewModel.selfies.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Selfie-Memory") },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        if (selfies.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No selfies yet.\nUnlock your device at a configured location to capture.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            val groupedSelfies = selfies.groupBy { selfie ->
                getDateKey(selfie.timestamp)
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                groupedSelfies.forEach { (dateKey, daySelfies) ->
                    item(key = "header_$dateKey") {
                        DateHeader(dateKey = dateKey)
                    }

                    items(
                        items = daySelfies.chunked(3),
                        key = { row -> row.map { it.id }.joinToString("_") }
                    ) { rowSelfies ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            rowSelfies.forEach { selfie ->
                                SelfieThumbnail(
                                    selfie = selfie,
                                    onClick = { onNavigateToViewer(selfie.id) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            // Fill remaining space if row is not complete
                            repeat(3 - rowSelfies.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DateHeader(dateKey: String) {
    val displayDate = formatDateHeader(dateKey)

    Text(
        text = displayDate,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun SelfieThumbnail(
    selfie: Selfie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = File(selfie.filePath),
            contentDescription = "Selfie",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
    }
}

private fun getDateKey(timestamp: Long): String {
    val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

private fun formatDateHeader(dateKey: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val outputFormat = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault())
        val date = inputFormat.parse(dateKey)
        if (date != null) {
            val today = Calendar.getInstance()
            val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
            val selfieDate = Calendar.getInstance().apply { time = date }

            when {
                isSameDay(today, selfieDate) -> "Today"
                isSameDay(yesterday, selfieDate) -> "Yesterday"
                else -> outputFormat.format(date)
            }
        } else {
            dateKey
        }
    } catch (e: Exception) {
        dateKey
    }
}

private fun isSameDay(cal1: Calendar, cal2: Calendar): Boolean {
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
            cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}
