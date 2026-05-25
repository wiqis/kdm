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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    
    val colorScheme = if (appState.darkMode) {
        darkColorScheme(
            primary = accentColor,
            onPrimary = Color.Black,
            background = darkBg,
            surface = darkSurface,
            surfaceVariant = darkSurfaceVariant,
            onBackground = textPrimary,
            onSurface = textPrimary,
            onSurfaceVariant = textSecondary
        )
    } else {
        lightColorScheme(
            primary = accentColor,
            onPrimary = Color.White,
            background = Color(0xFFF5F5F5),
            surface = Color.White,
            surfaceVariant = Color(0xFFE0E0E0),
            onBackground = Color(0xFF212121),
            onSurface = Color(0xFF212121),
            onSurfaceVariant = Color(0xFF757575)
        )
    }

    Window(
        onCloseRequest = { appState.hideProgress(id) },
        title = entry?.file ?: "Downloading...",
        state = rememberWindowState(position = WindowPosition.Aligned(Alignment.Center)),
        resizable = false
    ) {
        window.minimumSize = Dimension(400, 340)
        window.preferredSize = Dimension(400, 340)

        MaterialTheme(colorScheme = colorScheme) {
            val bg = MaterialTheme.colorScheme.background
            val txtPrimary = MaterialTheme.colorScheme.onSurface
            val txtSecondary = MaterialTheme.colorScheme.onSurfaceVariant
            val variant = MaterialTheme.colorScheme.surfaceVariant
            
            Column(
                modifier = Modifier.fillMaxSize().background(bg).padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // File name
                Text(
                    entry?.file ?: "Unknown",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = txtPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(4.dp))

                // URL
                Text(
                    "URL: ${XDMApp.getURL(id)}",
                    fontSize = 10.sp,
                    color = txtSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    "Save to: ${if (entry != null) XDMApp.getFolder(entry) else ""}",
                    fontSize = 10.sp,
                    color = txtSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                // Circular progress
                CircularProgress(
                    progress = progress.progress,
                    modifier = Modifier.size(80.dp),
                    trackColor = variant,
                    progressColor = accentColor
                )
                Spacer(Modifier.height(4.dp))
                Text("${progress.progress}%", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = accentColor)

                Spacer(Modifier.height(8.dp))

                // Size info
                Text(
                    "${FormatUtilities.formatSize(progress.downloaded.toDouble())} / ${FormatUtilities.formatSize(progress.size.toDouble())}",
                    fontSize = 11.sp,
                    color = txtSecondary
                )

                Spacer(Modifier.height(8.dp))

                // Segment progress bar
                val segDet = XDMApp.getSegmentDetails(id)
                if (segDet != null && segDet.getChunkCount() > 0) {
                    SegmentProgressView(
                        segDet = segDet,
                        totalLength = progress.size,
                        modifier = Modifier.fillMaxWidth().height(16.dp),
                        trackColor = variant,
                        segmentColor = segmentColor
                    )
                    Spacer(Modifier.height(4.dp))
                    Text("${segDet.getChunkCount()} segments", fontSize = 10.sp, color = txtSecondary)
                } else {
                    // Fallback linear progress
                    LinearProgressIndicator(
                        progress = { progress.progress / 100.0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = downloadingColor,
                        trackColor = variant
                    )
                }

                Spacer(Modifier.height(8.dp))

                // Speed and ETA
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Speed", fontSize = 10.sp, color = txtSecondary)
                        Text(if (progress.speed > 0) "${FormatUtilities.formatSize(progress.speed.toDouble())}/s" else "---",
                            fontSize = 11.sp, color = txtPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("ETA", fontSize = 10.sp, color = txtSecondary)
                        Text(progress.eta.ifEmpty { "---" }, fontSize = 11.sp, color = txtPrimary)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        val elapsed = if (progress.elapsed > 0) formatDuration(progress.elapsed) else "---"
                        Text("Elapsed", fontSize = 10.sp, color = txtSecondary)
                        Text(elapsed, fontSize = 11.sp, color = txtPrimary)
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
                            IconButton(
                                onClick = { XDMApp.pauseDownload(id) },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = pausedColor),
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.Pause, "Pause", tint = Color.Black, modifier = Modifier.size(18.dp)) }
                        }
                        state == XDMConstants.PAUSED || state == XDMConstants.FAILED -> {
                            IconButton(
                                onClick = { XDMApp.resumeDownload(id, true) },
                                colors = IconButtonDefaults.iconButtonColors(containerColor = downloadingColor),
                                modifier = Modifier.size(32.dp)
                            ) { Icon(Icons.Default.PlayArrow, "Resume", tint = Color.White, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    IconButton(
                        onClick = { appState.hideProgress(id) },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = Color(0xFFF44336)),
                        modifier = Modifier.size(32.dp)
                    ) { Icon(Icons.Default.Close, "Close", tint = Color.White, modifier = Modifier.size(18.dp)) }
                    if (entry != null) {
                        IconButton(
                            onClick = {
                                try {
                                    XDMUtils.openFolder(null, XDMApp.getFolder(entry))
                                } catch (_: Exception) {}
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = variant),
                            modifier = Modifier.size(32.dp)
                        ) { Icon(Icons.Default.Folder, "Open Folder", modifier = Modifier.size(18.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CircularProgress(progress: Int, trackColor: Color, progressColor: Color, modifier: Modifier = Modifier) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 6f
            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                topLeft = Offset(strokeWidth / 2, strokeWidth / 2)
            )
            drawArc(
                color = progressColor,
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
    trackColor: Color,
    segmentColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.background(trackColor, shape = CircleShape)) {
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
