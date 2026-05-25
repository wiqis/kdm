package xdman.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
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
import xdman.CombinedYTDownload
import xdman.Config
import xdman.XDMApp
import xdman.XDMConstants
import xdman.downloaders.metadata.HttpMetadata
import xdman.ytdlp.*
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YTPlaylistDialog(
    onCombinedCreated: (List<CombinedYTDownload>) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var playlistInfo by remember { mutableStateOf<YTPlaylistInfo?>(null) }
    var selectedEntries by remember { mutableStateOf(setOf<String>()) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var downloadStarted by remember { mutableStateOf(false) }
    var downloadCount by remember { mutableStateOf(0) }
    var formatChoice by remember { mutableStateOf("best") }
    var mergeEnabled by remember { mutableStateOf(true) }
    var minQuality by remember { mutableIntStateOf(0) }
    var maxQuality by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    val qualityOptions = listOf(0, 144, 240, 360, 480, 720, 1080, 1440, 2160)
    fun qualityLabel(h: Int) = if (h == 0) "Any" else "${h}p"
    fun formatFilter(): String {
        val parts = buildList {
            if (minQuality > 0) add("height>=$minQuality")
            if (maxQuality > 0) add("height<=$maxQuality")
        }
        return if (parts.isEmpty()) "" else parts.joinToString("")
    }

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
            Column(modifier = Modifier.width(560.dp).heightIn(max = 540.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val c = MaterialTheme.colorScheme
                if (!YTDLPManager.isAvailable()) {
                    Text("yt-dlp is not installed.", fontSize = 12.sp, color = c.error)
                    Text("Go to Download > Setup Tools to install it.", fontSize = 11.sp, color = c.onSurfaceVariant)
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

                    errorMsg?.let { Text(it, fontSize = 11.sp, color = c.error) }

                    playlistInfo?.let { playlist ->
                        HorizontalDivider()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistPlay, "Playlist", modifier = Modifier.size(20.dp), tint = c.primary)
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(playlist.title.ifBlank { "Playlist" }, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${playlist.entries.size} videos", fontSize = 11.sp, color = c.onSurfaceVariant)
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        Text("Format for all videos:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            FilterChip(selected = formatChoice == "best", onClick = { formatChoice = "best"; mergeEnabled = false },
                                label = { Text("Best (AV)", fontSize = 10.sp) })
                            FilterChip(selected = formatChoice == "video", onClick = { formatChoice = "video"; mergeEnabled = true },
                                label = { Text("Video + Audio", fontSize = 10.sp) })
                            FilterChip(selected = formatChoice == "mp4", onClick = { formatChoice = "mp4"; mergeEnabled = false },
                                label = { Text("MP4", fontSize = 10.sp) })
                            FilterChip(selected = formatChoice == "audio", onClick = { formatChoice = "audio"; mergeEnabled = false },
                                label = { Text("Audio only", fontSize = 10.sp) })
                        }
                        if (formatChoice == "video") {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(checked = mergeEnabled, onCheckedChange = { mergeEnabled = it })
                                Spacer(Modifier.width(4.dp))
                                Text("Merge after download", fontSize = 11.sp)
                            }
                            Text("Requires ffmpeg", fontSize = 9.sp, color = c.onSurfaceVariant)
                        }

                        if (formatChoice != "audio") {
                            Spacer(Modifier.height(4.dp))
                            Text("Quality range:", fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Column {
                                    Text("Min", fontSize = 9.sp, color = c.onSurfaceVariant)
                                    var expanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                        OutlinedTextField(
                                            value = qualityLabel(minQuality),
                                            onValueChange = {},
                                            readOnly = true,
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                                            modifier = Modifier.width(90.dp).menuAnchor(),
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                                        )
                                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            qualityOptions.forEach { q ->
                                                DropdownMenuItem(
                                                    text = { Text(qualityLabel(q), fontSize = 11.sp) },
                                                    onClick = { minQuality = q; expanded = false },
                                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                                )
                                            }
                                        }
                                    }
                                }
                                Column {
                                    Text("Max", fontSize = 9.sp, color = c.onSurfaceVariant)
                                    var expanded by remember { mutableStateOf(false) }
                                    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                                        OutlinedTextField(
                                            value = qualityLabel(maxQuality),
                                            onValueChange = {},
                                            readOnly = true,
                                            singleLine = true,
                                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                                            modifier = Modifier.width(90.dp).menuAnchor(),
                                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
                                        )
                                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                            qualityOptions.forEach { q ->
                                                DropdownMenuItem(
                                                    text = { Text(qualityLabel(q), fontSize = 11.sp) },
                                                    onClick = { maxQuality = q; expanded = false },
                                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = {
                                selectedEntries = if (selectedEntries.size == playlist.entries.size) emptySet()
                                else playlist.entries.map { it.id }.toSet()
                            }) {
                                Text(if (selectedEntries.size == playlist.entries.size) "Deselect All" else "Select All", fontSize = 11.sp)
                            }
                            Spacer(Modifier.weight(1f))
                            Text("${selectedEntries.size} selected", fontSize = 11.sp, color = c.onSurfaceVariant)
                        }

                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            items(playlist.entries) { entry ->
                                val checked = entry.id in selectedEntries
                                Surface(
                                    color = if (checked) c.primary.copy(alpha = 0.06f) else c.surface,
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
                                                Text(entry.durationStr, fontSize = 9.sp, color = c.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when {
                downloadStarted -> Button(onClick = onDismiss) { Text("Close") }
                playlistInfo != null -> {
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val selected = playlistInfo!!.entries.filter { it.id in selectedEntries }
                                    val combinedEntries = withContext(Dispatchers.IO) {
                                        startPlaylistDownload(selected, formatChoice, mergeEnabled, minQuality, maxQuality)
                                    }
                                    onCombinedCreated(combinedEntries)
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
                else -> Button(
                    onClick = { fetchPlaylist() },
                    enabled = url.isNotBlank() && !loading && YTDLPManager.isAvailable()
                ) { Text(if (loading) "Fetching..." else "Fetch Playlist") }
            }
        },
        dismissButton = {
            if (!loading && !downloadStarted) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private suspend fun startPlaylistDownload(
    entries: List<YTPlaylistEntry>, formatChoice: String, merge: Boolean,
    minQuality: Int, maxQuality: Int
): List<CombinedYTDownload> {
    val filter = buildList {
        if (minQuality > 0) add("height>=$minQuality")
        if (maxQuality > 0) add("height<=$maxQuality")
    }.joinToString("")
    val results = mutableListOf<CombinedYTDownload>()
    for (entry in entries) {
        try {
            val result = when (formatChoice) {
                "best" -> downloadPlaylistEntry(entry, "best[ext=mp4]$filter/best$filter", null, false)
                "video" -> downloadPlaylistEntry(entry, "bestvideo$filter", "bestaudio", merge)
                "mp4" -> downloadPlaylistEntry(entry, "best[ext=mp4]$filter", null, false)
                "audio" -> downloadPlaylistEntry(entry, "bestaudio", null, false)
                else -> downloadPlaylistEntry(entry, formatChoice, null, false)
            }
            result?.let { results.add(it) }
        } catch (e: Exception) {
            xdman.util.Logger.log(e)
        }
    }
    return results
}

private suspend fun downloadPlaylistEntry(
    entry: YTPlaylistEntry, videoFmt: String, audioFmt: String?, merge: Boolean
): CombinedYTDownload? {
    val safeTitle = entry.title.replace(Regex("""[<>:"/\\|?*]"""), "_")
    val videoUrl = YTDLP.getDirectUrl(entry.url, videoFmt) ?: return null

    if (audioFmt != null && merge) {
        val audioUrl = YTDLP.getDirectUrl(entry.url, audioFmt) ?: return null
        val ytTemp = File(Config.getInstance().dataFolder, "yt-temp").also { it.mkdirs() }
        val videoMeta = HttpMetadata().apply { url = videoUrl }
        val audioMeta = HttpMetadata().apply { url = audioUrl }
        val vExt = "mp4"
        val aExt = "m4a"

        XDMApp.createDownload("${safeTitle}_v.$vExt", ytTemp.absolutePath, videoMeta, true, "", 0, 0, XDMConstants.VIDEO)
        XDMApp.createDownload("${safeTitle}_a.$aExt", ytTemp.absolutePath, audioMeta, true, "", 0, 0, XDMConstants.MUSIC)

        val outputFolder = XDMApp.getFolder(XDMConstants.VIDEO)
        YTMergeTracker.registerMerge(
            baseName = safeTitle,
            videoDownloadId = videoMeta.id,
            audioDownloadId = audioMeta.id,
            videoExt = vExt,
            audioExt = aExt,
            tempFolder = ytTemp.absolutePath,
            outputFolder = outputFolder
        )

        return CombinedYTDownload(
            combinedId = safeTitle,
            title = safeTitle,
            videoEntryId = videoMeta.id,
            audioEntryId = audioMeta.id,
            videoExt = vExt,
            audioExt = aExt,
            tempFolder = ytTemp.absolutePath,
            outputFolder = outputFolder
        )
    } else {
        val meta = HttpMetadata().apply { url = videoUrl }
        val cat = if (audioFmt != null || videoFmt.contains("audio")) XDMConstants.MUSIC else XDMConstants.VIDEO
        XDMApp.createDownload("$safeTitle.${
            when {
                videoFmt.contains("audio") -> "m4a"
                else -> "mp4"
            }
        }", null, meta, true, "", 0, 0, cat)
        return null
    }
}
