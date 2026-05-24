package xdman

import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.*
import xdman.downloaders.metadata.HttpMetadata
import xdman.ui.DownloadProgressWindow
import xdman.ui.MainWindowUI
import xdman.util.Logger
import xdman.util.XDMUtils
import java.awt.Dimension

data class ProgressInfo(
    var downloaded: Long = 0,
    var size: Long = 0,
    var progress: Int = 0,
    var speed: Long = 0,
    var eta: String = "",
    var elapsed: Long = 0L
)

class XDMAppUIState {
    var downloadIds by mutableStateOf(listOf<String>())
    var categoryFilter by mutableStateOf(XDMConstants.ALL)
    var stateFilter by mutableStateOf(XDMConstants.ALL)
    var searchText by mutableStateOf("")
    var sortField by mutableStateOf(0)
    var sortAsc by mutableStateOf(true)
    var queueIdFilter by mutableStateOf<String?>(null)
    var selectedIds by mutableStateOf(setOf<String>())
    var notificationState by mutableStateOf(-1)

    // Dialog states
    var showNewDownloadDialog by mutableStateOf(false)
    var showSettingsDialog by mutableStateOf(false)
    var showAboutDialog by mutableStateOf(false)
    var newDownloadMetadata: HttpMetadata? by mutableStateOf(null)
    var newDownloadFileName by mutableStateOf("")
    var newDownloadFolder: String? by mutableStateOf(null)

    // Detail dialog states
    var propertiesDialogId: String? by mutableStateOf(null)
    var refreshLinkId: String? by mutableStateOf(null)
    var convertDialogId: String? by mutableStateOf(null)

    // Progress tracking
    var progressMap by mutableStateOf(mapOf<String, ProgressInfo>())
    var activeProgressWindows by mutableStateOf(setOf<String>())

    fun refresh() {
        downloadIds = XDMApp.getDownloadList(categoryFilter, stateFilter, searchText, queueIdFilter ?: "ALL")
    }

    fun getProgress(id: String): ProgressInfo = progressMap[id] ?: ProgressInfo()

    fun showProgress(id: String) {
        activeProgressWindows = activeProgressWindows + id
    }

    fun hideProgress(id: String) {
        activeProgressWindows = activeProgressWindows - id
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
    }

    // Listen for download list changes
    XDMApp.addListener(object : ListChangeListener {
        override fun listChanged() { appState.refresh() }
        override fun listItemUpdated(id: String) { appState.refresh() }
    })

    Window(
        onCloseRequest = {
            XDMApp.exit()
            exitApplication()
        },
        title = XDMApp.XDM_WINDOW_TITLE,
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
                },
                onStartDownload = { file, folder, metadata, now, queueId, fmtIdx, streamIdx ->
                    XDMApp.createDownload(file, folder, metadata, now, queueId, fmtIdx, streamIdx)
                    appState.showNewDownloadDialog = false
                    appState.newDownloadMetadata = null
                }
            )
        }
        if (appState.showSettingsDialog) {
            xdman.ui.SettingsDialog(
                onDismiss = { appState.showSettingsDialog = false }
            )
        }
        if (appState.showAboutDialog) {
            xdman.ui.AboutDialog(
                onDismiss = { appState.showAboutDialog = false }
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
