package xdman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xdman.*
import xdman.util.FormatUtilities
import xdman.util.Logger
import xdman.util.XDMUtils
import kotlin.system.exitProcess

private val darkBg = Color(0xFF1E1E1E)
private val darkSurface = Color(0xFF2D2D2D)
private val darkSurfaceVariant = Color(0xFF3C3C3C)
private val accentColor = Color(0xFFFF9800)
private val textPrimary = Color(0xFFE0E0E0)
private val textSecondary = Color(0xFF9E9E9E)
private val finishedColor = Color(0xFF4CAF50)
private val pausedColor = Color(0xFFFFC107)
private val downloadingColor = Color(0xFF2196F3)
private val failedColor = Color(0xFFF44336)

private val XDMColorScheme = darkColorScheme(
    primary = accentColor,
    onPrimary = Color.Black,
    secondary = Color(0xFF03DAC6),
    background = darkBg,
    surface = darkSurface,
    surfaceVariant = darkSurfaceVariant,
    onBackground = textPrimary,
    onSurface = textPrimary,
    onSurfaceVariant = textSecondary,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainWindowUI(appState: XDMAppUIState) {
    MaterialTheme(colorScheme = XDMColorScheme) {
        LaunchedEffect(appState.categoryFilter, appState.stateFilter, appState.searchText,
            appState.sortField, appState.sortAsc, appState.queueIdFilter) {
            appState.refresh()
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                MenuBar(appState)
                Toolbar(appState)
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SidePanel(appState)
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TabsAndSearch(appState)
                        DownloadListView(appState)
                    }
                }
                StatusBar(appState)
            }
        }
    }
}

@Composable
private fun MenuBar(appState: XDMAppUIState) {
    Surface(color = darkSurfaceVariant, modifier = Modifier.fillMaxWidth().height(32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { expanded = true }) { Text("File", fontSize = 12.sp) }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text("Add URL") }, onClick = {
                        expanded = false
                        appState.showNewDownloadDialog = true
                    })
                    DropdownMenuItem(text = { Text("Exit") }, onClick = {
                        expanded = false
                        XDMApp.exit()
                        exitProcess(0)
                    })
                }
            }
            var viewExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { viewExpanded = true }) { Text("View", fontSize = 12.sp) }
                DropdownMenu(expanded = viewExpanded, onDismissRequest = { viewExpanded = false }) {
                    val categories = listOf(
                        XDMConstants.ALL to "All",
                        XDMConstants.VIDEO to "Videos",
                        XDMConstants.MUSIC to "Music",
                        XDMConstants.DOCUMENTS to "Documents",
                        XDMConstants.PROGRAMS to "Programs",
                        XDMConstants.COMPRESSED to "Compressed",
                        XDMConstants.OTHER to "Other"
                    )
                    for ((cat, name) in categories) {
                        DropdownMenuItem(text = { Text(name) }, onClick = {
                            viewExpanded = false
                            appState.categoryFilter = cat
                        })
                    }
                }
            }
            var helpExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { helpExpanded = true }) { Text("Help", fontSize = 12.sp) }
                DropdownMenu(expanded = helpExpanded, onDismissRequest = { helpExpanded = false }) {
                    DropdownMenuItem(text = { Text("Settings") }, onClick = {
                        helpExpanded = false
                        appState.showSettingsDialog = true
                    })
                    DropdownMenuItem(text = { Text("About") }, onClick = {
                        helpExpanded = false
                        appState.showAboutDialog = true
                    })
                }
            }
        }
    }
}

@Composable
private fun Toolbar(appState: XDMAppUIState) {
    Surface(color = darkSurface, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Button(
                onClick = { appState.showNewDownloadDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                modifier = Modifier.height(36.dp)
            ) {
                Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = Color.Black)
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = {
                val ids = ArrayList(appState.downloadIds.filter { id ->
                    val ent = XDMApp.getEntry(id)
                    ent != null && (ent.state == XDMConstants.PAUSED || ent.state == XDMConstants.FAILED)
                })
                for (id in ids) XDMApp.resumeDownload(id, true)
            }) {
                Text("Resume All", fontSize = 12.sp)
            }
            TextButton(onClick = {
                val ids = ArrayList(appState.downloadIds.filter { id ->
                    val ent = XDMApp.getEntry(id)
                    ent != null && ent.state == XDMConstants.DOWNLOADING
                })
                for (id in ids) XDMApp.pauseDownload(id)
            }) {
                Text("Pause All", fontSize = 12.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(XDMApp.APP_VERSION, fontSize = 11.sp, color = textSecondary)
        }
    }
}

@Composable
private fun SidePanel(appState: XDMAppUIState) {
    Surface(color = darkSurfaceVariant, modifier = Modifier.width(160.dp).fillMaxHeight()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Categories", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = textPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))

            val categories = listOf(
                XDMConstants.ALL to "All",
                XDMConstants.VIDEO to "Videos",
                XDMConstants.MUSIC to "Music",
                XDMConstants.DOCUMENTS to "Documents",
                XDMConstants.PROGRAMS to "Programs",
                XDMConstants.COMPRESSED to "Compressed",
                XDMConstants.OTHER to "Other"
            )

            for ((cat, name) in categories) {
                val selected = appState.categoryFilter == cat
                Surface(
                    color = if (selected) accentColor.copy(alpha = 0.2f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().clickable { appState.categoryFilter = cat }
                ) {
                    Text(name, fontSize = 12.sp, color = if (selected) accentColor else textSecondary,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun TabsAndSearch(appState: XDMAppUIState) {
    Surface(color = darkSurface, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            val states = listOf(
                XDMConstants.ALL to "All",
                XDMConstants.FINISHED to "Finished",
                XDMConstants.UNFINISHED to "Active"
            )
            for ((st, label) in states) {
                val selected = appState.stateFilter == st
                TextButton(onClick = { appState.stateFilter = st }) {
                    Text(label, fontSize = 12.sp,
                        color = if (selected) accentColor else textSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
            }
            Spacer(Modifier.weight(1f))
            OutlinedTextField(
                value = appState.searchText,
                onValueChange = { appState.searchText = it },
                placeholder = { Text("Search...", color = textSecondary) },
                modifier = Modifier.width(200.dp).height(36.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = textPrimary),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = darkSurfaceVariant
                )
            )
        }
    }
}

@Composable
private fun DownloadListView(appState: XDMAppUIState) {
    val entries = remember(appState.downloadIds, appState.progressMap) {
        appState.downloadIds.mapNotNull { id ->
            val ent = XDMApp.getEntry(id)
            if (ent != null) Pair(id, ent) else null
        }.sortedByDescending { (_, ent) -> ent.date }
    }

    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No downloads", color = textSecondary, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(1.dp)
        ) {
            items(entries, key = { it.first }) { (id, ent) ->
                val progress = appState.getProgress(id)
                DownloadItem(
                    entry = ent,
                    progress = progress,
                    isSelected = id in appState.selectedIds,
                    onClick = {
                        appState.selectedIds = if (id in appState.selectedIds)
                            appState.selectedIds - id else appState.selectedIds + id
                    },
                    onDoubleClick = {
                        if (ent.state == XDMConstants.FINISHED) {
                            try { XDMUtils.openFile(ent.file, XDMApp.getFolder(ent)) }
                            catch (e: Exception) { Logger.log(e) }
                        }
                    },
                    onPause = { XDMApp.pauseDownload(id) },
                    onResume = { XDMApp.resumeDownload(id, true) },
                    onRestart = { XDMApp.restartDownload(id) },
                    onDelete = { XDMApp.deleteDownloads(listOf(id), false) },
                    onDeleteWithFile = { XDMApp.deleteDownloads(listOf(id), true) },
                    onOpenFolder = {
                        try { XDMUtils.openFolder(null, XDMApp.getFolder(ent)) }
                        catch (e: Exception) { Logger.log(e) }
                    },
                    onOpenFile = {
                        try { XDMUtils.openFile(ent.file, XDMApp.getFolder(ent)) }
                        catch (e: Exception) { Logger.log(e) }
                    }
                )
            }
        }
    }
}

@Composable
private fun DownloadItem(
    entry: DownloadEntry,
    progress: ProgressInfo,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
    onDeleteWithFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onOpenFile: () -> Unit
) {
    var contextMenuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = if (isSelected) darkSurfaceVariant else darkSurface,
        modifier = Modifier.fillMaxWidth().height(64.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() },
                    onLongPress = { contextMenuExpanded = true }
                )
            }
    ) {
        Box {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.file ?: "Unknown",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    val stateText = when (entry.state) {
                        XDMConstants.DOWNLOADING -> formatSpeed(progress.speed)
                        XDMConstants.PAUSED -> "Paused"
                        XDMConstants.FAILED -> "Failed"
                        XDMConstants.FINISHED -> "Completed - ${entry.file}"
                        XDMConstants.ASSEMBLING -> "Assembling..."
                        else -> FormatUtilities.formatSize(entry.downloaded.toDouble()) + " / " + FormatUtilities.formatSize(entry.size.toDouble())
                    }
                    Text(stateText, fontSize = 11.sp, color = textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (entry.state == XDMConstants.DOWNLOADING || entry.state == XDMConstants.ASSEMBLING) {
                        Spacer(Modifier.height(2.dp))
                        LinearProgressIndicator(
                            progress = { entry.progress / 100.0f },
                            modifier = Modifier.fillMaxWidth().height(4.dp),
                            color = downloadingColor,
                            trackColor = darkSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        FormatUtilities.formatSize(entry.size.toDouble()),
                        fontSize = 11.sp,
                        color = textSecondary
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        when (entry.state) {
                            XDMConstants.DOWNLOADING -> {
                                IconButton(onClick = onPause, modifier = Modifier.size(24.dp)) {
                                    Text("||", fontSize = 10.sp, color = pausedColor)
                                }
                            }
                            XDMConstants.PAUSED, XDMConstants.FAILED -> {
                                IconButton(onClick = onResume, modifier = Modifier.size(24.dp)) {
                                    Text(">", fontSize = 12.sp, color = downloadingColor)
                                }
                            }
                            XDMConstants.FINISHED -> {
                                IconButton(onClick = onOpenFile, modifier = Modifier.size(24.dp)) {
                                    Text("O", fontSize = 10.sp, color = finishedColor)
                                }
                            }
                        }
                    }
                }
            }

            // Context menu on right-click
            DropdownMenu(expanded = contextMenuExpanded, onDismissRequest = { contextMenuExpanded = false }) {
                when (entry.state) {
                    XDMConstants.DOWNLOADING -> {
                        DropdownMenuItem(text = { Text("Pause") }, onClick = { contextMenuExpanded = false; onPause() })
                    }
                    XDMConstants.PAUSED -> {
                        DropdownMenuItem(text = { Text("Resume") }, onClick = { contextMenuExpanded = false; onResume() })
                        DropdownMenuItem(text = { Text("Restart") }, onClick = { contextMenuExpanded = false; onRestart() })
                    }
                    XDMConstants.FAILED -> {
                        DropdownMenuItem(text = { Text("Resume") }, onClick = { contextMenuExpanded = false; onResume() })
                        DropdownMenuItem(text = { Text("Restart") }, onClick = { contextMenuExpanded = false; onRestart() })
                    }
                    XDMConstants.FINISHED -> {
                        DropdownMenuItem(text = { Text("Open File") }, onClick = { contextMenuExpanded = false; onOpenFile() })
                        DropdownMenuItem(text = { Text("Open Folder") }, onClick = { contextMenuExpanded = false; onOpenFolder() })
                    }
                }
                DropdownMenuItem(text = { Text("Delete") }, onClick = { contextMenuExpanded = false; onDelete() })
                DropdownMenuItem(text = { Text("Delete with File") }, onClick = { contextMenuExpanded = false; onDeleteWithFile() })
            }


        }
    }
}

@Composable
private fun StatusBar(appState: XDMAppUIState) {
    Surface(color = darkSurfaceVariant, modifier = Modifier.fillMaxWidth().height(28.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val activeCount = appState.downloadIds.count { id ->
                val ent = XDMApp.getEntry(id)
                ent != null && (ent.state == XDMConstants.DOWNLOADING || ent.state == XDMConstants.ASSEMBLING)
            }
            Text("Downloads: ${appState.downloadIds.size}  Active: $activeCount",
                fontSize = 11.sp, color = textSecondary)
        }
    }
}

private fun formatSpeed(speed: Long): String {
    if (speed <= 0) return "Starting..."
    return FormatUtilities.formatSize(speed.toDouble()) + "/s"
}
