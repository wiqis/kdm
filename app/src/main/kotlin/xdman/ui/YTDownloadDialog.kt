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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xdman.CombinedYTDownload
import xdman.Config
import xdman.XDMApp
import xdman.XDMConstants
import xdman.downloaders.metadata.HttpMetadata
import xdman.ytdlp.*

@Composable
fun YTDownloadDialog(
    onCombinedCreated: (CombinedYTDownload) -> Unit,
    onDismiss: () -> Unit
) {
    var url by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var startingDownload by remember { mutableStateOf(false) }
    var videoInfo by remember { mutableStateOf<YTVideoInfo?>(null) }
    var selectedVideoFmt by remember { mutableStateOf<String?>(null) }
    var selectedAudioFmt by remember { mutableStateOf<String?>(null) }
    var mergeEnabled by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var downloadStarted by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun fetchInfo() {
        if (!YTDLPManager.isAvailable()) return
        scope.launch {
            loading = true
            errorMsg = null
            try {
                val info = withContext(Dispatchers.IO) { YTDLP.getVideoInfo(url.trim()) }
                videoInfo = info
                val best = info.bestCombinedFormat
                selectedVideoFmt = best?.formatId ?: info.bestVideoOnly?.formatId
                selectedAudioFmt = info.bestAudioOnly?.formatId
                mergeEnabled = best == null
            } catch (e: Exception) {
                errorMsg = e.message ?: "Failed to fetch video info"
            }
            loading = false
        }
    }

    AlertDialog(
        onDismissRequest = { if (!loading && !startingDownload) onDismiss() },
        title = { Text("Download Video") },
        text = {
            Column(modifier = Modifier.width(520.dp).heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val c = MaterialTheme.colorScheme
                if (!YTDLPManager.isAvailable()) {
                    Text("yt-dlp is not installed.", fontSize = 12.sp, color = c.error)
                    Text("Go to Download > Setup Tools to install it.", fontSize = 11.sp, color = c.onSurfaceVariant)
                } else if (downloadStarted) {
                    Icon(Icons.Default.CheckCircle, "Done", modifier = Modifier.size(48.dp), tint = Color(0xFF4CAF50))
                    Text("Video download started!", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("Check the download list for combined progress.", fontSize = 11.sp, color = c.onSurfaceVariant)
                } else {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it; videoInfo = null; selectedVideoFmt = null; selectedAudioFmt = null },
                        label = { Text("YouTube URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !loading && !startingDownload
                    )

                    if (loading || startingDownload) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (startingDownload) "Starting download..." else "Fetching video info...", fontSize = 12.sp)
                        }
                    }

                    errorMsg?.let { Text(it, fontSize = 11.sp, color = c.error) }

                    videoInfo?.let { info ->
                        HorizontalDivider()
                        Text(info.title, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text("Duration: ${info.durationStr}", fontSize = 11.sp, color = c.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))

                        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            val videoFormats = info.formats.filter { it.isCombined || it.isVideoOnly }
                                .sortedByDescending { it.height }
                            val audioFormats = info.formats.filter { it.isAudioOnly }
                                .sortedByDescending { it.tbr }

                            item {
                                Text("VIDEO FORMAT", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                    color = c.primary, modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp))
                            }
                            items(videoFormats) { fmt ->
                                val isSelected = fmt.formatId == selectedVideoFmt
                                Surface(
                                    color = if (isSelected) c.primary.copy(alpha = 0.12f) else c.surface,
                                    modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        RadioButton(selected = isSelected, onClick = {
                                            selectedVideoFmt = fmt.formatId
                                            mergeEnabled = !fmt.isCombined
                                        })
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(fmt.qualityLabel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            val sizeLabel = when {
                                                fmt.filesize > 0 -> formatFileSize(fmt.filesize)
                                                fmt.filesizeApprox > 0 -> "~${formatFileSize(fmt.filesizeApprox)}"
                                                else -> ""
                                            }
                                            if (sizeLabel.isNotBlank() || fmt.tbr > 0) {
                                                Text("$sizeLabel${if (sizeLabel.isNotBlank() && fmt.tbr > 0) " • " else ""}${if (fmt.tbr > 0) "${fmt.tbr.toInt()}kbps" else ""}",
                                                    fontSize = 9.sp, color = c.onSurfaceVariant)
                                            }
                                        }
                                    }
                                }
                            }

                            val showAudio = selectedVideoFmt != null && info.formats.find { it.formatId == selectedVideoFmt }?.isVideoOnly == true
                            if (showAudio && audioFormats.isNotEmpty()) {
                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Text("AUDIO FORMAT", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                        color = c.primary, modifier = Modifier.padding(vertical = 4.dp, horizontal = 4.dp))
                                }
                                items(audioFormats) { fmt ->
                                    val isSelected = fmt.formatId == selectedAudioFmt
                                    Surface(
                                        color = if (isSelected) c.primary.copy(alpha = 0.12f) else c.surface,
                                        modifier = Modifier.fillMaxWidth().heightIn(min = 36.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier.padding(horizontal = 8.dp)
                                        ) {
                                            RadioButton(selected = isSelected, onClick = {
                                                selectedAudioFmt = fmt.formatId
                                            })
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(fmt.qualityLabel, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                val sizeLabel = when {
                                                    fmt.filesize > 0 -> formatFileSize(fmt.filesize)
                                                    fmt.filesizeApprox > 0 -> "~${formatFileSize(fmt.filesizeApprox)}"
                                                    else -> ""
                                                }
                                                if (sizeLabel.isNotBlank() || fmt.tbr > 0) {
                                                    Text("$sizeLabel${if (sizeLabel.isNotBlank() && fmt.tbr > 0) " • " else ""}${if (fmt.tbr > 0) "${fmt.tbr.toInt()}kbps" else ""}",
                                                        fontSize = 9.sp, color = c.onSurfaceVariant)
                                                }
                                            }
                                        }
                                    }
                                }

                                item {
                                    Spacer(Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(checked = mergeEnabled, onCheckedChange = { mergeEnabled = it })
                                        Spacer(Modifier.width(4.dp))
                                        Text("Merge audio & video after download", fontSize = 11.sp)
                                    }
                                    Text("Requires ffmpeg", fontSize = 9.sp, color = c.onSurfaceVariant)
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
                videoInfo != null -> {
                    Button(
                        onClick = {
                            val videoFmt = selectedVideoFmt?.let { id -> videoInfo!!.formats.find { it.formatId == id } }
                            if (videoFmt != null) {
                                scope.launch {
                                    startingDownload = true
                                    errorMsg = null
                                    try {
                                        val result = withContext(Dispatchers.IO) {
                                            startYoutubeDownload(videoInfo!!, videoFmt, selectedAudioFmt, mergeEnabled)
                                        }
                                        result?.let { onCombinedCreated(it) }
                                        downloadStarted = true
                                    } catch (e: Exception) {
                                        errorMsg = e.message ?: "Download failed"
                                    }
                                    startingDownload = false
                                }
                            }
                        },
                        enabled = selectedVideoFmt != null && !startingDownload
                    ) { Text(if (startingDownload) "Starting..." else "Download") }
                }
                else -> Button(
                    onClick = { fetchInfo() },
                    enabled = url.isNotBlank() && !loading && YTDLPManager.isAvailable()
                ) { Text(if (loading) "Fetching..." else "Fetch Info") }
            }
        },
        dismissButton = {
            if (!loading && !startingDownload && !downloadStarted) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private suspend fun startYoutubeDownload(
    info: YTVideoInfo, videoFmt: YTFormat, audioFmtId: String?, merge: Boolean
): CombinedYTDownload? {
    val safeTitle = info.title.replace(Regex("""[<>:"/\\|?*]"""), "_")

    if (videoFmt.isCombined) {
        val url = YTDLP.getDirectUrl(info.webpageUrl, videoFmt.formatId)
        if (url != null) {
            val meta = HttpMetadata().apply { this.url = url }
            XDMApp.createDownload("$safeTitle.${videoFmt.ext}", null, meta, true, "", 0, 0, XDMConstants.VIDEO)
        }
        return null
    }

    if (videoFmt.isVideoOnly && audioFmtId != null && merge) {
        val audioFmt = info.formats.find { it.formatId == audioFmtId }
        if (audioFmt != null) {
            val urls = YTDLP.getDirectUrls(info.webpageUrl, listOf(videoFmt.formatId, audioFmt.formatId))
            val videoUrl = urls[videoFmt.formatId]
            val audioUrl = urls[audioFmt.formatId]

            if (videoUrl != null && audioUrl != null) {
                val ytTemp = File(Config.getInstance().dataFolder, "yt-temp").also { it.mkdirs() }
                val videoMeta = HttpMetadata().apply { url = videoUrl }
                val audioMeta = HttpMetadata().apply { url = audioUrl }

                XDMApp.createDownload("${safeTitle}_v.${videoFmt.ext}", ytTemp.absolutePath, videoMeta, true, "", 0, 0, XDMConstants.VIDEO)
                XDMApp.createDownload("${safeTitle}_a.${audioFmt.ext}", ytTemp.absolutePath, audioMeta, true, "", 0, 0, XDMConstants.MUSIC)

                val outputFolder = XDMApp.getFolder(XDMConstants.VIDEO)
                YTMergeTracker.registerMerge(
                    baseName = safeTitle,
                    videoDownloadId = videoMeta.id,
                    audioDownloadId = audioMeta.id,
                    videoExt = videoFmt.ext,
                    audioExt = audioFmt.ext,
                    tempFolder = ytTemp.absolutePath,
                    outputFolder = outputFolder
                )

                return CombinedYTDownload(
                    combinedId = safeTitle,
                    title = safeTitle,
                    videoEntryId = videoMeta.id,
                    audioEntryId = audioMeta.id,
                    videoExt = videoFmt.ext,
                    audioExt = audioFmt.ext,
                    tempFolder = ytTemp.absolutePath,
                    outputFolder = outputFolder
                )
            }
        }
    }

    val url = YTDLP.getDirectUrl(info.webpageUrl, videoFmt.formatId)
    if (url != null) {
        val meta = HttpMetadata().apply { this.url = url }
        val cat = if (videoFmt.isAudioOnly) XDMConstants.MUSIC else XDMConstants.VIDEO
        XDMApp.createDownload("$safeTitle.${videoFmt.ext}", null, meta, true, "", 0, 0, cat)
    }
    return null
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes >= 1_000_000_000 -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.1f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }
}
