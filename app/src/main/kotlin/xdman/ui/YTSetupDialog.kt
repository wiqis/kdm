package xdman.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import xdman.Config
import xdman.ytdlp.FFmpegManager
import xdman.ytdlp.YTDLPManager
import java.io.File

@Composable
fun YTSetupDialog(onDismiss: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    var ytStatus by remember { mutableStateOf("") }
    var ytInstalling by remember { mutableStateOf(false) }
    var ytDone by remember { mutableStateOf(false) }
    var ytError by remember { mutableStateOf<String?>(null) }
    var ytProgress by remember { mutableStateOf(0f) }
    var ytStatusText by remember { mutableStateOf("") }

    var ffStatus by remember { mutableStateOf("") }
    var ffInstalling by remember { mutableStateOf(false) }
    var ffDone by remember { mutableStateOf(false) }
    var ffError by remember { mutableStateOf<String?>(null) }
    var ffProgress by remember { mutableStateOf(0f) }
    var ffStatusText by remember { mutableStateOf("") }

    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        ytStatus = if (YTDLPManager.isAvailable()) "yt-dlp ${YTDLPManager.getVersion()}" else "Not installed"
        ytDone = YTDLPManager.isAvailable()
        ffStatus = if (FFmpegManager.isAvailable()) "ffmpeg ${FFmpegManager.getVersion()}" else "Not installed"
        ffDone = FFmpegManager.isAvailable()
    }

    AlertDialog(
        onDismissRequest = { if (!ytInstalling && !ffInstalling) onDismiss() },
        title = { Text("Setup Tools") },
        text = {
            Column(modifier = Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = tab == 0, onClick = { tab = 0 }, label = { Text("yt-dlp", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.Download, null, modifier = Modifier.size(16.dp)) })
                    FilterChip(selected = tab == 1, onClick = { tab = 1 }, label = { Text("FFmpeg", fontSize = 11.sp) },
                        leadingIcon = { Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(16.dp)) })
                }

                when (tab) {
                    0 -> YTSetupSection(
                        status = ytStatus, installing = ytInstalling, done = ytDone,
                        errorMsg = ytError, progress = ytProgress, statusText = ytStatusText,
                        installLabel = "Install yt-dlp",
                        location = YTDLPManager.getBinaryFile().absolutePath,
                        onInstall = {
                            ytInstalling = true; ytError = null; ytStatus = "Downloading yt-dlp..."
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        YTDLPManager.downloadBinary { dl, total ->
                                            ytProgress = if (total > 0) dl.toFloat() / total else 0f
                                            ytStatusText = "${dl / 1024} KB${if (total > 0) " / ${total / 1024} KB" else ""}"
                                        }
                                    }
                                    ytStatus = "yt-dlp ${YTDLPManager.getVersion()}"
                                    ytDone = true
                                } catch (e: Exception) { ytError = e.message }
                                ytInstalling = false
                            }
                        }
                    )
                    1 -> YTSetupSection(
                        status = ffStatus, installing = ffInstalling, done = ffDone,
                        errorMsg = ffError, progress = ffProgress, statusText = ffStatusText,
                        installLabel = "Install ffmpeg",
                        location = FFmpegManager.getBinaryFile()?.absolutePath ?: "Not found",
                        onInstall = {
                            ffInstalling = true; ffError = null; ffStatus = "Downloading ffmpeg..."
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        FFmpegManager.downloadBinary { dl, total ->
                                            ffProgress = if (total > 0) dl.toFloat() / total else 0f
                                            ffStatusText = "${dl / 1024} KB${if (total > 0) " / ${total / 1024} KB" else ""}"
                                        }
                                    }
                                    ffStatus = "ffmpeg ${FFmpegManager.getVersion()}"
                                    ffDone = true
                                } catch (e: Exception) { ffError = e.message }
                                ffInstalling = false
                            }
                        }
                    )
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } },
        dismissButton = {
            if (!ytInstalling && !ffInstalling) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun YTSetupSection(
    status: String, installing: Boolean, done: Boolean,
    errorMsg: String?, progress: Float, statusText: String,
    installLabel: String, location: String,
    onInstall: () -> Unit
) {
    val c = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (installing) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
            Text(statusText, fontSize = 11.sp, color = c.onSurfaceVariant)
        }

        Text(status, fontSize = 13.sp,
            fontWeight = if (done) FontWeight.Normal else FontWeight.Bold,
            color = if (done) Color(0xFF4CAF50) else c.onSurface)

        errorMsg?.let { Text(it, fontSize = 11.sp, color = c.error) }

        if (done) {
            Text("Ready to use.", fontSize = 11.sp, color = c.onSurfaceVariant)
        } else if (!installing) {
            Text("Click Install to download automatically.", fontSize = 11.sp, color = c.onSurfaceVariant)
            Text("Location: $location", fontSize = 9.sp, color = c.onSurfaceVariant)
            Button(onClick = onInstall, modifier = Modifier.padding(top = 4.dp)) { Text(installLabel) }
        }
    }
}
