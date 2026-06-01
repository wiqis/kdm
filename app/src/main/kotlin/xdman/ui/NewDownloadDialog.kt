package xdman.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xdman.*
import xdman.downloaders.metadata.HttpMetadata
import xdman.util.XDMUtils
import java.text.SimpleDateFormat
import java.util.*
import javax.swing.JFileChooser

@Composable
fun NewDownloadDialog(
    metadata: HttpMetadata?,
    fileName: String,
    folder: String?,
    onDismiss: () -> Unit,
    onStartDownload: (String?, String?, HttpMetadata, Boolean, String, Int, Int, Int, Long) -> Unit
) {
    var url by remember(metadata) { mutableStateOf(metadata?.url ?: "") }
    val detectedName = remember(url) { XDMUtils.getFileName(url) }
    var fileNameText by remember { mutableStateOf(if (fileName.isNotBlank()) fileName else "") }
    var userModifiedName by remember { mutableStateOf(fileName.isNotBlank()) }
    var saveTo by remember(folder) { mutableStateOf(folder ?: Config.getInstance().downloadFolder) }
    var startNow by remember { mutableStateOf(true) }
    var scheduleEnabled by remember { mutableStateOf(false) }
    var scheduleHours by remember { mutableStateOf("0") }
    var scheduleMinutes by remember { mutableStateOf("30") }
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
        if (!userModifiedName && detectedName.isNotBlank() && detectedName != "FILE") {
            fileNameText = detectedName
        }
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
                    onValueChange = {
                        fileNameText = it
                        userModifiedName = true
                    },
                    label = { Text("Save As") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(detectedName.ifBlank { "filename" }, fontSize = 11.sp) }
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

                val primaryColor = MaterialTheme.colorScheme.primary
                val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant
                // Schedule options
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = startNow, onCheckedChange = {
                                startNow = it
                                if (it) scheduleEnabled = false
                            })
                            Spacer(Modifier.width(4.dp))
                            Text("Start now", fontSize = 11.sp, fontWeight = if (startNow) FontWeight.Medium else FontWeight.Normal)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = scheduleEnabled, onCheckedChange = {
                                scheduleEnabled = it
                                if (it) startNow = false
                            })
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Schedule, "Schedule", modifier = Modifier.size(16.dp), tint = primaryColor)
                            Spacer(Modifier.width(4.dp))
                            Text("Schedule for later", fontSize = 11.sp, fontWeight = if (scheduleEnabled) FontWeight.Medium else FontWeight.Normal)
                        }
                        if (scheduleEnabled) {
                            Spacer(Modifier.height(4.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text("Start in:", fontSize = 11.sp, color = secondaryText)
                                OutlinedTextField(
                                    value = scheduleHours,
                                    onValueChange = { scheduleHours = it.filter { c -> c.isDigit() }.take(3) },
                                    modifier = Modifier.width(60.dp).height(48.dp),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                                    label = { Text("Hrs", fontSize = 9.sp) }
                                )
                                Text(":", fontSize = 12.sp)
                                OutlinedTextField(
                                    value = scheduleMinutes,
                                    onValueChange = { scheduleMinutes = it.filter { c -> c.isDigit() }.take(2) },
                                    modifier = Modifier.width(60.dp).height(48.dp),
                                    singleLine = true,
                                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                                    label = { Text("Min", fontSize = 9.sp) }
                                )
                                Spacer(Modifier.weight(1f))
                                val h = scheduleHours.toIntOrNull() ?: 0
                                val m = scheduleMinutes.toIntOrNull() ?: 0
                                val totalMin = h * 60 + m
                                if (totalMin > 0) {
                                    val cal = Calendar.getInstance()
                                    cal.add(Calendar.MINUTE, totalMin)
                                    val fmt = SimpleDateFormat("HH:mm, MMM d", Locale.getDefault())
                                    Text(fmt.format(cal.time), fontSize = 10.sp, color = primaryColor)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (url.isNotBlank()) {
                    val meta = metadata ?: HttpMetadata().apply { this.url = url }
                    if (meta.url != url) meta.url = url
                    val finalName = fileNameText.ifBlank { detectedName.ifBlank { XDMUtils.getFileName(url) } }
                    val scheduleTime = if (scheduleEnabled && !startNow) {
                        val h = scheduleHours.toIntOrNull() ?: 0
                        val m = scheduleMinutes.toIntOrNull() ?: 0
                        val cal = Calendar.getInstance()
                        cal.add(Calendar.MINUTE, h * 60 + m)
                        cal.timeInMillis
                    } else 0L
                    onStartDownload(finalName, saveTo, meta, startNow, selectedQueueId, 0, 0, selectedCategory, scheduleTime)
                }
            }) { Text("Download") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
