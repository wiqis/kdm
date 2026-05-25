package xdman.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xdman.Config
import javax.swing.JFileChooser

@Composable
fun SettingsDialog(onDismiss: () -> Unit, onDarkModeChange: (Boolean) -> Unit) {
    val config = Config.getInstance()
    var downloadFolder by remember { mutableStateOf(config.downloadFolder) }
    var maxDownloads by remember { mutableStateOf(config.maxDownloads.toString()) }
    var maxSegments by remember { mutableStateOf(config.maxSegments.toString()) }
    var speedLimit by remember { mutableStateOf((config.speedLimit / 1024).toString()) }
    var darkMode by remember { mutableStateOf(config.isDarkMode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(modifier = Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = downloadFolder,
                        onValueChange = { downloadFolder = it },
                        label = { Text("Download Folder") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        val chooser = JFileChooser(downloadFolder)
                        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        chooser.isAcceptAllFileFilterUsed = false
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            downloadFolder = chooser.selectedFile.absolutePath
                        }
                    }, modifier = Modifier.height(56.dp)) { Text("Browse", fontSize = 11.sp) }
                }
                OutlinedTextField(
                    value = maxDownloads,
                    onValueChange = { maxDownloads = it },
                    label = { Text("Max Simultaneous Downloads") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = maxSegments,
                    onValueChange = { maxSegments = it },
                    label = { Text("Max Segments per Download") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = speedLimit,
                    onValueChange = { speedLimit = it },
                    label = { Text("Speed Limit (KB/s)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Checkbox(checked = darkMode, onCheckedChange = { darkMode = it })
                    Text("Dark Mode", fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                config.downloadFolder = downloadFolder
                try { config.maxDownloads = maxDownloads.toInt() } catch (_: Exception) {}
                try { config.maxSegments = maxSegments.toInt() } catch (_: Exception) {}
                try { config.setSpeedLimit(speedLimit.toInt()) } catch (_: Exception) {}
                config.isDarkMode = darkMode
                config.save()
                onDarkModeChange(darkMode)
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
