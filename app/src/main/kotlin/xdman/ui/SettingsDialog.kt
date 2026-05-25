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
    var temporaryFolder by remember { mutableStateOf(config.temporaryFolder ?: "") }
    var maxDownloads by remember { mutableStateOf(config.maxDownloads.toString()) }
    var maxSegments by remember { mutableStateOf(config.maxSegments.toString()) }
    var speedLimit by remember { mutableStateOf((config.speedLimit / 1024).toString()) }
    var networkTimeout by remember { mutableStateOf(config.networkTimeout.toString()) }
    var darkMode by remember { mutableStateOf(config.isDarkMode) }
    var monitorClipboard by remember { mutableStateOf(config.isMonitorClipboard) }
    var downloadAutoStart by remember { mutableStateOf(config.isDownloadAutoStart) }
    var showDownloadWindow by remember { mutableStateOf(config.showDownloadWindow()) }
    var forceSingleFolder by remember { mutableStateOf(config.isForceSingleFolder) }

    var proxyMode by remember { mutableStateOf(config.proxyMode) }
    var proxyHost by remember { mutableStateOf(config.proxyHost ?: "") }
    var proxyPort by remember { mutableStateOf(config.proxyPort.toString()) }
    var proxyUser by remember { mutableStateOf(config.proxyUser ?: "") }
    var proxyPass by remember { mutableStateOf(config.proxyPass ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.width(480.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = downloadFolder,
                        onValueChange = { downloadFolder = it },
                        label = { Text("Download Folder") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        val chooser = JFileChooser(downloadFolder)
                        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        chooser.isAcceptAllFileFilterUsed = false
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                            downloadFolder = chooser.selectedFile.absolutePath
                    }, modifier = Modifier.height(56.dp)) { Text("Browse", fontSize = 11.sp) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = temporaryFolder,
                        onValueChange = { temporaryFolder = it },
                        label = { Text("Temporary Folder") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = {
                        val chooser = JFileChooser(temporaryFolder.ifBlank { downloadFolder })
                        chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                        chooser.isAcceptAllFileFilterUsed = false
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                            temporaryFolder = chooser.selectedFile.absolutePath
                    }, modifier = Modifier.height(56.dp)) { Text("Browse", fontSize = 11.sp) }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxDownloads,
                        onValueChange = { maxDownloads = it },
                        label = { Text("Max Downloads") },
                        modifier = Modifier.width(140.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                    OutlinedTextField(
                        value = maxSegments,
                        onValueChange = { maxSegments = it },
                        label = { Text("Segments") },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                    OutlinedTextField(
                        value = speedLimit,
                        onValueChange = { speedLimit = it },
                        label = { Text("Speed Limit KB/s") },
                        modifier = Modifier.width(130.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = networkTimeout,
                        onValueChange = { networkTimeout = it },
                        label = { Text("Timeout (sec)") },
                        modifier = Modifier.width(140.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = darkMode, onCheckedChange = { darkMode = it })
                        Text("Dark Mode", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = monitorClipboard, onCheckedChange = { monitorClipboard = it })
                        Text("Monitor Clipboard", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = downloadAutoStart, onCheckedChange = { downloadAutoStart = it })
                        Text("Auto-start downloads", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showDownloadWindow, onCheckedChange = { showDownloadWindow = it })
                        Text("Show download progress window", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = forceSingleFolder, onCheckedChange = { forceSingleFolder = it })
                        Text("Force single folder (no subfolders)", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Text("Proxy", fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, fontSize = 12.sp)

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf(0 to "None", 1 to "PAC", 2 to "HTTP", 3 to "SOCKS").forEach { (mode, label) ->
                        FilterChip(
                            selected = proxyMode == mode,
                            onClick = { proxyMode = mode },
                            label = { Text(label, fontSize = 10.sp) }
                        )
                    }
                }
                if (proxyMode == 2 || proxyMode == 3) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = proxyHost,
                            onValueChange = { proxyHost = it },
                            label = { Text("Host") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                        )
                        OutlinedTextField(
                            value = proxyPort,
                            onValueChange = { proxyPort = it },
                            label = { Text("Port") },
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        OutlinedTextField(
                            value = proxyUser,
                            onValueChange = { proxyUser = it },
                            label = { Text("Username") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                        )
                        OutlinedTextField(
                            value = proxyPass,
                            onValueChange = { proxyPass = it },
                            label = { Text("Password") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                        )
                    }
                }
                if (proxyMode == 1) {
                    OutlinedTextField(
                        value = proxyHost, // reuse as PAC URL
                        onValueChange = { proxyHost = it },
                        label = { Text("PAC URL") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                config.downloadFolder = downloadFolder
                config.temporaryFolder = temporaryFolder.ifBlank { null }
                try { config.maxDownloads = maxDownloads.toInt() } catch (_: Exception) {}
                try { config.maxSegments = maxSegments.toInt() } catch (_: Exception) {}
                try { config.setSpeedLimit(speedLimit.toInt() * 1024) } catch (_: Exception) {}
                try { config.networkTimeout = networkTimeout.toInt() } catch (_: Exception) {}
                config.isDarkMode = darkMode
                config.setMonitorClipboard(monitorClipboard)
                config.setDownloadAutoStart(downloadAutoStart)
                config.setShowDownloadWindow(showDownloadWindow)
                config.isForceSingleFolder = forceSingleFolder
                config.proxyMode = proxyMode
                config.proxyHost = proxyHost.ifBlank { null }
                try { config.proxyPort = proxyPort.toInt() } catch (_: Exception) {}
                config.proxyUser = proxyUser.ifBlank { null }
                config.proxyPass = proxyPass.ifBlank { null }
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
