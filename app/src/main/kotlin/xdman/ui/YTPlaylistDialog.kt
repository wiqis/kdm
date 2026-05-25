package xdman.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xdman.XDMApp
import xdman.downloaders.metadata.HttpMetadata
import xdman.ytdlp.YTDLP
import xdman.ytdlp.YTDLPManager
import xdman.ytdlp.YTFormat
import xdman.ytdlp.YTVideoInfo
import xdman.ytdlp.YTPlaylistInfo
import xdman.ytdlp.YTPlaylistEntry

@Composable
fun YTPlaylistDialog(onDismiss: () -> Unit) {
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var playlistInfo by remember { mutableStateOf<YTPlaylistInfo?>(null) }
    var selectedEntries by remember { mutableStateOf(setOf<String>()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var downloadStarted by remember { mutableStateOf(false) }
    var downloadCount by remember { mutableStateOf(0) }
    var formatChoice by remember { mutableStateOf("best") }
    var mergeEnabled by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    fun fetchPlaylist() {
        if (!YTDLPManager.isAvailable()) return
        scope.launch {
            loading = true
            errorMsg = null
            try {
                val info = withContext(Dispatchers.IO) {
                    YTDLP.getPlaylistInfo(url.trim(), flat = true)
                }
                playlistInfo = info
                selectedEntries = info.entries.map { it.id }.toSet()
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to fetch playlist info"
            }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading) onDismiss() },
        title = { Text("Download Playlist") },
        text = {
            Column(modifier = Modifier.width(560.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val c = MaterialTheme.colorScheme
                if (!YTDLPManager.isAvailable()) {
                    Text("yt-dlp is not installed.", fontSize = 12.sp, color = c.error)
                    Text("Go to Download > Setup yt-dlp to install it.", fontSize = 11.sp, color = c.onSurfaceVariant)
                } else if (downloadStarted) {
                    Icon(Icons.Default.CheckCircle, "Done", modifier = Modifier.size(48.dp), tint = Color(0xFF4CAF50))
                    Text("Playlist download started!", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("$downloadCount videos added to download list.", fontSize = 11.sp, color = c.onSurfaceVariant)
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; playlistInfo = null },
                        label = { Text("Playlist URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading
                    )

                    if (loading) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Fetching playlist info...", fontSize = 12.sp)
                        }
                    }

                    errorMsg?.let {
                        Text(it, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }

                    playlistInfo?.let { playlist ->
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlaylistPlay, "Playlist", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(playlist.title.ifBlank { "Playlist" }, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${playlist.entries.size} videos", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Spacer(Modifier.height(4.dp))

                        // Select/deselect all
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                selectedEntries = if (selectedEntries.size == playlist.entries.size) emptySet()
                                else playlist.entries.map { it.id }.toSet()
                            }) {
                                Text(if (selectedEntries.size == playlist.entries.size) "Deselect All" else "Select All", fontSize = 11.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("${selectedEntries.size} selected", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }

                        // Format choice
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Format:", fontSize = 11.sp)
                            FilterChip(selected = formatChoice == "best", onClick = { formatChoice = "best" }, label = { Text("Best", fontSize = 10.sp) })
                            FilterChip(selected = formatChoice == "bestvideo+bestaudio", onClick = { formatChoice = "bestvideo+bestaudio" }, label = { Text("Best AV", fontSize = 10.sp) })
                            FilterChip(selected = formatChoice == "mp4", onClick = { formatChoice = "mp4" }, label = { Text("MP4", fontSize = 10.sp) })
                        }

                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            items(playlist.entries) { entry ->
                                val checked = entry.id in selectedEntries
                                Surface(
                                    color = if (checked) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f) else MaterialTheme.colorScheme.surface,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    ) {
                                        Checkbox(checked = checked, onCheckedChange = {
                                            selectedEntries = if (checked) selectedEntries - entry.id else selectedEntries + entry.id
                                        }, modifier = Modifier.size(32.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(entry.title, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            if (entry.durationStr.isNotBlank()) {
                                                Text(entry.durationStr, fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (formatChoice == "bestvideo+bestaudio") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = mergeEnabled, onCheckedChange = { mergeEnabled = it })
                                Spacer(Modifier.width(4.dp))
                                Text("Merge audio & video after download", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                downloadStarted -> {
                    Button(onClick = onDismiss) { Text("Close") }
                }
                playlistInfo != null -> {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val selected = playlistInfo!!.entries.filter { it.id in selectedEntries }
                                    startPlaylistDownload(selected, formatChoice, mergeEnabled)
                                    downloadCount = selected.size
                                    downloadStarted = true
                                } catch (e: Exception) {
                                    errorMsg = e.message
                                }
                            }
                        },
                        enabled = selectedEntries.isNotEmpty()
                    ) { Text("Download ${selectedEntries.size} videos") }
                }
                else -> {
                    Button(
                        onClick = { fetchPlaylist() },
                        enabled = url.isNotBlank() && !loading && YTDLPManager.isAvailable()
                    ) { Text(if (loading) "Fetching..." else "Fetch Playlist") }
                }
            }
        },
        dismissButton = {
            if (!loading && !downloadStarted) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private suspend fun startPlaylistDownload(entries: List<YTPlaylistEntry>, formatChoice: String, merge: Boolean) {
    for (entry in entries) {
        try {
            val fmtIds = when (formatChoice) {
                "best" -> listOf("best[ext=mp4]", "best")
                "bestvideo+bestaudio" -> listOf("bestvideo+bestaudio/best")
                "mp4" -> listOf("best[ext=mp4]")
                else -> listOf(formatChoice)
            }

            val urls = withContext(Dispatchers.IO) {
                val videoUrl = entry.url
                val result = mutableMapOf<String, String>()
                for (fid in fmtIds) {
                    YTDLP.getDirectUrl(videoUrl, fid)?.let { result[fid] = it }
                }
                result
            }

            for ((fid, directUrl) in urls) {
                val meta = HttpMetadata().apply { url = directUrl }
                val safeTitle = entry.title.replace(Regex("""[<>:"/\\|?*]"""), "_")
                val ext = if (fid.contains("bestaudio")) "m4a" else "mp4"
                XDMApp.createDownload("$safeTitle.$ext", null, meta, true, "", 0, 0)
            }
        } catch (e: Exception) {
            xdman.util.Logger.log(e)
        }
    }
}
