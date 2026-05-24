package xdman.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import xdman.Config
import xdman.downloaders.metadata.HttpMetadata

@Composable
fun NewDownloadDialog(
    metadata: HttpMetadata?,
    fileName: String,
    folder: String?,
    onDismiss: () -> Unit,
    onStartDownload: (String?, String?, HttpMetadata, Boolean, String, Int, Int) -> Unit
) {
    var url by remember(metadata) { mutableStateOf(metadata?.url ?: "") }
    var saveAs by remember { mutableStateOf(fileName) }
    var saveTo by remember(folder) { mutableStateOf(folder ?: Config.getInstance().downloadFolder) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Download") },
        text = {
            Column(modifier = Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = saveAs,
                    onValueChange = { saveAs = it },
                    label = { Text("Save As") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = saveTo,
                    onValueChange = { saveTo = it },
                    label = { Text("Save To") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                if (url.isNotBlank()) {
                    val meta = metadata ?: HttpMetadata().apply { this.url = url }
                    if (meta.url != url) meta.url = url
                    onStartDownload(saveAs, saveTo, meta, true, "", 0, 0)
                }
            }) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
