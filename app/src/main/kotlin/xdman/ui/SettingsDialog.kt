package xdman.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xdman.Config
import xdman.XDMConstants
import xdman.XDMApp
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
    var showDownloadWindow by remember { mutableStateOf(config.isShowDownloadWindow) }
    var showCompleteWindow by remember { mutableStateOf(config.isShowDownloadCompleteWindow) }
    var forceSingleFolder by remember { mutableStateOf(config.isForceSingleFolder) }
    var autoResumeFailed by remember { mutableStateOf(config.isAutoResumeFailed) }
    var minimizeToTray by remember { mutableStateOf(config.isMinimizeToTray) }
    var confirmBeforeDelete by remember { mutableStateOf(config.isConfirmBeforeDelete) }
    var startWithSystem by remember { mutableStateOf(config.isStartWithSystem) }
    var showSpeedInTitle by remember { mutableStateOf(config.showSpeedInTitle) }

    // Category folders
    var catDocuments by remember { mutableStateOf(config.categoryDocuments) }
    var catMusic by remember { mutableStateOf(config.categoryMusic) }
    var catVideos by remember { mutableStateOf(config.categoryVideos) }
    var catPrograms by remember { mutableStateOf(config.categoryPrograms) }
    var catCompressed by remember { mutableStateOf(config.categoryCompressed) }
    var catOther by remember { mutableStateOf(config.categoryOther) }
    var showCategories by remember { mutableStateOf(false) }

    var proxyMode by remember { mutableStateOf(config.proxyMode) }
    var proxyHost by remember { mutableStateOf(config.proxyHost ?: "") }
    var proxyPort by remember { mutableStateOf(config.proxyPort.toString()) }
    var proxyUser by remember { mutableStateOf(config.proxyUser ?: "") }
    var proxyPass by remember { mutableStateOf(config.proxyPass ?: "") }

    val primary = MaterialTheme.colorScheme.primary
    val secondaryText = MaterialTheme.colorScheme.onSurfaceVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column(
                modifier = Modifier.width(520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // === Download Section ===
                Text("Downloads", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = primary)
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = maxDownloads,
                        onValueChange = { maxDownloads = it },
                        label = { Text("Max Concurrent") },
                        modifier = Modifier.width(130.dp),
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
                    OutlinedTextField(
                        value = networkTimeout,
                        onValueChange = { networkTimeout = it },
                        label = { Text("Timeout sec") },
                        modifier = Modifier.width(100.dp),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(fontSize = 11.sp)
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // === Behavior Section ===
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Behavior", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = primary)

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = darkMode, onCheckedChange = { darkMode = it })
                        Text("Dark mode", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = downloadAutoStart, onCheckedChange = { downloadAutoStart = it })
                        Text("Auto-start downloads when added", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = autoResumeFailed, onCheckedChange = { autoResumeFailed = it })
                        Text("Auto-resume failed downloads", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = confirmBeforeDelete, onCheckedChange = { confirmBeforeDelete = it })
                        Text("Confirm before deleting downloads", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showCompleteWindow, onCheckedChange = { showCompleteWindow = it })
                        Text("Show notification on download complete", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = showSpeedInTitle, onCheckedChange = { showSpeedInTitle = it })
                        Text("Show download speed in window title", fontSize = 12.sp)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // === System Section ===
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("System", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = primary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = monitorClipboard, onCheckedChange = { monitorClipboard = it })
                        Text("Monitor clipboard for URLs", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = forceSingleFolder, onCheckedChange = { forceSingleFolder = it })
                        Text("Force single download folder (disable categories)", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = minimizeToTray, onCheckedChange = { minimizeToTray = it })
                        Text("Minimize to tray instead of closing", fontSize = 12.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = startWithSystem, onCheckedChange = { startWithSystem = it })
                        Text("Start KDM with system", fontSize = 12.sp)
                    }
                }

                // === Category Folders ===
                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable { showCategories = !showCategories }
                        ) {
                            Icon(
                                if (showCategories) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                "Toggle", modifier = Modifier.size(18.dp), tint = primary
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("Category Folders", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = primary)
                        }
                        if (showCategories) {
                            Spacer(Modifier.height(6.dp))
                            CategoryFolderField("Documents", catDocuments, { catDocuments = it })
                            CategoryFolderField("Music", catMusic, { catMusic = it })
                            CategoryFolderField("Videos", catVideos, { catVideos = it })
                            CategoryFolderField("Programs", catPrograms, { catPrograms = it })
                            CategoryFolderField("Compressed", catCompressed, { catCompressed = it })
                            CategoryFolderField("Other", catOther, { catOther = it })
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp))

                // === Proxy ===
                Text("Proxy", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = primary)

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
                        value = proxyHost,
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
                config.temporaryFolder = temporaryFolder
                try { config.maxDownloads = maxDownloads.toInt() } catch (_: Exception) {}
                try { config.maxSegments = maxSegments.toInt() } catch (_: Exception) {}
                try { config.speedLimit = speedLimit.toInt() * 1024 } catch (_: Exception) {}
                try { config.networkTimeout = networkTimeout.toInt() } catch (_: Exception) {}
                config.isDarkMode = darkMode
                config.isMonitorClipboard = monitorClipboard
                config.isDownloadAutoStart = downloadAutoStart
                config.isShowDownloadWindow = showDownloadWindow
                config.isShowDownloadCompleteWindow = showCompleteWindow
                config.isForceSingleFolder = forceSingleFolder
                config.isAutoResumeFailed = autoResumeFailed
                config.isMinimizeToTray = minimizeToTray
                config.isConfirmBeforeDelete = confirmBeforeDelete
                config.isStartWithSystem = startWithSystem
                config.showSpeedInTitle = showSpeedInTitle
                config.categoryDocuments = catDocuments
                config.categoryMusic = catMusic
                config.categoryVideos = catVideos
                config.categoryPrograms = catPrograms
                config.categoryCompressed = catCompressed
                config.categoryOther = catOther
                config.proxyMode = proxyMode
                config.proxyHost = proxyHost
                try { config.proxyPort = proxyPort.toInt() } catch (_: Exception) {}
                config.proxyUser = proxyUser
                config.proxyPass = proxyPass
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

@Composable
private fun CategoryFolderField(label: String, value: String, onChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text("$label:", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(90.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.weight(1f).height(48.dp),
            singleLine = true,
            textStyle = LocalTextStyle.current.copy(fontSize = 10.sp)
        )
        Spacer(Modifier.width(4.dp))
        Button(
            onClick = {
                val chooser = JFileChooser(value)
                chooser.fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
                chooser.isAcceptAllFileFilterUsed = false
                if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION)
                    onChange(chooser.selectedFile.absolutePath)
            },
            modifier = Modifier.height(48.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
        ) { Text("...", fontSize = 11.sp) }
    }
}
