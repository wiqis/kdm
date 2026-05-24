package xdman.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import xdman.Config
import xdman.downloaders.metadata.HttpMetadata
import xdman.util.XDMUtils
import javax.swing.JFileChooser

@Composable
fun NewDownloadDialog(
    metadata: HttpMetadata?,
    fileName: String,
    folder: String?,
    onDismiss: () -> Unit,
    onStartDownload: (String?, String?, HttpMetadata, Boolean, String, Int, Int) -> Unit
) {
    var url by remember(metadata) { mutableStateOf(metadata?.url ?: "") }
    val detectedName = remember(url) { XDMUtils.getFileName(url) }
    var fileNameText by remember { mutableStateOf(fileName.ifEmpty { detectedName }) }
    var saveTo by remember(folder) { mutableStateOf(folder ?: Config.getInstance().downloadFolder) }

    LaunchedEffect(detectedName) {
        if (fileNameText.isEmpty() || fileNameText == detectedName || fileNameText.isBlank()) {
            fileNameText = detectedName
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Download") },
        text = {
            Column(modifier = Modifier.width(420.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = fileNameText,
                    onValueChange = { fileNameText = it },
                    label = { Text("Save As") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = saveTo,
                        onValueChange = { saveTo = it },
                        label = { Text("Save To") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(4.dp))
                    Button(
                        onClick = {
                            val chooser = JFileChooser(saveTo)
                            chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                            chooser.isAcceptAllFileFilterUsed = false
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                saveTo = chooser.selectedFile.absolutePath
                            }
                        },
                        modifier = Modifier.height(56.dp)
                    ) { Text("Browse", fontSize = 11.sp) }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (url.isNotBlank()) {
                    val meta = metadata ?: HttpMetadata().apply { this.url = url }
                    if (meta.url != url) meta.url = url
                    onStartDownload(fileNameText.ifBlank { detectedName }, saveTo, meta, true, "", 0, 0)
                }
            }) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
