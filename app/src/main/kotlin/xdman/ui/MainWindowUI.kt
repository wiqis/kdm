package xdman.ui

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
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
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.system.exitProcess

// Modern color palette
private val darkBg = Color(0xFF1A1A2E)
private val darkSurface = Color(0xFF232340)
private val darkSurfaceVariant = Color(0xFF2D2D4E)
private val accentColor = Color(0xFFFF9800)
private val accentDim = Color(0xFFCC7A00)
private val textPrimary = Color(0xFFE8E8F0)
private val textSecondary = Color(0xFF9E9EB0)
private val finishedColor = Color(0xFF4CAF50)
private val pausedColor = Color(0xFFFFC107)
private val downloadingColor = Color(0xFF42A5F5)
private val failedColor = Color(0xFFEF5350)
private val successBg = Color(0xFF1B3A1B)
private val warningBg = Color(0xFF3A3A1B)
private val errorBg = Color(0xFF3A1B1B)
private val infoBg = Color(0xFF1B2A3A)

private val XDMColorScheme = darkColorScheme(
    primary = accentColor,
    onPrimary = Color.Black,
    secondary = Color(0xFF7C4DFF),
    tertiary = Color(0xFF00E5FF),
    background = darkBg,
    surface = darkSurface,
    surfaceVariant = darkSurfaceVariant,
    onBackground = textPrimary,
    onSurface = textPrimary,
    onSurfaceVariant = textSecondary,
    outline = Color(0xFF3D3D5C),
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFE65100),
    onPrimary = Color.White,
    secondary = Color(0xFF7C4DFF),
    tertiary = Color(0xFF00838F),
    background = Color(0xFFF8F9FA),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0F0F4),
    onBackground = Color(0xFF212121),
    onSurface = Color(0xFF212121),
    onSurfaceVariant = Color(0xFF757575),
    outline = Color(0xFFE0E0E0),
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
            appState.sortField, appState.sortAsc, appState.queueIdFilter, appState.tagFilter, appState.downloadTags) {
            appState.refresh()
        }

        Surface(
            modifier = Modifier.fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when {
                            event.isCtrlPressed && event.key == Key.N -> {
                                appState.showNewDownloadDialog = true; true
                            }
                            event.isCtrlPressed && event.key == Key.F -> {
                                appState.searchText = ""; true
                            }
                            event.key == Key.Delete || event.key == Key.Backspace -> {
                                val toDelete = appState.selectedIds.toList()
                                if (toDelete.isNotEmpty()) {
                                    toDelete.forEach { XDMApp.deleteDownloads(listOf(it), false) }
                                    appState.selectedIds = emptySet()
                                }
                                true
                            }
                            event.isCtrlPressed && event.key == Key.I -> {
                                appState.showImportUrlsDialog = true; true
                            }
                            event.isCtrlPressed && event.key == Key.A -> {
                                appState.selectedIds = appState.downloadIds.toSet(); true
                            }
                            else -> false
                        }
                    } else false
                }
        ) {
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
                    DropdownMenuItem(text = { Text("Import URLs") }, onClick = {
                        expanded = false
                        appState.showImportUrlsDialog = true
                    })
                    DropdownMenuItem(text = { Text("Export Data") }, onClick = {
                        expanded = false
                        appState.showExportDialog = true
                    })
                    DropdownMenuItem(text = { Text("Import Data") }, onClick = {
                        expanded = false
                        appState.showImportDialog = true
                    })
                    DropdownMenuItem(text = { Text("Exit") }, onClick = {
                        expanded = false
                        XDMApp.exit()
                        exitProcess(0)
                    })
                }
            }
            var dlExpanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { dlExpanded = true }) { Text("Download", fontSize = 12.sp) }
                DropdownMenu(expanded = dlExpanded, onDismissRequest = { dlExpanded = false }) {
                    DropdownMenuItem(text = { Text("Download Video") }, onClick = {
                        dlExpanded = false
                        appState.showYTDownloadDialog = true
                    })
                    DropdownMenuItem(text = { Text("Download Playlist") }, onClick = {
                        dlExpanded = false
                        appState.showYTPlaylistDialog = true
                    })
                    HorizontalDivider(color = textSecondary.copy(alpha = 0.3f))
                    DropdownMenuItem(text = { Text("Setup Tools (yt-dlp + ffmpeg)") }, onClick = {
                        dlExpanded = false
                        appState.showYTSetupDialog = true
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
                    DropdownMenuItem(text = { Text("Keyboard Shortcuts") }, onClick = {
                        helpExpanded = false
                        appState.showShortcutsDialog = true
                    })
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
    Surface(
        color = bgColor,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxHeight().padding(horizontal = 8.dp)
        ) {
            IconButton(
                onClick = { appState.showNewDownloadDialog = true },
                colors = IconButtonDefaults.iconButtonColors(containerColor = accentColor, contentColor = Color.Black),
                modifier = Modifier.size(38.dp)
            ) {
                Icon(Icons.Default.Add, "New Download (Ctrl+N)", modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Surface(
                color = darkSurfaceVariant,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = {
                            val ids = XDMApp.getDownloads().values
                                .filter { it.state == XDMConstants.PAUSED || it.state == XDMConstants.FAILED }
                                .map { it.id }
                            ids.forEach { XDMApp.resumeDownload(it, true) }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, "Resume All", tint = finishedColor, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            val ids = XDMApp.getDownloads().values
                                .filter { it.state == XDMConstants.DOWNLOADING || it.state == XDMConstants.ASSEMBLING }
                                .map { it.id }
                            ids.forEach { XDMApp.pauseDownload(it) }
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Pause, "Pause All", tint = pausedColor, modifier = Modifier.size(18.dp))
                    }
                    IconButton(
                        onClick = {
                            appState.showImportUrlsDialog = true
                        },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, "Import URLs (Ctrl+I)", tint = textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
            Spacer(Modifier.width(8.dp))
            // Search / Filter
            OutlinedTextField(
                value = appState.searchText,
                onValueChange = { appState.searchText = it },
                placeholder = { Text("Search downloads...", color = textSecondary.copy(alpha = 0.5f), fontSize = 12.sp) },
                modifier = Modifier.width(220.dp).height(34.dp),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(fontSize = 12.sp, color = textColor),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor,
                    unfocusedBorderColor = Color.Transparent,
                    cursorColor = accentColor,
                ),
                leadingIcon = { Icon(Icons.Default.Search, "Search", modifier = Modifier.size(16.dp), tint = textSecondary) },
                shape = RoundedCornerShape(8.dp)
            )
            Spacer(Modifier.weight(1f))
            if (appState.activeCount > 0) {
                Surface(
                    color = infoBg,
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        "${appState.activeCount} active",
                        fontSize = 11.sp,
                        color = downloadingColor,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
                Spacer(Modifier.width(6.dp))
            }
            IconButton(
                onClick = { appState.showSettingsDialog = true },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(Icons.Default.Settings, "Settings", tint = textSecondary, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun SidePanel(appState: XDMAppUIState, bgColor: Color, textColor: Color) {
    val scrollState = rememberScrollState()
    Surface(color = bgColor, modifier = Modifier.width(190.dp).fillMaxHeight()) {
        Column(modifier = Modifier.padding(vertical = 4.dp).verticalScroll(scrollState)) {
            // Categories section
            Text("Categories", fontWeight = FontWeight.Bold, fontSize = 11.sp,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            val categories = listOf(
                XDMConstants.ALL to "All" to Icons.Default.AllInclusive,
                XDMConstants.VIDEO to "Videos" to Icons.Default.Movie,
                XDMConstants.MUSIC to "Music" to Icons.Default.MusicNote,
                XDMConstants.DOCUMENTS to "Documents" to Icons.Default.Description,
                XDMConstants.PROGRAMS to "Programs" to Icons.Default.Apps,
                XDMConstants.COMPRESSED to "Compressed" to Icons.Default.Archive,
                XDMConstants.OTHER to "Other" to Icons.Default.Folder
            )

            for (item in categories) {
                val pair = item.first
                val icon = item.second
                val cat = pair.first
                val name = pair.second
                val selected = appState.categoryFilter == cat
                Surface(
                    color = if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent,
                    shape = RoundedCornerShape(0.dp, 8.dp, 8.dp, 0.dp),
                    modifier = Modifier.fillMaxWidth().clickable { appState.categoryFilter = cat; appState.tagFilter = null }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                    ) {
                        Icon(icon, contentDescription = name, modifier = Modifier.size(16.dp),
                            tint = if (selected) accentColor else textSecondary)
                        Spacer(Modifier.width(10.dp))
                        Text(name, fontSize = 12.sp,
                            color = if (selected) accentColor else textColor,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            HorizontalDivider(color = textSecondary.copy(alpha = 0.15f), modifier = Modifier.padding(horizontal = 12.dp))
            Spacer(Modifier.height(4.dp))

            // Tags section
            Text("Tags", fontWeight = FontWeight.Bold, fontSize = 11.sp,
                color = textColor.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))

            if (appState.tagFilter != null) {
                Surface(
                    color = accentColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth().clickable { appState.tagFilter = null }.padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
                        Icon(Icons.Default.Close, "Clear", modifier = Modifier.size(12.dp), tint = accentColor)
                        Spacer(Modifier.width(4.dp))
                        Text("Clear Filter", fontSize = 10.sp, color = accentColor)
                    }
                }
                Spacer(Modifier.height(2.dp))
            }

            if (appState.availableTags.isEmpty()) {
                Text("No tags", fontSize = 11.sp, color = textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
            } else {
                appState.availableTags.forEach { tag ->
                    val selected = appState.tagFilter == tag.name
                    Surface(
                        color = if (selected) accentColor.copy(alpha = 0.12f) else Color.Transparent,
                        shape = RoundedCornerShape(0.dp, 6.dp, 6.dp, 0.dp),
                        modifier = Modifier.fillMaxWidth().clickable {
                            if (selected) { appState.tagFilter = null }
                            else { appState.tagFilter = tag.name; appState.categoryFilter = XDMConstants.ALL }
                        }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(14.dp),
                                tint = if (selected) accentColor else textSecondary)
                            Spacer(Modifier.width(10.dp))
                            Text(tag.name, fontSize = 12.sp,
                                color = if (selected) accentColor else textColor,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Surface(
                color = accentColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth().clickable { appState.showAddTagDialog = true }.padding(horizontal = 12.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Tag", modifier = Modifier.size(14.dp), tint = accentColor)
                    Spacer(Modifier.width(8.dp))
                    Text("Add Tag", fontSize = 11.sp, color = accentColor)
                }
            }

            if (appState.availableTags.isNotEmpty()) {
                Surface(
                    color = textSecondary.copy(alpha = 0.05f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().clickable { appState.showManageTagsDialog = true }.padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Manage Tags", modifier = Modifier.size(12.dp), tint = textSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text("Manage Tags", fontSize = 10.sp, color = textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun TabsAndSearch(appState: XDMAppUIState, bgColor: Color, variantColor: Color, textColor: Color) {
    Surface(color = bgColor, modifier = Modifier.fillMaxWidth().height(40.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxHeight().padding(horizontal = 4.dp)) {
            val states = listOf(
                XDMConstants.ALL to "All",
                XDMConstants.FINISHED to "Finished",
                XDMConstants.UNFINISHED to "Active"
            )
            for ((st, label) in states) {
                val selected = appState.stateFilter == st
                val count = when (st) {
                    XDMConstants.ALL -> appState.downloadIds.size
                    XDMConstants.FINISHED -> appState.finishedCount
                    XDMConstants.UNFINISHED -> appState.activeCount + appState.pausedCount + appState.failedCount
                    else -> 0
                }
                Surface(
                    color = if (selected) accentColor.copy(alpha = 0.15f) else Color.Transparent,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.padding(horizontal = 2.dp)
                ) {
                    TextButton(
                        onClick = { appState.stateFilter = st },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(label, fontSize = 12.sp,
                            color = if (selected) accentColor else textSecondary,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        if (count > 0) {
                            Spacer(Modifier.width(4.dp))
                            Surface(
                                color = if (selected) accentColor.copy(alpha = 0.3f) else variantColor.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    "$count",
                                    fontSize = 9.sp,
                                    color = if (selected) accentColor else textSecondary,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
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
            if (appState.availableTags.isNotEmpty()) {
                TextButton(onClick = {
                    appState.batchTagIds = appState.selectedIds.toList()
                    appState.showBatchTagDialog = true
                }) {
                    Text("Tag", fontSize = 11.sp, color = accentColor)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { appState.selectedIds = emptySet() }) {
                Text("Clear", fontSize = 11.sp, color = textSecondary)
            }
        }
    }
}

private val SORT_DATE = 0
private val SORT_NAME = 1
private val SORT_SIZE = 2
private val SORT_PROGRESS = 3
private val SORT_STATE = 4

private sealed class ListItem {
    data class Single(val id: String, val entry: DownloadEntry) : ListItem()
    data class Combined(val info: CombinedYTDownload, val videoEntry: DownloadEntry?, val audioEntry: DownloadEntry?) : ListItem()
}

@Composable
private fun ColumnHeader(label: String, sortField: Int, icon: ImageVector, appState: XDMAppUIState) {
    val isSorted = appState.sortField == sortField
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.clickable {
            if (isSorted) appState.sortAsc = !appState.sortAsc
            else { appState.sortField = sortField; appState.sortAsc = true }
        }.padding(horizontal = 4.dp)
    ) {
        if (isSorted) {
            Icon(
                if (appState.sortAsc) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                contentDescription = "Sort",
                modifier = Modifier.size(14.dp),
                tint = accentColor
            )
        }
        Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = if (isSorted) accentColor else textSecondary)
        Spacer(Modifier.width(2.dp))
        Text(label, fontSize = 11.sp, fontWeight = if (isSorted) FontWeight.Bold else FontWeight.Normal,
            color = if (isSorted) accentColor else textSecondary)
    }
}

@Composable
private fun DownloadListView(appState: XDMAppUIState, itemBg: Color, variantColor: Color, textColor: Color) {
    val sortField = appState.sortField
    val sortAsc = appState.sortAsc
    val items = remember(appState.downloadIds, appState.progressMap, appState.combinedDownloads, sortField, sortAsc, appState.refreshCounter) {
        val combinedIds = appState.combinedDownloads.values.flatMap { listOfNotNull(it.videoEntryId, it.audioEntryId) }.toSet()
        val singles = appState.downloadIds
            .filter { it !in combinedIds }
            .mapNotNull { id -> XDMApp.getEntry(id)?.let { ListItem.Single(id, it) } }

        val combined = appState.combinedDownloads.values.map { cd ->
            ListItem.Combined(cd, XDMApp.getEntry(cd.videoEntryId), cd.audioEntryId?.let { XDMApp.getEntry(it) })
        }

        val allItems = singles + combined
        val cmp: Comparator<ListItem> = when (sortField) {
            SORT_NAME -> compareBy { when (it) { is ListItem.Single -> it.entry.file?.lowercase() ?: ""; is ListItem.Combined -> it.info.title.lowercase() } }
            SORT_SIZE -> compareBy { when (it) { is ListItem.Single -> it.entry.size; is ListItem.Combined -> { val v = it.videoEntry?.size ?: 0L; val a = it.audioEntry?.size ?: 0L; v + a } } }
            SORT_PROGRESS -> compareBy { when (it) { is ListItem.Single -> it.entry.progress; is ListItem.Combined -> { val v = it.videoEntry?.progress ?: 0; val a = it.audioEntry?.progress ?: 0; (v + a) / 2 } } }
            SORT_STATE -> compareBy { when (it) { is ListItem.Single -> it.entry.state; is ListItem.Combined -> { val v = it.videoEntry?.state ?: 0; val a = it.audioEntry?.state ?: 0; minOf(v, a) } } }
            else -> compareBy { when (it) { is ListItem.Single -> it.entry.date; is ListItem.Combined -> it.videoEntry?.date ?: 0L } }
        }
        if (sortAsc) allItems.sortedWith(cmp) else allItems.sortedWith(cmp.reversed())
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = variantColor.copy(alpha = 0.3f), modifier = Modifier.fillMaxWidth().height(28.dp)) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ColumnHeader("Name", SORT_NAME, Icons.Default.Description, appState)
                Spacer(Modifier.width(8.dp))
                ColumnHeader("Date", SORT_DATE, Icons.Default.DateRange, appState)
                Spacer(Modifier.weight(1f))
                ColumnHeader("Size", SORT_SIZE, Icons.Default.Storage, appState)
                Spacer(Modifier.width(16.dp))
                ColumnHeader("Prog.", SORT_PROGRESS, Icons.Default.TrendingUp, appState)
                Spacer(Modifier.width(16.dp))
                ColumnHeader("Status", SORT_STATE, Icons.Default.Info, appState)
            }
        }
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(48.dp), tint = textSecondary)
                    Spacer(Modifier.height(8.dp))
                    Text("No downloads", color = textSecondary, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("Add a download using File > Add URL or Ctrl+N", color = textSecondary.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp)
            ) {
                items(items, key = {
                    when (it) {
                        is ListItem.Single -> it.id
                        is ListItem.Combined -> "combined:${it.info.combinedId}"
                    }
                }) { item ->
                    when (item) {
                        is ListItem.Single -> {
                            val (id, ent) = item
                            val progress = appState.getProgress(id)
                            val tags = appState.getDownloadTags(id)
                            DownloadItem(
                                entry = ent,
                                progress = progress,
                                tags = tags,
                                isSelected = id in appState.selectedIds,
                                itemBg = itemBg,
                                variantColor = variantColor,
                                textColor = textColor,
                                onClick = {
                                    appState.selectedIds = if (id in appState.selectedIds) appState.selectedIds - id else appState.selectedIds + id
                                },
                                onDoubleClick = {
                                    if (ent.state == XDMConstants.FINISHED) {
                                        try { XDMUtils.openFile(ent.file, XDMApp.getFolder(ent)) } catch (e: Exception) { Logger.log(e) }
                                    } else if (ent.state == XDMConstants.DOWNLOADING || ent.state == XDMConstants.ASSEMBLING) {
                                        appState.showProgress(id)
                                    }
                                },
                                onOpenFile = { try { XDMUtils.openFile(ent.file, XDMApp.getFolder(ent)) } catch (e: Exception) { Logger.log(e) } },
                                onOpenFolder = { try { XDMUtils.openFolder(null, XDMApp.getFolder(ent)) } catch (e: Exception) { Logger.log(e) } },
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
                                onConvert = { appState.convertDialogId = id },
                                onManageTags = { appState.tagPickerDownloadId = id }
                            )
                        }
                        is ListItem.Combined -> {
                            CombinedDownloadItem(item, appState, itemBg, variantColor, textColor)
                        }
                    }
                }
            }
        }
    }
}


@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun DownloadItem(
    entry: DownloadEntry,
    progress: ProgressInfo,
    tags: Set<String> = emptySet(),
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
    onConvert: () -> Unit = {},
    onManageTags: () -> Unit = {}
) {
    var contextMenuExpanded by remember { mutableStateOf(false) }
    val isActive = entry.state == XDMConstants.DOWNLOADING || entry.state == XDMConstants.ASSEMBLING
    val isPausedOrFailed = entry.state == XDMConstants.PAUSED || entry.state == XDMConstants.FAILED
    val isFinished = entry.state == XDMConstants.FINISHED

    Surface(
        color = if (isSelected) accentColor.copy(alpha = 0.12f)
                else if (isFinished) itemBg
                else itemBg,
        modifier = Modifier.fillMaxWidth().height(68.dp)
            .onPointerEvent(PointerEventType.Press) {
                val awtEvent = it.awtEventOrNull
                if (awtEvent != null && awtEvent.isPopupTrigger) contextMenuExpanded = true
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() },
                    onLongPress = { contextMenuExpanded = true }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // State indicator bar
            Surface(
                color = when (entry.state) {
                    XDMConstants.DOWNLOADING -> downloadingColor
                    XDMConstants.ASSEMBLING -> pausedColor
                    XDMConstants.PAUSED -> pausedColor
                    XDMConstants.FAILED -> failedColor
                    XDMConstants.FINISHED -> finishedColor
                    else -> textSecondary
                },
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.width(3.dp).height(44.dp)
            ) {}

            Spacer(Modifier.width(10.dp))

            // File icon area
            Surface(
                color = when (entry.state) {
                    XDMConstants.FINISHED -> successBg
                    XDMConstants.FAILED -> errorBg
                    XDMConstants.PAUSED -> warningBg
                    XDMConstants.DOWNLOADING, XDMConstants.ASSEMBLING -> infoBg
                    else -> darkSurfaceVariant
                },
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        when (entry.category) {
                            XDMConstants.VIDEO -> Icons.Default.Movie
                            XDMConstants.MUSIC -> Icons.Default.MusicNote
                            XDMConstants.DOCUMENTS -> Icons.Default.Description
                            XDMConstants.PROGRAMS -> Icons.Default.Apps
                            XDMConstants.COMPRESSED -> Icons.Default.Archive
                            else -> Icons.Default.InsertDriveFile
                        },
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = when (entry.state) {
                            XDMConstants.FINISHED -> finishedColor
                            XDMConstants.FAILED -> failedColor
                            XDMConstants.PAUSED -> pausedColor
                            XDMConstants.DOWNLOADING, XDMConstants.ASSEMBLING -> downloadingColor
                            else -> textSecondary
                        }
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // Info column
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        entry.file ?: "Unknown",
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                        color = textColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (tags.isNotEmpty()) {
                        Spacer(Modifier.width(4.dp))
                        tags.take(3).forEach { tag ->
                            Surface(
                                color = accentColor.copy(alpha = 0.15f),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(tag, fontSize = 8.sp, color = accentColor,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                                    maxLines = 1)
                            }
                        }
                        if (tags.size > 3) {
                            Text("+${tags.size - 3}", fontSize = 8.sp, color = textSecondary,
                                modifier = Modifier.padding(start = 2.dp))
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val stateChip = when (entry.state) {
                        XDMConstants.DOWNLOADING -> "Downloading"
                        XDMConstants.PAUSED -> "Paused"
                        XDMConstants.FAILED -> "Failed"
                        XDMConstants.FINISHED -> "Completed"
                        XDMConstants.ASSEMBLING -> "Assembling"
                        else -> "Unknown"
                    }
                    val stateColor = when (entry.state) {
                        XDMConstants.DOWNLOADING -> downloadingColor
                        XDMConstants.PAUSED -> pausedColor
                        XDMConstants.FAILED -> failedColor
                        XDMConstants.FINISHED -> finishedColor
                        XDMConstants.ASSEMBLING -> pausedColor
                        else -> textSecondary
                    }
                    Surface(
                        color = stateColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(stateChip, fontSize = 9.sp, color = stateColor,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                    if (isActive) {
                        Spacer(Modifier.width(6.dp))
                        Text(formatSpeed(progress.speed), fontSize = 10.sp, color = textSecondary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (progress.eta.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text("ETA ${progress.eta}", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.7f))
                        }
                    } else if (isPausedOrFailed) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${FormatUtilities.formatSize(entry.downloaded.toDouble())} / ${FormatUtilities.formatSize(entry.size.toDouble())}",
                            fontSize = 10.sp, color = textSecondary)
                    } else if (isFinished) {
                        Spacer(Modifier.width(6.dp))
                        Text(FormatUtilities.formatSize(entry.size.toDouble()), fontSize = 10.sp, color = textSecondary)
                    }
                }
                if (isActive || isPausedOrFailed) {
                    Spacer(Modifier.height(4.dp))
                    val progressVal = if (entry.size > 0) (entry.downloaded.toFloat() / entry.size) else 0f
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { progressVal.coerceIn(0f, 1f) },
                            modifier = Modifier.weight(1f).height(4.dp),
                            color = if (entry.state == XDMConstants.FAILED) failedColor
                                    else if (entry.state == XDMConstants.PAUSED) pausedColor
                                    else downloadingColor,
                            trackColor = variantColor,
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("${entry.progress}%", fontSize = 9.sp, color = textSecondary, modifier = Modifier.width(32.dp))
                    }
                }
            }

            Spacer(Modifier.width(8.dp))

            // Size and date column
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    FormatUtilities.formatSize(entry.size.toDouble()),
                    fontSize = 11.sp,
                    color = textSecondary
                )
                Text(
                    entry.dateStr ?: "",
                    fontSize = 10.sp,
                    color = textSecondary.copy(alpha = 0.6f)
                )
            }

            Spacer(Modifier.width(4.dp))

            // Action button
            Surface(
                color = when (entry.state) {
                    XDMConstants.DOWNLOADING -> pausedColor.copy(alpha = 0.2f)
                    XDMConstants.PAUSED, XDMConstants.FAILED -> downloadingColor.copy(alpha = 0.2f)
                    XDMConstants.FINISHED -> finishedColor.copy(alpha = 0.2f)
                    else -> darkSurfaceVariant
                },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.size(34.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    when (entry.state) {
                        XDMConstants.DOWNLOADING -> {
                            IconButton(onClick = onPause, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.Pause, "Pause", tint = pausedColor, modifier = Modifier.size(18.dp))
                            }
                        }
                        XDMConstants.PAUSED, XDMConstants.FAILED -> {
                            IconButton(onClick = onResume, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.PlayArrow, "Resume", tint = downloadingColor, modifier = Modifier.size(20.dp))
                            }
                        }
                        XDMConstants.ASSEMBLING -> {
                            Icon(Icons.Default.Tune, "Assembling", tint = pausedColor, modifier = Modifier.size(18.dp))
                        }
                        XDMConstants.FINISHED -> {
                            IconButton(onClick = onOpenFile, modifier = Modifier.size(34.dp)) {
                                Icon(Icons.Default.OpenInNew, "Open", tint = finishedColor, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Context menu
            DropdownMenu(expanded = contextMenuExpanded, onDismissRequest = { contextMenuExpanded = false }) {
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

                HorizontalDivider(color = variantColor.copy(alpha = 0.5f))

                DropdownMenuItem(text = { Text("Open Folder") }, onClick = { contextMenuExpanded = false; onOpenFolder() })
                DropdownMenuItem(text = { Text("Save As") }, onClick = { contextMenuExpanded = false; onSaveAs() })
                DropdownMenuItem(text = { Text("Delete") }, onClick = { contextMenuExpanded = false; onDelete() })
                DropdownMenuItem(text = { Text("Delete with File") }, onClick = { contextMenuExpanded = false; onDeleteWithFile() })

                HorizontalDivider(color = variantColor.copy(alpha = 0.5f))

                DropdownMenuItem(text = { Text("Refresh Link") }, onClick = { contextMenuExpanded = false; onRefreshLink() })
                if (!isFinished) {
                    DropdownMenuItem(text = { Text("Preview") }, onClick = { contextMenuExpanded = false; onPreview() })
                }
                DropdownMenuItem(text = { Text("Show Progress") }, onClick = { contextMenuExpanded = false; onShowProgress() })

                HorizontalDivider(color = variantColor.copy(alpha = 0.5f))

                DropdownMenuItem(text = { Text("Copy URL") }, onClick = { contextMenuExpanded = false; onCopyUrl() })
                DropdownMenuItem(text = { Text("Copy File Path") }, onClick = { contextMenuExpanded = false; onCopyFile() })

                HorizontalDivider(color = variantColor.copy(alpha = 0.5f))

                DropdownMenuItem(text = { Text("Convert Media") }, onClick = { contextMenuExpanded = false; onConvert() })
                DropdownMenuItem(text = { Text("Tags") }, onClick = { contextMenuExpanded = false; onManageTags() })
                DropdownMenuItem(text = { Text("Properties") }, onClick = { contextMenuExpanded = false; onProperties() })
            }
        }
    }
}

@Composable
private fun StatusBar(appState: XDMAppUIState, bgColor: Color) {
    Surface(color = bgColor, modifier = Modifier.fillMaxWidth().height(26.dp)) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val total = appState.downloadIds.size
            if (total > 0) {
                Surface(color = textSecondary.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp)) {
                    Text("$total total", fontSize = 10.sp, color = textSecondary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                }
                Spacer(Modifier.width(4.dp))
                if (appState.activeCount > 0) {
                    Surface(color = downloadingColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("${appState.activeCount} active", fontSize = 10.sp, color = downloadingColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (appState.pausedCount > 0) {
                    Surface(color = pausedColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("${appState.pausedCount} paused", fontSize = 10.sp, color = pausedColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (appState.failedCount > 0) {
                    Surface(color = failedColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("${appState.failedCount} failed", fontSize = 10.sp, color = failedColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                    Spacer(Modifier.width(4.dp))
                }
                if (appState.finishedCount > 0) {
                    Surface(color = finishedColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                        Text("${appState.finishedCount} finished", fontSize = 10.sp, color = finishedColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                }
                if (appState.totalSpeed > 0) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.Speed, "Speed", modifier = Modifier.size(12.dp), tint = accentColor)
                    Spacer(Modifier.width(2.dp))
                    Text(appState.formattedSpeed, fontSize = 10.sp, color = accentColor, fontWeight = FontWeight.Medium)
                }
            } else {
                Text("No downloads", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.5f))
            }

            if (appState.selectedIds.isNotEmpty()) {
                Spacer(Modifier.width(12.dp))
                Text("${appState.selectedIds.size} selected", fontSize = 10.sp, color = accentColor)
            }

            Spacer(Modifier.weight(1f))

            val notification = XDMApp.getNotification()
            if (notification > 0) {
                Surface(color = accentColor.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
                    Text("Update available", fontSize = 10.sp, color = accentColor,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
                }
            } else {
                Text("KDM ${XDMApp.APP_VERSION}", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.6f))
            }
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
                        Text(group.desc ?: "", fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
            entry.folder = f.parent
            entry.file = f.name
            XDMApp.fileNameChanged(entry.id)
        }
    } catch (e: Exception) {
        Logger.log(e)
    }
}

@Composable
fun ShortcutsDialog(onDismiss: () -> Unit) {
    val shortcuts = listOf(
        "Ctrl+N" to "New Download",
        "Ctrl+F" to "Focus Search",
        "Ctrl+I" to "Import URLs",
        "Ctrl+A" to "Select All Downloads",
        "Delete / Backspace" to "Delete Selected Downloads",
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keyboard Shortcuts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                shortcuts.forEach { (key, desc) ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(key, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                            modifier = Modifier.width(160.dp))
                        Text(desc, fontSize = 12.sp, color = textSecondary)
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("OK") } }
    )
}

@Composable
fun AddTagDialog(appState: XDMAppUIState, onDismiss: () -> Unit) {
    var tagName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Tag") },
        text = {
            OutlinedTextField(
                value = tagName,
                onValueChange = { tagName = it },
                label = { Text("Tag name") },
                modifier = Modifier.width(300.dp),
                singleLine = true
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    appState.addTag(tagName.trim())
                    onDismiss()
                },
                enabled = tagName.isNotBlank()
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ManageTagsDialog(appState: XDMAppUIState, onDismiss: () -> Unit) {
    var editingTag by remember { mutableStateOf<String?>(null) }
    var editValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Tags") },
        text = {
            Column(modifier = Modifier.width(350.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (appState.availableTags.isEmpty()) {
                    Text("No tags created yet.", fontSize = 12.sp, color = textSecondary)
                } else {
                    appState.availableTags.forEach { tag ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (editingTag == tag.name) {
                                OutlinedTextField(
                                    value = editValue,
                                    onValueChange = { editValue = it },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f).height(48.dp),
                                    textStyle = LocalTextStyle.current.copy(fontSize = 12.sp)
                                )
                                IconButton(onClick = {
                                    appState.renameTag(tag.name, editValue.trim())
                                    editingTag = null
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Check, "Save", modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { editingTag = null }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Close, "Cancel", modifier = Modifier.size(16.dp))
                                }
                            } else {
                                Text(tag.name, fontSize = 12.sp, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    editingTag = tag.name
                                    editValue = tag.name
                                }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Edit, "Rename", tint = accentColor, modifier = Modifier.size(16.dp))
                                }
                                IconButton(onClick = { appState.removeTag(tag.name) }, modifier = Modifier.size(32.dp)) {
                                    Icon(Icons.Default.Delete, "Remove", tint = failedColor, modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
fun BatchTagDialog(appState: XDMAppUIState, ids: List<String>, onDismiss: () -> Unit) {
    var selectedTags by remember { mutableStateOf(setOf<String>()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tag ${ids.size} Downloads") },
        text = {
            Column(modifier = Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                appState.availableTags.forEach { tag ->
                    val hasTag = tag.name in selectedTags
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().clickable {
                            selectedTags = if (hasTag) selectedTags - tag.name else selectedTags + tag.name
                        }
                    ) {
                        Checkbox(checked = hasTag, onCheckedChange = {
                            selectedTags = if (hasTag) selectedTags - tag.name else selectedTags + tag.name
                        })
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(16.dp), tint = textSecondary)
                        Spacer(Modifier.width(8.dp))
                        Text(tag.name, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                ids.forEach { id ->
                    selectedTags.forEach { tag -> appState.toggleDownloadTag(id, tag) }
                }
                onDismiss()
            }) { Text("Apply to ${ids.size} downloads") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun TagPickerDialog(appState: XDMAppUIState, id: String, onDismiss: () -> Unit) {
    val currentTags = appState.getDownloadTags(id)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assign Tags") },
        text = {
            Column(modifier = Modifier.width(300.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (appState.availableTags.isEmpty()) {
                    Text("No tags available. Create tags first in the side panel.",
                        fontSize = 12.sp, color = textSecondary)
                } else {
                    appState.availableTags.forEach { tag ->
                        val hasTag = tag.name in currentTags
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().clickable {
                                appState.toggleDownloadTag(id, tag.name)
                            }
                        ) {
                            Checkbox(checked = hasTag, onCheckedChange = { appState.toggleDownloadTag(id, tag.name) })
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.Default.Label, contentDescription = null, modifier = Modifier.size(16.dp), tint = textSecondary)
                            Spacer(Modifier.width(8.dp))
                            Text(tag.name, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = onDismiss) { Text("Done") } }
    )
}

@Composable
fun ImportUrlsDialog(onDismiss: () -> Unit) {
    var urlsText by remember { mutableStateOf("") }
    var startNow by remember { mutableStateOf(true) }
    var importedFile by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import URLs") },
        text = {
            Column(modifier = Modifier.width(480.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Enter URLs (one per line):", fontSize = 12.sp, color = textSecondary, modifier = Modifier.weight(1f))
                    if (importedFile != null) {
                        Text("Loaded from: ${importedFile}", fontSize = 10.sp, color = accentColor)
                    }
                }
                OutlinedTextField(
                    value = urlsText,
                    onValueChange = { urlsText = it; importedFile = null },
                    modifier = Modifier.fillMaxWidth().height(160.dp),
                    textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
                    placeholder = { Text("https://...", fontSize = 11.sp) }
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val chooser = JFileChooser()
                            chooser.fileFilter = FileNameExtensionFilter("Text files (*.txt, *.csv, *.urls)", "txt", "csv", "urls")
                            chooser.isAcceptAllFileFilterUsed = true
                            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                                try {
                                    val text = chooser.selectedFile.readText()
                                    val lines = text.lines()
                                        .map { it.trim() }
                                        .filter { it.isNotBlank() && !it.startsWith("#") }
                                    urlsText = if (urlsText.isBlank()) lines.joinToString("\n") else urlsText + "\n" + lines.joinToString("\n")
                                    importedFile = chooser.selectedFile.name
                                } catch (e: Exception) {
                                    Logger.log(e)
                                }
                            }
                        },
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.FileOpen, "Open File", modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("From File", fontSize = 11.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Checkbox(checked = startNow, onCheckedChange = { startNow = it })
                    Text("Start now", fontSize = 11.sp)
                }
                val urlCount = urlsText.lines().count { it.isNotBlank() }
                if (urlCount > 0) {
                    Text("$urlCount URL(s) ready to import", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.7f))
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val urls = urlsText.lines().map { it.trim() }.filter { it.isNotBlank() }
                    for (url in urls) {
                        try {
                            val meta = HttpMetadata().apply { this.url = url }
                            val fileName = XDMUtils.getFileName(url)
                            XDMApp.createDownload(fileName, null, meta, startNow, "", 0, 0)
                        } catch (e: Exception) {
                            Logger.log(e)
                        }
                    }
                    onDismiss()
                },
                enabled = urlsText.isNotBlank()
            ) { Text("Import ${urlsText.lines().count { it.isNotBlank() }} URLs") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CombinedDownloadItem(
    item: ListItem.Combined,
    appState: XDMAppUIState,
    itemBg: Color,
    variantColor: Color,
    textColor: Color
) {
    val cd = item.info
    val videoId = cd.videoEntryId
    val audioId = cd.audioEntryId
    val videoEntry = item.videoEntry
    val audioEntry = item.audioEntry
    val videoProgressInfo = appState.getProgress(videoId)
    val audioProgressInfo = if (audioId != null) appState.getProgress(audioId) else ProgressInfo()
    var contextMenuExpanded by remember { mutableStateOf(false) }
    val combinedId = "combined:${cd.combinedId}"
    val isSelected = combinedId in appState.selectedIds || listOfNotNull(videoId, audioId).all { it in appState.selectedIds }

    // Use progressMap for real-time values, entry.progress as fallback
    val videoProgressVal = if (videoProgressInfo.progress > 0) videoProgressInfo.progress else (videoEntry?.progress ?: 0)
    val audioProgressVal = if (audioProgressInfo.progress > 0) audioProgressInfo.progress else (audioEntry?.progress ?: 0)
    val videoState = videoEntry?.state ?: XDMConstants.PAUSED
    val audioState = audioEntry?.state ?: XDMConstants.PAUSED
    val bothDone = videoState == XDMConstants.FINISHED && (audioId == null || audioState == XDMConstants.FINISHED)
    val anyActive = videoState == XDMConstants.DOWNLOADING || audioState == XDMConstants.DOWNLOADING
    val anyPaused = videoState == XDMConstants.PAUSED || (audioId != null && audioState == XDMConstants.PAUSED)
    val anyFailed = videoState == XDMConstants.FAILED || (audioId != null && audioState == XDMConstants.FAILED)
    val totalSpeed = videoProgressInfo.speed + audioProgressInfo.speed

    val isMerging = cd.merging || (bothDone && !cd.mergeFailed && cd.mergedFilePath == null)

    val statusText = when {
        cd.mergeFailed -> "Merge failed"
        cd.mergedFilePath != null -> "Completed - merged"
        isMerging -> "Merging..."
        anyActive -> if (audioId != null) "Downloading video+audio (${formatSpeed(totalSpeed)})" else "Downloading (${formatSpeed(videoProgressInfo.speed)})"
        anyPaused -> "Paused"
        anyFailed -> "Failed"
        bothDone -> "Completed"
        else -> "Pending"
    }

    fun mergeProgress(): Float {
        if (!isMerging) return 1f
        if (cd.mergeFailed) return 0f
        if (cd.mergedFilePath != null) return 1f
        return 0.5f
    }

    Surface(
        color = if (isSelected) accentColor.copy(alpha = 0.12f) else itemBg,
        modifier = Modifier.fillMaxWidth().height(80.dp)
            .onPointerEvent(PointerEventType.Press) {
                val awtEvent = it.awtEventOrNull
                if (awtEvent != null && awtEvent.isPopupTrigger) contextMenuExpanded = true
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        val ids = listOfNotNull(videoId, audioId)
                        appState.selectedIds = if (ids.all { it in appState.selectedIds }) {
                            appState.selectedIds - ids.toSet() - combinedId
                        } else {
                            appState.selectedIds + ids.toSet()
                        }
                    },
                    onLongPress = { contextMenuExpanded = true }
                )
            }
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // State indicator
            Surface(
                color = when {
                    isMerging || anyActive -> downloadingColor
                    anyPaused -> pausedColor
                    anyFailed -> failedColor
                    cd.mergedFilePath != null || bothDone -> finishedColor
                    else -> textSecondary
                },
                shape = RoundedCornerShape(2.dp),
                modifier = Modifier.width(3.dp).height(44.dp)
            ) {}

            Spacer(Modifier.width(10.dp))
            Surface(
                color = infoBg,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.PlaylistPlay, "YT", modifier = Modifier.size(22.dp),
                        tint = if (isMerging) pausedColor else accentColor)
                }
            }

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(cd.title, fontWeight = FontWeight.Medium, fontSize = 13.sp, color = textColor,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = when {
                            isMerging -> pausedColor.copy(alpha = 0.15f)
                            anyActive -> downloadingColor.copy(alpha = 0.15f)
                            cd.mergedFilePath != null -> finishedColor.copy(alpha = 0.15f)
                            anyPaused -> pausedColor.copy(alpha = 0.15f)
                            anyFailed -> failedColor.copy(alpha = 0.15f)
                            else -> textSecondary.copy(alpha = 0.15f)
                        },
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(statusText, fontSize = 9.sp,
                            color = when {
                                isMerging -> pausedColor
                                cd.mergeFailed -> failedColor
                                cd.mergedFilePath != null -> finishedColor
                                anyActive -> downloadingColor
                                anyPaused -> pausedColor
                                anyFailed -> failedColor
                                else -> textSecondary
                            },
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp))
                    }
                    if (audioId != null && !isMerging) {
                        val vLabel = if (videoEntry != null) "${videoProgressVal}% video" else ""
                        val aLabel = if (audioEntry != null) "${audioProgressVal}% audio" else ""
                        Text("$vLabel  $aLabel", fontSize = 10.sp, color = textSecondary)
                    } else if (isMerging) {
                        val lastLine = cd.ffmpegOutput.lines().lastOrNull { it.isNotBlank() } ?: ""
                        Text(if (lastLine.length > 60) lastLine.takeLast(60) + "..." else if (lastLine.isNotBlank()) lastLine else "Running ffmpeg -c copy ...",
                            fontSize = 9.sp, color = pausedColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Spacer(Modifier.height(4.dp))
                if (isMerging) {
                    LinearProgressIndicator(progress = { mergeProgress() },
                        modifier = Modifier.fillMaxWidth().height(4.dp), color = pausedColor, trackColor = variantColor)
                } else if (audioId != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        LinearProgressIndicator(progress = { videoProgressVal / 100.0f },
                            modifier = Modifier.weight(1f).height(4.dp), color = downloadingColor, trackColor = variantColor)
                        LinearProgressIndicator(progress = { audioProgressVal / 100.0f },
                            modifier = Modifier.weight(1f).height(4.dp), color = pausedColor, trackColor = variantColor)
                    }
                } else {
                    LinearProgressIndicator(progress = { videoProgressVal / 100.0f },
                        modifier = Modifier.fillMaxWidth().height(4.dp), color = downloadingColor, trackColor = variantColor)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                val totalSize = (videoEntry?.size ?: 0L) + (audioEntry?.size ?: 0L)
                Text(FormatUtilities.formatSize(totalSize.toDouble()), fontSize = 11.sp, color = textSecondary)
                Text(videoEntry?.dateStr ?: "", fontSize = 10.sp, color = textSecondary.copy(alpha = 0.7f))
            }
        }

        DropdownMenu(expanded = contextMenuExpanded, onDismissRequest = { contextMenuExpanded = false }) {
            Text(cd.title, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
            HorizontalDivider(color = variantColor.copy(alpha = 0.5f))

            if (videoEntry != null) {
                Text("Video (${videoProgressVal}%)", fontSize = 10.sp, color = textSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                if (videoEntry.state == XDMConstants.DOWNLOADING)
                    DropdownMenuItem(text = { Text("Pause video") }, onClick = { contextMenuExpanded = false; XDMApp.pauseDownload(videoId) })
                if (videoEntry.state == XDMConstants.PAUSED || videoEntry.state == XDMConstants.FAILED)
                    DropdownMenuItem(text = { Text("Resume video") }, onClick = { contextMenuExpanded = false; XDMApp.resumeDownload(videoId, true) })
                DropdownMenuItem(text = { Text("Open video folder") }, onClick = {
                    contextMenuExpanded = false
                    try { XDMUtils.openFolder(null, XDMApp.getFolder(videoEntry)) } catch (e: Exception) { Logger.log(e) }
                })
                DropdownMenuItem(text = { Text("Delete video") }, onClick = { contextMenuExpanded = false; XDMApp.deleteDownloads(listOf(videoId), true); appState.refresh() })
            }
            if (audioEntry != null && audioId != null) {
                HorizontalDivider(color = variantColor.copy(alpha = 0.5f))
                Text("Audio (${audioProgressVal}%)", fontSize = 10.sp, color = textSecondary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp))
                if (audioEntry.state == XDMConstants.DOWNLOADING)
                    DropdownMenuItem(text = { Text("Pause audio") }, onClick = { contextMenuExpanded = false; XDMApp.pauseDownload(audioId) })
                if (audioEntry.state == XDMConstants.PAUSED || audioEntry.state == XDMConstants.FAILED)
                    DropdownMenuItem(text = { Text("Resume audio") }, onClick = { contextMenuExpanded = false; XDMApp.resumeDownload(audioId, true) })
                DropdownMenuItem(text = { Text("Open audio folder") }, onClick = {
                    contextMenuExpanded = false
                    try { XDMUtils.openFolder(null, XDMApp.getFolder(audioEntry)) } catch (e: Exception) { Logger.log(e) }
                })
                DropdownMenuItem(text = { Text("Delete audio") }, onClick = { contextMenuExpanded = false; XDMApp.deleteDownloads(listOf(audioId), true); appState.refresh() })
            }

            HorizontalDivider(color = variantColor.copy(alpha = 0.5f))
            DropdownMenuItem(text = { Text("Open temp folder") }, onClick = {
                contextMenuExpanded = false
                try { XDMUtils.openFolder(null, cd.tempFolder) } catch (e: Exception) { Logger.log(e) }
            })
            DropdownMenuItem(text = { Text("Delete both") }, onClick = {
                contextMenuExpanded = false
                listOfNotNull(videoId, audioId).let { ids -> XDMApp.deleteDownloads(ids, true) }
                appState.combinedDownloads = appState.combinedDownloads - cd.combinedId
                appState.refresh()
            })
            if (cd.mergedFilePath != null) {
                DropdownMenuItem(text = { Text("Open merged file") }, onClick = {
                    contextMenuExpanded = false
                    try { XDMUtils.openFile(File(cd.mergedFilePath).name, File(cd.mergedFilePath).parent) } catch (e: Exception) { Logger.log(e) }
                })
            }
            if (cd.mergeFailed) {
                DropdownMenuItem(text = { Text("Open output folder") }, onClick = {
                    contextMenuExpanded = false
                    try { XDMUtils.openFolder(null, cd.outputFolder) } catch (e: Exception) { Logger.log(e) }
                })
            }
        }
    }
}

@Composable
fun ExportDialog(appState: XDMAppUIState, onDismiss: () -> Unit) {
    var exporting by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!exporting) onDismiss() },
        title = { Text("Export Data") },
        text = {
            Column(modifier = Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done) {
                    Text("Export complete!", fontSize = 12.sp, color = finishedColor)
                    Text("Settings, download list, tags, and metadata have been exported.", fontSize = 11.sp, color = textSecondary)
                } else if (errorMsg != null) {
                    Text("Error: $errorMsg", fontSize = 12.sp, color = failedColor)
                } else if (exporting) {
                    Text("Exporting data...", fontSize = 12.sp)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Export KDM settings, download list, tags, and metadata to a .kdmx file.", fontSize = 11.sp, color = textSecondary)
                    Text("Actual downloaded files are NOT included.", fontSize = 11.sp, color = textSecondary.copy(alpha = 0.7f))
                }
            }
        },
        confirmButton = {
            if (done || errorMsg != null) {
                Button(onClick = onDismiss) { Text("Close") }
            } else {
                Button(
                    onClick = {
                        val chooser = JFileChooser(Config.getInstance().downloadFolder)
                        chooser.selectedFile = File("kdm-export.kdmx")
                        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                            exporting = true
                            var target = chooser.selectedFile
                            if (!target.name.contains(".")) target = File(target.absolutePath + ".kdmx")
                            try {
                                XDMApp.exportData(target)
                                done = true
                            } catch (e: Exception) {
                                errorMsg = e.message ?: "Export failed"
                            }
                            exporting = false
                        }
                    },
                    enabled = !exporting
                ) { Text("Export...") }
            }
        },
        dismissButton = {
            if (!exporting) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ImportDialog(appState: XDMAppUIState, onDismiss: () -> Unit) {
    var importing by remember { mutableStateOf(false) }
    var done by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!importing) onDismiss() },
        title = { Text("Import Data") },
        text = {
            Column(modifier = Modifier.width(380.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (done) {
                    Text("Import complete!", fontSize = 12.sp, color = finishedColor)
                    Text("Settings, download list, tags, and metadata have been restored.", fontSize = 11.sp, color = textSecondary)
                    Text("Reloading data...", fontSize = 11.sp, color = accentColor)
                } else if (errorMsg != null) {
                    Text("Error: $errorMsg", fontSize = 12.sp, color = failedColor)
                } else if (importing) {
                    Text("Importing data...", fontSize = 12.sp)
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    Text("Import KDM data from a .kdmx file.", fontSize = 11.sp, color = textSecondary)
                    Text("This will replace current settings and download list.", fontSize = 11.sp, color = textSecondary.copy(alpha = 0.7f))
                    Text("Active downloads will be paused.", fontSize = 11.sp, color = textSecondary.copy(alpha = 0.7f))
                }
            }
        },
        confirmButton = {
            if (done || errorMsg != null) {
                Button(onClick = { onDismiss() }) { Text("Close") }
            } else {
                Button(
                    onClick = {
                        val chooser = JFileChooser(Config.getInstance().downloadFolder)
                        chooser.fileFilter = javax.swing.filechooser.FileNameExtensionFilter("KDM Export (*.kdmx)", "kdmx")
                        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                            importing = true
                            try {
                                XDMApp.importData(chooser.selectedFile)
                                // Reload everything
                                Config.getInstance().load()
                                XDMApp.reloadDownloadList()
                                appState.refresh()
                                appState.loadTags()
                                done = true
                            } catch (e: Exception) {
                                errorMsg = e.message ?: "Import failed"
                            }
                            importing = false
                        }
                    },
                    enabled = !importing
                ) { Text("Import...") }
            }
        },
        dismissButton = {
            if (!importing) TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatSpeed(speed: Long): String {
    if (speed <= 0) return "Starting..."
    return FormatUtilities.formatSize(speed.toDouble()) + "/s"
}
