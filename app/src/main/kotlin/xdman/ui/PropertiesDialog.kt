package xdman.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xdman.*
import xdman.downloaders.metadata.HttpMetadata
import xdman.downloaders.metadata.DashMetadata
import xdman.util.FormatUtilities
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PropertiesDialog(id: String, onDismiss: () -> Unit) {
    val entry = XDMApp.getEntry(id)
    val metadata = remember { try { HttpMetadata.load(id) } catch (_: Exception) { null } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Download Properties") },
        text = {
            Column(
                modifier = Modifier.width(450.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (entry == null) {
                    Text("Download not found")
                    return@AlertDialog
                }

                PropertyRow("File Name", entry.file ?: "Unknown")
                PropertyRow("URL", metadata?.url ?: XDMApp.getURL(id))

                val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm:ss") }
                PropertyRow("Added", dateFormat.format(Date(entry.date)))
                PropertyRow("Category", categoryName(entry.category))
                PropertyRow("Size", FormatUtilities.formatSize(entry.size.toDouble()))
                PropertyRow("Downloaded", FormatUtilities.formatSize(entry.downloaded.toDouble()))
                PropertyRow("Progress", "${entry.progress}%")
                PropertyRow("Status", when (entry.state) {
                    XDMConstants.DOWNLOADING -> "Downloading"
                    XDMConstants.PAUSED -> "Paused"
                    XDMConstants.FAILED -> "Failed"
                    XDMConstants.FINISHED -> "Finished"
                    XDMConstants.ASSEMBLING -> "Assembling"
                    else -> "Unknown"
                })
                PropertyRow("Folder", XDMApp.getFolder(entry))

                if (entry.tempFolder != null) {
                    PropertyRow("Temp Folder", entry.tempFolder)
                }
                if (entry.queueId != null && entry.queueId.isNotEmpty()) {
                    PropertyRow("Queue", entry.queueId)
                }

                if (metadata is DashMetadata) {
                    PropertyRow("URL 2", metadata.url2 ?: "N/A")
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun PropertyRow(label: String, value: String) {
    Row {
        Text("$label:  ", fontWeight = FontWeight.Medium, fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onSurface)
        Text(value, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun categoryName(cat: Int): String = when (cat) {
    XDMConstants.VIDEO -> "Video"
    XDMConstants.MUSIC -> "Music"
    XDMConstants.DOCUMENTS -> "Documents"
    XDMConstants.PROGRAMS -> "Programs"
    XDMConstants.COMPRESSED -> "Compressed"
    XDMConstants.OTHER -> "Other"
    else -> "All"
}
