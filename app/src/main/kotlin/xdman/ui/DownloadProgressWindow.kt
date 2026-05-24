package xdman.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import xdman.*
import xdman.downloaders.SegmentDetails
import xdman.util.FormatUtilities
import xdman.util.XDMUtils
import java.awt.Dimension

private val darkBg = Color(0xFF1E1E1E)
private val darkSurface = Color(0xFF2D2D2D)
private val darkSurfaceVariant = Color(0xFF3C3C3C)
private val accentColor = Color(0xFFFF9800)
private val textPrimary = Color(0xFFE0E0E0)
private val textSecondary = Color(0xFF9E9E9E)
private val downloadingColor = Color(0xFF2196F3)
private val pausedColor = Color(0xFFFFC107)
private val segmentColor = Color(0xFF4CAF50)

data class SegmentInfoData(
    val start: Long = 0,
    val length: Long = 0,
    val downloaded: Long = 0
)

@Composable
fun DownloadProgressWindow(id: String, appState: XDMAppUIState) {
    val progress = appState.getProgress(id)
    val entry = XDMApp.getEntry(id)

    Window(
        onCloseRequest = { appState.hideProgress(id) },
        title = entry?.file ?: "Downloading...",
        state = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center)),
        resizable = false
    ) {
        window.minimumSize = Dimension(400, 340)
        window.preferredSize = Dimension(400, 340)

        MaterialTheme(colorScheme = darkColorScheme(
            primary = accentColor,
            onPrimary = Color.Black,
            background = darkBg,
            surface = darkSurface,
            surfaceVariant = darkSurfaceVariant,
            onBackground = textPrimary,
            onSurface = textPrimary,
            onSurfaceVariant = textSecondary
        )) {
            Column(
                modifier = Modifier.fillMaxSize().background(darkBg).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // File name
                Text(
                    entry?.file ?: "Unknown",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))

                // URL
                Text(
                    "URL: ${XDMApp.getURL(id)}",
                    fontSize = 10.sp,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Save to: ${if (entry != null) XDMApp.getFolder(entry) else ""}",
                    fontSize = 10.sp,
                    color = textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Circular progress
                CircularProgress(
                    progress = progress.progress,
                    modifier = Modifier.size(80.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text("${progress.progress}%", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accentColor)

                Spacer(Modifier.height(8.dp))

                // Size info
                Text(
                    "${FormatUtilities.formatSize(progress.downloaded.toDouble())} / ${FormatUtilities.formatSize(progress.size.toDouble())}",
                    fontSize = 11.sp,
                    color = textSecondary
                )

                Spacer(Modifier.height(8.dp))

                // Segment progress bar
                val segDet = XDMApp.getSegmentDetails(id)
                if (segDet != null && segDet.getChunkCount() > 0) {
                    SegmentProgressView(
                        segDet = segDet,
                        totalLength = progress.size,
                        modifier = Modifier.fillMaxWidth().height(16.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${segDet.getChunkCount()} segments", fontSize = 10.sp, color = textSecondary)
                } else {
                    // Fallback linear progress
                    LinearProgressIndicator(
                        progress = { progress.progress / 100.0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = downloadingColor,
                        trackColor = darkSurfaceVariant
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Speed and ETA
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Speed", fontSize = 10.sp, color = textSecondary)
                        Text(if (progress.speed > 0) "${FormatUtilities.formatSize(progress.speed.toDouble())}/s" else "---",
                            fontSize = 11.sp, color = textPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ETA", fontSize = 10.sp, color = textSecondary)
                        Text(progress.eta.ifEmpty { "---" }, fontSize = 11.sp, color = textPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val elapsed = if (progress.elapsed > 0) formatDuration(progress.elapsed) else "---"
                        Text("Elapsed", fontSize = 10.sp, color = textSecondary)
                        Text(elapsed, fontSize = 11.sp, color = textPrimary)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
                ) {
                    val state = entry?.state ?: XDMConstants.PAUSED
                    when {
                        state == XDMConstants.DOWNLOADING || state == XDMConstants.ASSEMBLING -> {
                            Button(
                                onClick = { XDMApp.pauseDownload(id) },
                                colors = ButtonDefaults.buttonColors(containerColor = pausedColor),
                                modifier = Modifier.height(32.dp)
                            ) { Text("Pause", fontSize = 11.sp, color = Color.Black) }
                        }
                        state == XDMConstants.PAUSED || state == XDMConstants.FAILED -> {
                            Button(
                                onClick = { XDMApp.resumeDownload(id, true) },
                                colors = ButtonDefaults.buttonColors(containerColor = downloadingColor),
                                modifier = Modifier.height(32.dp)
                            ) { Text("Resume", fontSize = 11.sp, color = Color.White) }
                        }
                    }
                    Button(
                        onClick = { appState.hideProgress(id) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336)),
                        modifier = Modifier.height(32.dp)
                    ) { Text("Close", fontSize = 11.sp, color = Color.White) }
                    if (entry != null) {
                        Button(
                            onClick = {
                                try {
                                    XDMUtils.openFolder(null, XDMApp.getFolder(entry))
                                } catch (_: Exception) {}
                            },
                            modifier = Modifier.height(32.dp)
                        ) { Text("Open Folder", fontSize = 11.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularProgress(progress: Int, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6f
            drawArc(
                color = darkSurfaceVariant,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            )
            drawArc(
                color = accentColor,
                startAngle = -90f,
                sweepAngle = (progress / 100f) * 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            )
        }
    }
}

@Composable
private fun SegmentProgressView(
    segDet: SegmentDetails,
    totalLength: Long,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.background(darkSurfaceVariant, shape = CircleShape)) {
        if (totalLength <= 0 || segDet.getChunkCount() <= 0) return@Canvas
        val segmentList = segDet.getChunkUpdates()
        val totalW = size.width
        val totalH = size.height
        val scale = totalW / totalLength.toFloat()

        for (i in 0 until segDet.getChunkCount().toInt()) {
            if (i >= segmentList.size) break
            val info = segmentList[i]
            val start = info.start * scale
            val len = info.length * scale
            val dwn = info.downloaded * scale
            if (dwn > len) continue
            // Draw segment outline
            drawRect(
                color = Color.Gray,
                topLeft = Offset(start, 0f),
                size = Size(len, totalH)
            )
            // Draw downloaded portion
            drawRect(
                color = segmentColor,
                topLeft = Offset(start, 0f),
                size = Size(dwn.coerceAtMost(len), totalH)
            )
        }
    }
}

private fun formatDuration(millis: Long): String {
    val secs = millis / 1000
    val m = (secs / 60) % 60
    val s = secs % 60
    val h = secs / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
