package xdman

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import xdman.downloaders.metadata.HttpMetadata
import xdman.ui.DownloadProgressWindow
import xdman.ui.MainWindowUI
import xdman.util.Logger
import xdman.util.XDMUtils
import xdman.ytdlp.YTMergeTracker
import java.awt.Dimension
import java.io.File

data class TagInfo(val name: String, val color: Long = 0xFFFF9800)

data class ProgressInfo(
    var downloaded: Long = 0,
    var size: Long = 0,
    var progress: Int = 0,
    var speed: Long = 0,
    var eta: String = "",
    var elapsed: Long = 0L
)

class XDMAppUIState {
    var darkMode by mutableStateOf(Config.getInstance().isDarkMode)
    var downloadIds by mutableStateOf(listOf<String>())
    var categoryFilter by mutableStateOf(XDMConstants.ALL)
    var stateFilter by mutableStateOf(XDMConstants.ALL)
    var searchText by mutableStateOf("")
    var sortField by mutableStateOf(0)
    var sortAsc by mutableStateOf(false)
    var queueIdFilter by mutableStateOf<String?>(null)
    var selectedIds by mutableStateOf(setOf<String>())
    var notificationState by mutableStateOf(-1)

    // Dialog states
    var showNewDownloadDialog by mutableStateOf(false)
    var showImportUrlsDialog by mutableStateOf(false)
    var showSettingsDialog by mutableStateOf(false)
    var showAboutDialog by mutableStateOf(false)
    var showShortcutsDialog by mutableStateOf(false)
    var showExportDialog by mutableStateOf(false)
    var showImportDialog by mutableStateOf(false)
    var showYTDownloadDialog by mutableStateOf(false)
    var showYTPlaylistDialog by mutableStateOf(false)
    var showYTSetupDialog by mutableStateOf(false)
    var newDownloadMetadata: HttpMetadata? by mutableStateOf(null)
    var newDownloadFileName by mutableStateOf("")
    var newDownloadFolder: String? by mutableStateOf(null)

    // Tag dialogs
    var showAddTagDialog by mutableStateOf(false)
    var showManageTagsDialog by mutableStateOf(false)
    var tagPickerDownloadId: String? by mutableStateOf(null)
    var showBatchTagDialog by mutableStateOf(false)
    var batchTagIds: List<String> by mutableStateOf(emptyList())

    // Detail dialog states
    var propertiesDialogId: String? by mutableStateOf(null)
    var refreshLinkId: String? by mutableStateOf(null)
    var convertDialogId: String? by mutableStateOf(null)

    // Progress tracking
    var progressMap by mutableStateOf(mapOf<String, ProgressInfo>())
    var activeProgressWindows by mutableStateOf(setOf<String>())

    // Combined YT downloads
    var combinedDownloads by mutableStateOf(mapOf<String, CombinedYTDownload>())
    var refreshCounter by mutableStateOf(0)
    fun registerCombined(dl: CombinedYTDownload) {
        combinedDownloads = combinedDownloads + (dl.combinedId to dl)
        saveCombined()
    }
    fun isPartOfCombined(id: String): String? {
        return combinedDownloads.entries.firstOrNull { (_, cd) ->
            cd.videoEntryId == id || cd.audioEntryId == id
        }?.key
    }

    fun saveCombined() {
        try {
            val obj = org.json.simple.JSONObject()
            val arr = org.json.simple.JSONArray()
            combinedDownloads.values.forEach { cd ->
                val c = org.json.simple.JSONObject()
                c["combinedId"] = cd.combinedId
                c["title"] = cd.title
                c["videoEntryId"] = cd.videoEntryId
                c["audioEntryId"] = cd.audioEntryId ?: ""
                c["videoExt"] = cd.videoExt
                c["audioExt"] = cd.audioExt ?: ""
                c["tempFolder"] = cd.tempFolder
                c["outputFolder"] = cd.outputFolder
                if (cd.mergedFilePath != null) c["mergedFilePath"] = cd.mergedFilePath
                if (cd.mergeFailed) c["mergeFailed"] = true
                arr.add(c)
            }
            obj["combined"] = arr
            java.io.FileWriter(File(Config.getInstance().dataFolder, "yt-combined.json")).use { writer ->
                obj.writeJSONString(writer)
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    fun loadCombined() {
        try {
            val file = File(Config.getInstance().dataFolder, "yt-combined.json")
            if (!file.exists()) return
            val parser = org.json.simple.parser.JSONParser()
            val obj = parser.parse(java.io.FileReader(file)) as org.json.simple.JSONObject
            val arr = obj["combined"] as? org.json.simple.JSONArray ?: return
            val loaded = arr.mapNotNull { entry ->
                val c = entry as? org.json.simple.JSONObject ?: return@mapNotNull null
                CombinedYTDownload(
                    combinedId = c["combinedId"] as? String ?: return@mapNotNull null,
                    title = c["title"] as? String ?: return@mapNotNull null,
                    videoEntryId = c["videoEntryId"] as? String ?: return@mapNotNull null,
                    audioEntryId = (c["audioEntryId"] as? String)?.takeIf { it.isNotEmpty() },
                    videoExt = c["videoExt"] as? String ?: "",
                    audioExt = (c["audioExt"] as? String)?.takeIf { it.isNotEmpty() },
                    tempFolder = c["tempFolder"] as? String ?: "",
                    mergedFilePath = c["mergedFilePath"] as? String,
                    mergeFailed = c["mergeFailed"] == true,
                    outputFolder = c["outputFolder"] as? String ?: ""
                )
            }
            combinedDownloads = loaded.associateBy { it.combinedId }
            // Re-register pending merges where both parts are finished but no result yet
            loaded.forEach { cd ->
                if (!cd.mergeFailed && cd.mergedFilePath == null) {
                    val v = XDMApp.getEntry(cd.videoEntryId)
                    val a = cd.audioEntryId?.let { XDMApp.getEntry(it) }
                    if (v?.state == XDMConstants.FINISHED && a?.state == XDMConstants.FINISHED) {
                        YTMergeTracker.reRegister(cd)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    fun refresh() {
        var ids = XDMApp.getDownloadList(categoryFilter, stateFilter, searchText, queueIdFilter ?: "ALL")
        val activeTagFilter = tagFilter
        if (activeTagFilter != null) {
            ids = ids.filter { id -> downloadTags[id]?.contains(activeTagFilter) == true }
        }
        // Clean up combined entries where both parts no longer exist
        val stale = combinedDownloads.entries.filter { (_, cd) ->
            XDMApp.getEntry(cd.videoEntryId) == null && (cd.audioEntryId == null || XDMApp.getEntry(cd.audioEntryId) == null)
        }.map { it.key }
        if (stale.isNotEmpty()) combinedDownloads = combinedDownloads - stale.toSet()
        // Exclude IDs that are part of combined downloads
        val combinedPartIds = combinedDownloads.values.flatMap { listOfNotNull(it.videoEntryId, it.audioEntryId) }.toSet()
        ids = ids.filter { it !in combinedPartIds }
        downloadIds = ids
    }

    fun getProgress(id: String): ProgressInfo = progressMap[id] ?: ProgressInfo()

    fun showProgress(id: String) {
        activeProgressWindows = activeProgressWindows + id
    }

    fun hideProgress(id: String) {
        activeProgressWindows = activeProgressWindows - id
    }

    // Tag system
    var availableTags by mutableStateOf(listOf<TagInfo>())
    var downloadTags by mutableStateOf(mapOf<String, Set<String>>())
    var tagFilter: String? by mutableStateOf(null)

    fun addTag(name: String) {
        if (name.isNotBlank() && availableTags.none { it.name.equals(name, true) }) {
            availableTags = availableTags + TagInfo(name)
            saveTags()
        }
    }

    fun removeTag(name: String) {
        availableTags = availableTags.filter { !it.name.equals(name, true) }
        downloadTags = downloadTags.mapValues { (_, v) -> v.filter { !it.equals(name, true) }.toSet() }
        if (tagFilter?.equals(name, true) == true) tagFilter = null
        saveTags()
    }

    fun renameTag(oldName: String, newName: String) {
        if (newName.isBlank() || oldName.equals(newName, true)) return
        availableTags = availableTags.map { if (it.name.equals(oldName, true)) it.copy(name = newName) else it }
        downloadTags = downloadTags.mapValues { (_, v) ->
            v.map { if (it.equals(oldName, true)) newName else it }.toSet()
        }
        if (tagFilter?.equals(oldName, true) == true) tagFilter = newName
        saveTags()
    }

    fun getDownloadTags(id: String): Set<String> = downloadTags[id] ?: emptySet()

    fun toggleDownloadTag(id: String, tag: String) {
        val current = downloadTags[id] ?: emptySet()
        downloadTags = if (tag in current) {
            val updated = downloadTags + (id to (current - tag))
            if (updated[id]!!.isEmpty()) updated - id else updated
        } else {
            downloadTags + (id to (current + tag))
        }
        saveTags()
    }

    private fun saveTags() {
        try {
            val obj = org.json.simple.JSONObject()
            val tagsArr = org.json.simple.JSONArray()
            availableTags.forEach { t ->
                val tObj = org.json.simple.JSONObject()
                tObj.put("name", t.name)
                tObj.put("color", java.lang.Long.valueOf(t.color))
                tagsArr.add(tObj)
            }
            obj.put("tags", tagsArr)
            val dtObj = org.json.simple.JSONObject()
            downloadTags.forEach { (id, tags) ->
                val arr = org.json.simple.JSONArray()
                tags.forEach { arr.add(it) }
                dtObj.put(id, arr)
            }
            obj.put("downloadTags", dtObj)
            java.io.FileWriter(File(Config.getInstance().dataFolder, "tags.json")).use { writer ->
                obj.writeJSONString(writer)
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    fun loadTags() {
        try {
            val file = File(Config.getInstance().dataFolder, "tags.json")
            if (!file.exists()) return
            val parser = org.json.simple.parser.JSONParser()
            val obj = parser.parse(java.io.FileReader(file)) as org.json.simple.JSONObject
            val tagsArr = obj["tags"] as? org.json.simple.JSONArray ?: return
            availableTags = tagsArr.mapNotNull { entry ->
                val tObj = entry as? org.json.simple.JSONObject ?: return@mapNotNull null
                val name = tObj["name"] as? String ?: return@mapNotNull null
                val color = tObj["color"] as? Long ?: 0xFFFF9800
                TagInfo(name, color)
            }
            val dtObj = obj["downloadTags"] as? org.json.simple.JSONObject ?: return
            downloadTags = dtObj.mapNotNull { (key, value) ->
                val id = key as? String ?: return@mapNotNull null
                val arr = value as? org.json.simple.JSONArray ?: return@mapNotNull null
                id to arr.mapNotNull { it as? String }.toSet()
            }.toMap()
        } catch (e: Exception) {
            Logger.log(e)
        }
    }
}

fun main() = application {
    Logger.log("loading...")
    Logger.log(System.getProperty("java.version") + " " + System.getProperty("os.version"))

    System.setProperty("http.KeepAlive.remainingData", "0")
    System.setProperty("http.KeepAlive.queuedConnections", "0")
    System.setProperty("sun.net.http.errorstream.enableBuffering", "false")

    Config.getInstance().load()
    Config.getInstance().setAutoShutdown(false)
    if (Config.getInstance().zoomLevelIndex > 0) {
        val zoom = XDMApp.ZOOM_LEVEL_VALUES[Config.getInstance().zoomLevelIndex]
        println("Zoom index: " + Config.getInstance().zoomLevelIndex + " " + zoom)
        System.setProperty("sun.java2d.uiScale.enabled", "true")
        System.setProperty("sun.java2d.uiScale", String.format("%.2f", zoom))
    }

    val appState = remember { XDMAppUIState() }
    val startTimes = remember { mutableMapOf<String, Long>() }

    Tray(
        icon = painterResource("icons/xhdpi/icon.png"),
        menu = {
            Item("Show KDM", onClick = {
                // Focus window
            })
            Separator()
            Item("Exit", onClick = {
                XDMApp.exit()
                exitApplication()
            })
        }
    )

    // Register XDMApp callbacks
    XDMApp.onNewDownloadRequest = { metadata, fileName, folder ->
        appState.newDownloadMetadata = metadata
        appState.newDownloadFileName = fileName ?: ""
        appState.newDownloadFolder = folder
        appState.showNewDownloadDialog = true
    }
    XDMApp.onProgressUpdate = { id, downloaded, size, progress, speed, _ ->
        val startTime = startTimes.getOrPut(id) { System.currentTimeMillis() }
        val elapsed = System.currentTimeMillis() - startTime
        val eta = if (speed > 0 && size > 0) {
            val remaining = size - downloaded
            if (remaining > 0) formatDuration(remaining * 1000 / speed) else ""
        } else ""
        appState.progressMap = appState.progressMap + (id to ProgressInfo(downloaded, size, progress, speed, eta, elapsed))
        if (downloaded >= size && size > 0) {
            appState.progressMap = appState.progressMap - id
            appState.activeProgressWindows = appState.activeProgressWindows - id
            startTimes.remove(id)
        }
    }

    // Initialize
    XDMApp.start(arrayOf())

    // Initial refresh
    LaunchedEffect(Unit) {
        appState.refresh()
        appState.loadTags()
        appState.loadCombined()
    }

    // Listen for download list changes
    XDMApp.addListener(object : ListChangeListener {
        override fun listChanged() { appState.refresh(); appState.refreshCounter++ }
        override fun listItemUpdated(id: String) { appState.refresh(); appState.refreshCounter++ }
    })
    XDMApp.addListener(YTMergeTracker)
    YTMergeTracker.onMergeStart = { baseName ->
        appState.combinedDownloads = appState.combinedDownloads.toMutableMap().apply {
            val existing = this[baseName]
            if (existing != null) this[baseName] = existing.copy(merging = true, ffmpegOutput = "")
        }
        appState.refreshCounter++
        appState.refresh()
    }
    YTMergeTracker.onMergeOutput = { baseName, line ->
        appState.combinedDownloads = appState.combinedDownloads.toMutableMap().apply {
            val existing = this[baseName]
            if (existing != null && existing.merging) {
                val output = existing.ffmpegOutput + line + "\n"
                val lines = output.lines()
                val trimmed = if (lines.size > 20) lines.takeLast(20).joinToString("\n") else output
                this[baseName] = existing.copy(ffmpegOutput = trimmed)
            }
        }
        appState.refreshCounter++
    }
    YTMergeTracker.onMergeEvent = { baseName, success, mergedPath ->
        appState.combinedDownloads = appState.combinedDownloads.toMutableMap().apply {
            val existing = this[baseName]
            if (existing != null) {
                this[baseName] = existing.copy(
                    mergedFilePath = if (success) mergedPath else null,
                    mergeFailed = !success,
                    merging = false,
                    audioEntryId = if (success) null else existing.audioEntryId,
                    ffmpegOutput = if (success) "" else existing.ffmpegOutput
                )
            }
        }
        appState.saveCombined()
        appState.refreshCounter++
        appState.refresh()
    }

    Window(
        onCloseRequest = {
            XDMApp.exit()
            exitApplication()
        },
        title = XDMApp.XDM_WINDOW_TITLE,
        icon = painterResource("icons/xhdpi/icon.png"),
        state = rememberWindowState(
            position = WindowPosition(Alignment.Center),
            size = DpSize(950.dp, 600.dp)
        )
    ) {
        window.minimumSize = Dimension(700, 400)
        MainWindowUI(appState)

        // Dialogs
        if (appState.showNewDownloadDialog) {
            xdman.ui.NewDownloadDialog(
                metadata = appState.newDownloadMetadata,
                fileName = appState.newDownloadFileName,
                folder = appState.newDownloadFolder,
                onDismiss = {
                    appState.showNewDownloadDialog = false
                    appState.newDownloadMetadata = null
                    appState.newDownloadFileName = ""
                    appState.newDownloadFolder = null
                },
                onStartDownload = { file, folder, metadata, now, queueId, fmtIdx, streamIdx, category ->
                    XDMApp.createDownload(file, folder, metadata, now, queueId, fmtIdx, streamIdx, category)
                    appState.showNewDownloadDialog = false
                    appState.newDownloadMetadata = null
                }
            )
        }
        if (appState.showImportUrlsDialog) {
            xdman.ui.ImportUrlsDialog(
                onDismiss = { appState.showImportUrlsDialog = false }
            )
        }
        if (appState.showAddTagDialog) {
            xdman.ui.AddTagDialog(
                appState = appState,
                onDismiss = { appState.showAddTagDialog = false }
            )
        }
        if (appState.showManageTagsDialog) {
            xdman.ui.ManageTagsDialog(
                appState = appState,
                onDismiss = { appState.showManageTagsDialog = false }
            )
        }
        if (appState.showSettingsDialog) {
            xdman.ui.SettingsDialog(
                onDismiss = { appState.showSettingsDialog = false },
                onDarkModeChange = { appState.darkMode = it }
            )
        }
        if (appState.showAboutDialog) {
            xdman.ui.AboutDialog(
                onDismiss = { appState.showAboutDialog = false }
            )
        }
        if (appState.showYTDownloadDialog) {
            xdman.ui.YTDownloadDialog(
                onCombinedCreated = { appState.registerCombined(it) },
                onDismiss = { appState.showYTDownloadDialog = false }
            )
        }
        if (appState.showYTPlaylistDialog) {
            xdman.ui.YTPlaylistDialog(
                onCombinedCreated = { list -> list.forEach { appState.registerCombined(it) } },
                onDismiss = { appState.showYTPlaylistDialog = false }
            )
        }
        if (appState.showYTSetupDialog) {
            xdman.ui.YTSetupDialog(
                onDismiss = { appState.showYTSetupDialog = false }
            )
        }
        if (appState.showExportDialog) {
            xdman.ui.ExportDialog(
                appState = appState,
                onDismiss = { appState.showExportDialog = false }
            )
        }
        if (appState.showImportDialog) {
            xdman.ui.ImportDialog(
                appState = appState,
                onDismiss = { appState.showImportDialog = false }
            )
        }
        if (appState.showShortcutsDialog) {
            xdman.ui.ShortcutsDialog(
                onDismiss = { appState.showShortcutsDialog = false }
            )
        }
        appState.propertiesDialogId?.let { id ->
            xdman.ui.PropertiesDialog(
                id = id,
                onDismiss = { appState.propertiesDialogId = null }
            )
        }
        appState.refreshLinkId?.let { id ->
            xdman.ui.RefreshLinkDialog(
                id = id,
                onDismiss = { appState.refreshLinkId = null }
            )
        }
        appState.convertDialogId?.let { id ->
            xdman.ui.ConvertDialog(
                id = id,
                onDismiss = { appState.convertDialogId = null }
            )
        }
        appState.tagPickerDownloadId?.let { id ->
            xdman.ui.TagPickerDialog(
                appState = appState,
                id = id,
                onDismiss = { appState.tagPickerDownloadId = null }
            )
        }
        if (appState.showBatchTagDialog) {
            val ids = appState.batchTagIds
            if (ids.isNotEmpty()) {
                xdman.ui.BatchTagDialog(
                    appState = appState,
                    ids = ids,
                    onDismiss = { appState.showBatchTagDialog = false }
                )
            }
        }

        // Progress windows
        for (id in appState.activeProgressWindows) {
            DownloadProgressWindow(id = id, appState = appState)
        }
    }
}

private fun formatDuration(millis: Long): String {
    val secs = millis / 1000
    val m = (secs / 60) % 60
    val s = secs % 60
    val h = secs / 3600
    return if (h > 0) "%d:%02d:%02d".format(h, m, s)
    else "%d:%02d".format(m, s)
}
