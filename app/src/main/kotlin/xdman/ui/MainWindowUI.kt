package xdman.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.awtEventOrNull
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import xdman.*
import xdman.downloaders.metadata.HttpMetadata
import xdman.mediaconversion.FormatLoader
import xdman.util.FormatUtilities
import xdman.util.Logger
import xdman.util.XDMUtils
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import javax.swing.JFileChooser
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

private val LightColorScheme = lightColorScheme(
    primary = accentColor,
    onPrimary = Color.White,
    secondary = Color(0xFF03DAC6),
    background = Color(0xFFF5F5F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFE0E0E0),
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121),
    onSurfaceVariant = Color(0xFF757575),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainWindowUI(appState: XDMAppUIState) {
    val colorScheme = if (appState.darkMode) XDMColorScheme else LightColorScheme
    
    MaterialTheme(colorScheme = colorScheme) {
        val darkSurface = colorScheme.surface
        val darkBg = colorScheme.background
        val darkSurfaceVariant = colorScheme.surfaceVariant
        val textPrimary = colorScheme.onSurface
        val textSecondary = colorScheme.onSurfaceVariant
        
        LaunchedEffect(appState.categoryFilter, appState.stateFilter, appState.searchText,
            appState.sortField, appState.sortAsc, appState.queueIdFilter) {
            appState.refresh()
        }

        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                MenuBar(appState, darkSurfaceVariant)
                Toolbar(appState, darkSurface, textPrimary)
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    SidePanel(appState, darkSurfaceVariant, textPrimary)
                    Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        TabsAndSearch(appState, darkSurface, darkSurfaceVariant, textPrimary)
                        if (appState.selectedIds.isNotEmpty()) {
                            BatchActionBar(appState, textPrimary)
                        }
                        DownloadListView(appState, darkSurface, darkSurfaceVariant, textPrimary)
                    }
                }
                StatusBar(appState, darkSurfaceVariant)
            }
        }
    }
}

@Composable
private fun MenuBar(appState: XDMAppUIState, bgColor: Color) {
    Surface(color = bgColor, modifier = Modifier.fillMaxWidth().height(32.dp)) {
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
private fun Toolbar(appState: XDMAppUIState, bgColor: Color, textColor: Color) {
    Surface(color = bgColor, modifier = Modifier.fillMaxWidth().height(48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconButton(
                onClick = { appState.showNewDownloadDialog = true },
                colors = IconButtonDefaults.iconButtonColors(containerColor = accentColor, contentColor = Color.Black),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Add, "Add URL")
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = {
                val ids = ArrayList(appState.downloadIds.filter { id ->
                    val ent = XDMApp.getEntry(id)
                    ent != null && (ent.state == XDMConstants.PAUSED || ent.state == XDMConstants.FAILED)
                })
                for (id in ids) XDMApp.resumeDownload(id, true)
            }) {
                Icon(Icons.Default.PlayArrow, "Resume All", tint = textColor)
            }
            IconButton(onClick = {
                val ids = ArrayList(appState.downloadIds.filter { id ->
                    val ent = XDMApp.getEntry(id)
                    ent != null && ent.state == XDMConstants.DOWNLOADING
                })
                for (id in ids) XDMApp.pauseDownload(id)
            }) {
                Icon(Icons.Default.Pause, "Pause All", tint = textColor)
            }
            Spacer(Modifier.weight(1f))
            Text(XDMApp.APP_VERSION, fontSize = 11.sp, color = textSecondary)
        }
    }
}

@Composable
private fun SidePanel(appState: XDMAppUIState, bgColor: Color, textColor: Color) {
    Surface(color = bgColor, modifier = Modifier.width(180.dp).fillMaxHeight()) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Text("Categories", fontWeight = FontWeight.Bold, fontSize = 13.sp,
                color = textColor, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            val categories = listOf(
                XDMConstants.ALL to "All" to Icons.Default.AllInclusive,
                XDMConstants.VIDEO to "Videos" to Icons.Default.Movie,
                XDMConstants.MUSIC to "Music" to Icons.Default.MusicNote,
                XDMConstants.DOCUMENTS to "Documents" to Icons.Default.Description,
                XDMConstants.PROGRAMS to "Programs" to Icons.Default.Apps,
                XDMConstants.COMPRESSED to "Compressed" to Icons.Default.Archive,
                XDMConstants.OTHER to "Other" to Icons.Default.Category
            )

            for (item in categories) {
                val pair = item.first
                val icon = item.second
                val cat = pair.first
                val name = pair.second
                val selected = appState.categoryFilter == cat
                Surface(
                    color = if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent,
                    modifier = Modifier.fillMaxWidth().clickable { appState.categoryFilter = cat }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            icon,
                            contentDescription = name,
                            modifier = Modifier.size(18.dp),
                            tint = if (selected) accentColor else textSecondary
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            name,
                            fontSize = 12.sp,
                            color = if (selected) accentColor else textColor,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabsAndSearch(appState: XDMAppUIState, bgColor: Color, variantColor: Color, textColor: Color) {
    Surface(color = bgColor, modifier = Modifier.fillMaxWidth().height(48.dp)) {
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
                modifier = Modifier.width(200.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = textColor),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = variantColor
                )
            )
        }
    }
}

@Composable
private fun BatchActionBar(appState: XDMAppUIState, textColor: Color) {
    Surface(color = accentColor.copy(alpha = 0.15f), modifier = Modifier.fillMaxWidth().height(36.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text("${appState.selectedIds.size} selected", fontSize = 11.sp, color = accentColor)
            Spacer(Modifier.width(12.dp))
            TextButton(onClick = {
                val ids = appState.selectedIds.filter { id ->
                    val ent = XDMApp.getEntry(id)
                    ent != null && (ent.state == XDMConstants.PAUSED || ent.state == XDMConstants.FAILED)
                }
                ids.forEach { XDMApp.resumeDownload(it, true) }
            }) {
                Text("Resume", fontSize = 11.sp, color = textColor)
            }
            TextButton(onClick = {
                val ids = appState.selectedIds.filter { id ->
                    val ent = XDMApp.getEntry(id)
                    ent != null && ent.state == XDMConstants.DOWNLOADING
                }
                ids.forEach { XDMApp.pauseDownload(it) }
            }) {
                Text("Pause", fontSize = 11.sp, color = textColor)
            }
            TextButton(onClick = {
                appState.selectedIds.forEach { XDMApp.deleteDownloads(listOf(it), false) }
                appState.selectedIds = emptySet()
            }) {
                Text("Delete", fontSize = 11.sp, color = failedColor)
            }
            TextButton(onClick = {
                appState.selectedIds.forEach { XDMApp.deleteDownloads(listOf(it), true) }
                appState.selectedIds = emptySet()
            }) {
                Text("Delete w/ File", fontSize = 11.sp, color = failedColor)
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { appState.selectedIds = emptySet() }) {
                Text("Clear", fontSize = 11.sp, color = textSecondary)
            }
        }
    }
}

@Composable
private fun DownloadListView(appState: XDMAppUIState, itemBg: Color, variantColor: Color, textColor: Color) {
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
                    itemBg = itemBg,
                    variantColor = variantColor,
                    textColor = textColor,
                    onClick = {
                        appState.selectedIds = if (id in appState.selectedIds)
                            appState.selectedIds - id else appState.selectedIds + id
                    },
                    onDoubleClick = {
                        if (ent.state == XDMConstants.FINISHED) {
                            try { XDMUtils.openFile(ent.file, XDMApp.getFolder(ent)) }
                            catch (e: Exception) { Logger.log(e) }
                        } else if (ent.state == XDMConstants.DOWNLOADING || ent.state == XDMConstants.ASSEMBLING) {
                            appState.showProgress(id)
                        }
                    },
                    onOpenFile = {
                        try { XDMUtils.openFile(ent.file, XDMApp.getFolder(ent)) }
                        catch (e: Exception) { Logger.log(e) }
                    },
                    onOpenFolder = {
                        try { XDMUtils.openFolder(null, XDMApp.getFolder(ent)) }
                        catch (e: Exception) { Logger.log(e) }
                    },
                    onPause = { XDMApp.pauseDownload(id) },
                    onResume = { XDMApp.resumeDownload(id, true) },
                    onRestart = { XDMApp.restartDownload(id) },
                    onDelete = { XDMApp.deleteDownloads(listOf(id), false) },
                    onDeleteWithFile = { XDMApp.deleteDownloads(listOf(id), true) },
                    onShowProgress = { appState.showProgress(id) },
                    onCopyUrl = { XDMUtils.copyURL(XDMApp.getURL(id)) },
                    onCopyFile = { copyToClipboard("${XDMApp.getFolder(ent)}/${ent.file}") },
                    onSaveAs = { showSaveAsDialog(ent) },
                    onRefreshLink = { appState.refreshLinkId = id },
                    onPreview = { XDMApp.openPreview(id) },
                    onProperties = { appState.propertiesDialogId = id },
                    onConvert = { appState.convertDialogId = id }
                )
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DownloadItem(
    entry: DownloadEntry,
    progress: ProgressInfo,
    isSelected: Boolean,
    itemBg: Color,
    variantColor: Color,
    textColor: Color,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onOpenFile: () -> Unit,
    onOpenFolder: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
    onDeleteWithFile: () -> Unit,
    onShowProgress: () -> Unit = {},
    onCopyUrl: () -> Unit = {},
    onCopyFile: () -> Unit = {},
    onSaveAs: () -> Unit = {},
    onRefreshLink: () -> Unit = {},
    onPreview: () -> Unit = {},
    onProperties: () -> Unit = {},
    onConvert: () -> Unit = {}
) {
    var contextMenuExpanded by remember { mutableStateOf(false) }
    val isActive = entry.state == XDMConstants.DOWNLOADING || entry.state == XDMConstants.ASSEMBLING
    val isPausedOrFailed = entry.state == XDMConstants.PAUSED || entry.state == XDMConstants.FAILED
    val isFinished = entry.state == XDMConstants.FINISHED

    Surface(
        color = if (isSelected) variantColor else itemBg,
        modifier = Modifier.fillMaxWidth().height(64.dp)
            .onPointerEvent(PointerEventType.Press) {
                val awtEvent = it.awtEventOrNull
                if (awtEvent != null && awtEvent.isPopupTrigger) {
                    contextMenuExpanded = true
                }
            }
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
                        color = textColor,
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
                    if (isActive) {
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
                                    Icon(Icons.Default.Pause, "Pause", tint = pausedColor, modifier = Modifier.size(16.dp))
                                }
                            }
                            XDMConstants.PAUSED, XDMConstants.FAILED -> {
                                IconButton(onClick = onResume, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.PlayArrow, "Resume", tint = downloadingColor, modifier = Modifier.size(18.dp))
                                }
                            }
                            XDMConstants.FINISHED -> {
                                IconButton(onClick = onOpenFile, modifier = Modifier.size(24.dp)) {
                                    Icon(Icons.Default.OpenInNew, "Open", tint = finishedColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Context menu - right click or long press
            DropdownMenu(expanded = contextMenuExpanded, onDismissRequest = { contextMenuExpanded = false }) {
                // State-specific actions
                if (isFinished) {
                    DropdownMenuItem(text = { Text("Open") }, onClick = { contextMenuExpanded = false; onOpenFile() })
                }
                if (isActive) {
                    DropdownMenuItem(text = { Text("Pause") }, onClick = { contextMenuExpanded = false; onPause() })
                }
                if (isPausedOrFailed) {
                    DropdownMenuItem(text = { Text("Resume") }, onClick = { contextMenuExpanded = false; onResume() })
                    DropdownMenuItem(text = { Text("Restart") }, onClick = { contextMenuExpanded = false; onRestart() })
                }

                DropdownMenuItem(text = { Text("Open Folder") }, onClick = { contextMenuExpanded = false; onOpenFolder() })
                DropdownMenuItem(text = { Text("Save As") }, onClick = { contextMenuExpanded = false; onSaveAs() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { contextMenuExpanded = false; onDelete() })
                DropdownMenuItem(text = { Text("Delete with File") }, onClick = { contextMenuExpanded = false; onDeleteWithFile() })

                HorizontalDivider(color = darkSurfaceVariant)

                DropdownMenuItem(text = { Text("Refresh Link") }, onClick = { contextMenuExpanded = false; onRefreshLink() })
                if (!isFinished) {
                    DropdownMenuItem(text = { Text("Preview") }, onClick = { contextMenuExpanded = false; onPreview() })
                }
                DropdownMenuItem(text = { Text("Show Progress") }, onClick = { contextMenuExpanded = false; onShowProgress() })

                HorizontalDivider(color = darkSurfaceVariant)

                DropdownMenuItem(text = { Text("Copy URL") }, onClick = { contextMenuExpanded = false; onCopyUrl() })
                DropdownMenuItem(text = { Text("Copy File") }, onClick = { contextMenuExpanded = false; onCopyFile() })

                HorizontalDivider(color = darkSurfaceVariant)

                DropdownMenuItem(text = { Text("Convert") }, onClick = { contextMenuExpanded = false; onConvert() })

                HorizontalDivider(color = darkSurfaceVariant)

                DropdownMenuItem(text = { Text("Properties") }, onClick = { contextMenuExpanded = false; onProperties() })
            }
        }
    }
}

@Composable
private fun StatusBar(appState: XDMAppUIState, bgColor: Color) {
    Surface(color = bgColor, modifier = Modifier.fillMaxWidth().height(28.dp)) {
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

@Composable
fun RefreshLinkDialog(id: String, onDismiss: () -> Unit) {
    val metadata = remember { try { HttpMetadata.load(id) } catch (_: Exception) { null } }
    var url by remember { mutableStateOf(metadata?.url ?: XDMApp.getURL(id)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Refresh Link") },
        text = {
            Column(modifier = Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("URL") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                try {
                    val meta = HttpMetadata.load(id)
                    if (meta != null) {
                        meta.url = url
                        meta.save()
                    }
                } catch (e: Exception) {
                    Logger.log(e)
                }
                onDismiss()
            }) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ConvertDialog(id: String, onDismiss: () -> Unit) {
    val groups = remember {
        try { xdman.mediaconversion.FormatLoader.load() } catch (_: Exception) { emptyList() }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Convert Media") },
        text = {
            Column(modifier = Modifier.width(400.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Select output format:", fontSize = 12.sp, color = textSecondary)
                if (groups.isEmpty()) {
                    Text("No conversion formats available or FFmpeg not installed.",
                        fontSize = 12.sp, color = textSecondary)
                } else {
                    groups.forEach { group ->
                        Text(group.desc, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

private fun copyToClipboard(text: String) {
    try {
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
    } catch (e: Exception) {
        Logger.log(e)
    }
}

private fun showSaveAsDialog(entry: DownloadEntry) {
    try {
        val chooser = JFileChooser(XDMApp.getFolder(entry))
        chooser.selectedFile = File(entry.file ?: "download")
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
            val f = chooser.selectedFile
            entry.setFolder(f.parent)
            entry.setFile(f.name)
            XDMApp.fileNameChanged(entry.id)
        }
    } catch (e: Exception) {
        Logger.log(e)
    }
}

private fun formatSpeed(speed: Long): String {
    if (speed <= 0) return "Starting..."
    return FormatUtilities.formatSize(speed.toDouble()) + "/s"
}
