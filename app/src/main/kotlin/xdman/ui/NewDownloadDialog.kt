package xdman.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xdman.*
import xdman.downloaders.metadata.HttpMetadata
import xdman.util.XDMUtils
import javax.swing.JFileChooser

@Composable
fun NewDownloadDialog(
    metadata: HttpMetadata?,
    fileName: String,
    folder: String?,
    onDismiss: () -> Unit,
    onStartDownload: (String?, String?, HttpMetadata, Boolean, String, Int, Int, Int) -> Unit
) {
    var url by remember(metadata) { mutableStateOf(metadata?.url ?: "") }
    val detectedName = remember(url) { XDMUtils.getFileName(url) }
    var fileNameText by remember { mutableStateOf(fileName.ifEmpty { detectedName }) }
    var saveTo by remember(folder) { mutableStateOf(folder ?: Config.getInstance().downloadFolder) }
    var startNow by remember { mutableStateOf(true) }
    var selectedCategory by remember { mutableStateOf(XDMConstants.ALL) }
    var selectedQueueId by remember { mutableStateOf("") }

    val queues = remember { XDMApp.getQueueList() }
    val categoryNames = listOf(
        XDMConstants.ALL to "Auto Detect",
        XDMConstants.VIDEO to "Video",
        XDMConstants.MUSIC to "Music",
        XDMConstants.DOCUMENTS to "Documents",
        XDMConstants.PROGRAMS to "Programs",
        XDMConstants.COMPRESSED to "Compressed",
        XDMConstants.OTHER to "Other"
    )

    LaunchedEffect(detectedName) {
        if (fileNameText.isEmpty() || fileNameText == detectedName || fileNameText.isBlank()) {
            fileNameText = detectedName
        }
        // Auto-detect category from filename
        val cat = XDMUtils.findCategory(detectedName)
        if (cat != XDMConstants.OTHER) {
            selectedCategory = cat
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Download") },
        text = {
            Column(modifier = Modifier.width(440.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
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

                // Category dropdown
                var catExpanded by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Category: ", fontSize = 12.sp, modifier = Modifier.width(70.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = categoryNames.find { it.first == selectedCategory }?.second ?: "Auto Detect",
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Select category", modifier = Modifier.clickable { catExpanded = true }) }
                        )
                        DropdownMenu(expanded = catExpanded, onDismissRequest = { catExpanded = false }) {
                            categoryNames.forEach { (cat, name) ->
                                DropdownMenuItem(
                                    text = { Text(name, fontSize = 12.sp) },
                                    onClick = { selectedCategory = cat; catExpanded = false }
                                )
                            }
                        }
                    }
                }

                // Queue selector
                var queueExpanded by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Queue: ", fontSize = 12.sp, modifier = Modifier.width(70.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        val queueName = if (selectedQueueId.isEmpty()) "Default" else
                            queues.find { it.queueId == selectedQueueId }?.name ?: "Default"
                        OutlinedTextField(
                            value = queueName,
                            onValueChange = {},
                            readOnly = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Icon(Icons.Default.ArrowDropDown, "Select queue", modifier = Modifier.clickable { queueExpanded = true }) }
                        )
                        DropdownMenu(expanded = queueExpanded, onDismissRequest = { queueExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("Default", fontSize = 12.sp) },
                                onClick = { selectedQueueId = ""; queueExpanded = false }
                            )
                            queues.forEach { q ->
                                DropdownMenuItem(
                                    text = { Text(q.name ?: q.queueId, fontSize = 12.sp) },
                                    onClick = { selectedQueueId = q.queueId; queueExpanded = false }
                                )
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = startNow, onCheckedChange = { startNow = it })
                    Spacer(Modifier.width(4.dp))
                    Text("Start download immediately", fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (url.isNotBlank()) {
                    val meta = metadata ?: HttpMetadata().apply { this.url = url }
                    if (meta.url != url) meta.url = url
                    onStartDownload(fileNameText.ifBlank { detectedName }, saveTo, meta, startNow, selectedQueueId, 0, 0, selectedCategory)
                }
            }) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
