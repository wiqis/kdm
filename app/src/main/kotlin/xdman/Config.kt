package xdman

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.ArrayList
import java.util.Arrays

import xdman.MonitoringListener
import xdman.util.Logger
import xdman.util.StringUtils
import xdman.util.XDMUtils

class Config private constructor() {
    var isForceSingleFolder: Boolean = false
    var metadataFolder: String = ""
    var temporaryFolder: String = ""
    var downloadFolder: String = ""
    var dataFolder: String = ""
    var sortField: Int = 0
    var sortAsc: Boolean = false
    var categoryFilter: Int = 0
    var stateFilter: Int = 0
    var searchText: String? = null
    var maxSegments: Int = 0
    var minSegmentSize: Int = 0
    var speedLimit: Int = 0 // in kb/sec
    var isShowDownloadWindow: Boolean = false
    var isShowDownloadCompleteWindow: Boolean = false
    var maxDownloads: Int = 0
    var isAutoShutdown: Boolean = false
    var duplicateAction: Int = 0
    var isQuietMode: Boolean = false
    var blockedHosts: Array<String>? = null
    var vidUrls: Array<String>? = null
    var fileExts: Array<String>? = null
    var vidExts: Array<String>? = null
    var vidMime: Array<String>? = null
    var defaultFileTypes: Array<String>? = null
    var defaultVideoTypes: Array<String>? = null
    var networkTimeout: Int = 0
    var tcpWindowSize: Int = 0
    var proxyMode: Int = 0 // 0 no-proxy, 1 pac, 2 http, 3 socks
    var proxyPac: String? = ""
    var proxyHost: String? = ""
    var socksHost: String? = ""
    var proxyPort: Int = 0
    var socksPort: Int = 0
    var proxyUser: String? = ""
    var proxyPass: String? = ""
    var isShowVideoNotification: Boolean = false
    var minVidSize: Int = 0
    var isKeepAwake: Boolean = false
    var isExecCmd: Boolean = false
    var isExecAntivir: Boolean = false
    var isAutoStart: Boolean = false
    var customCmd: String? = null
    var antivirCmd: String? = null
    var antivirExe: String? = null
    var isFirstRun: Boolean = false
    var language: String = "en"
    var isMonitorClipboard: Boolean = false

    private var _categoryOther: String? = null
    var categoryOther: String
        get() = _categoryOther ?: downloadFolder
        set(value) { _categoryOther = value }

    private var _categoryDocuments: String? = null
    var categoryDocuments: String
        get() {
            if (_categoryDocuments == null) {
                val folder = File(downloadFolder, "Documents")
                folder.mkdirs()
                _categoryDocuments = folder.absolutePath
            }
            return _categoryDocuments!!
        }
        set(value) { _categoryDocuments = value }

    private var _categoryMusic: String? = null
    var categoryMusic: String
        get() {
            if (_categoryMusic == null) {
                val folder = File(downloadFolder, "Music")
                folder.mkdirs()
                _categoryMusic = folder.absolutePath
            }
            return _categoryMusic!!
        }
        set(value) { _categoryMusic = value }

    private var _categoryVideos: String? = null
    var categoryVideos: String
        get() {
            if (_categoryVideos == null) {
                val folder = File(downloadFolder, "Video")
                folder.mkdirs()
                _categoryVideos = folder.absolutePath
            }
            return _categoryVideos!!
        }
        set(value) { _categoryVideos = value }

    private var _categoryPrograms: String? = null
    var categoryPrograms: String
        get() {
            if (_categoryPrograms == null) {
                val folder = File(downloadFolder, "Programs")
                folder.mkdirs()
                _categoryPrograms = folder.absolutePath
            }
            return _categoryPrograms!!
        }
        set(value) { _categoryPrograms = value }

    private var _categoryCompressed: String? = null
    var categoryCompressed: String
        get() {
            if (_categoryCompressed == null) {
                val folder = File(downloadFolder, "Compressed")
                folder.mkdirs()
                _categoryCompressed = folder.absolutePath
            }
            return _categoryCompressed!!
        }
        set(value) { _categoryCompressed = value }

    var isDownloadAutoStart: Boolean = false
    var isFetchTs: Boolean = false
    var isNoTransparency: Boolean = false
    var isHideTray: Boolean = false
    var lastFolder: String? = null
    var queueIdFilter: String? = null
    private val listeners: MutableList<MonitoringListener> = ArrayList()
    var isShowVideoListOnlyInBrowser: Boolean = false
    var zoomLevelIndex: Int = 0
    var isDarkMode: Boolean = true
    var isAutoResumeFailed: Boolean = false
    var isMinimizeToTray: Boolean = true
    var isConfirmBeforeDelete: Boolean = true
    var isStartWithSystem: Boolean = false
    var showSpeedInTitle: Boolean = false

    fun save() {
        var fw: FileWriter? = null
        try {
            val file = File(System.getProperty("user.home"), ".xdman/config.txt")
            fw = FileWriter(file)

            val newLine = "\n"

            fw.write("downloadFolder:" + this.downloadFolder + newLine)
            fw.write("temporaryFolder:" + this.temporaryFolder + newLine)
            fw.write("parallelDownloads:" + this.maxDownloads + newLine)
            fw.write("maxSegments:" + this.maxSegments + newLine)
            fw.write("networkTimeout:" + this.networkTimeout + newLine)
            fw.write("tcpWindowSize2:" + this.tcpWindowSize + newLine)
            fw.write("minSegmentSize2:" + this.minSegmentSize + newLine)
            fw.write("minVidSize:" + this.minVidSize + newLine)
            fw.write("duplicateAction:" + this.duplicateAction + newLine)
            fw.write("speedLimit:" + this.speedLimit + newLine)
            fw.write("showDownloadWindow:" + this.isShowDownloadWindow + newLine)
            fw.write("showDownloadCompleteWindow:" + this.isShowDownloadCompleteWindow + newLine)
            fw.write("blockedHosts:" + XDMUtils.appendArray2Str(this.blockedHosts ?: emptyArray()) + newLine)
            fw.write("vidUrls:" + XDMUtils.appendArray2Str(this.vidUrls ?: emptyArray()) + newLine)
            fw.write("fileExts:" + XDMUtils.appendArray2Str(this.fileExts ?: emptyArray()) + newLine)
            fw.write("vidExts:" + XDMUtils.appendArray2Str(this.vidExts ?: emptyArray()) + newLine)

            fw.write("proxyMode:" + this.proxyMode + newLine)
            fw.write("proxyPac:" + this.proxyPac + newLine)
            fw.write("proxyHost:" + this.proxyHost + newLine)
            fw.write("proxyPort:" + this.proxyPort + newLine)
            fw.write("socksHost:" + this.socksHost + newLine)
            fw.write("socksPort:" + this.socksPort + newLine)
            fw.write("proxyUser:" + this.proxyUser + newLine)
            fw.write("proxyPass:" + this.proxyPass + newLine)
            fw.write("autoShutdown:" + this.isAutoShutdown + newLine)
            fw.write("keepAwake:" + this.isKeepAwake + newLine)
            fw.write("execCmd:" + this.isExecCmd + newLine)
            fw.write("execAntivir:" + this.isExecAntivir + newLine)
            fw.write("version:" + XDMApp.APP_VERSION + newLine)
            fw.write("autoStart:" + this.isAutoStart + newLine)
            fw.write("language:" + this.language + newLine)
            fw.write("downloadAutoStart:" + this.isDownloadAutoStart + newLine)
            if (!StringUtils.isNullOrEmptyOrBlank(this.antivirExe)) fw.write("antivirExe:" + this.antivirExe + newLine)
            if (!StringUtils.isNullOrEmptyOrBlank(this.antivirCmd)) fw.write("antivirCmd:" + this.antivirCmd + newLine)
            if (!StringUtils.isNullOrEmptyOrBlank(this.customCmd)) fw.write("customCmd:" + this.customCmd + newLine)
            fw.write("showVideoNotification:" + this.isShowVideoNotification + newLine)
            fw.write("monitorClipboard:" + this.isMonitorClipboard + newLine)

            if (!StringUtils.isNullOrEmptyOrBlank(this.categoryOther)) fw.write("categoryOther:" + this.categoryOther + newLine)
            if (!StringUtils.isNullOrEmptyOrBlank(this.categoryCompressed)) fw.write("categoryCompressed:" + this.categoryCompressed + newLine)
            if (!StringUtils.isNullOrEmptyOrBlank(this.categoryDocuments)) fw.write("categoryDocuments:" + this.categoryDocuments + newLine)
            if (!StringUtils.isNullOrEmptyOrBlank(this.categoryMusic)) fw.write("categoryMusic:" + this.categoryMusic + newLine)
            if (!StringUtils.isNullOrEmptyOrBlank(this.categoryVideos)) fw.write("categoryVideos:" + this.categoryVideos + newLine)
            if (!StringUtils.isNullOrEmptyOrBlank(this.categoryPrograms)) fw.write("categoryPrograms:" + this.categoryPrograms + newLine)
            fw.write("fetchTs:" + this.isFetchTs + newLine)
            fw.write("noTransparency:" + this.isNoTransparency + newLine)
            fw.write("forceSingleFolder:" + this.isForceSingleFolder + newLine)
            fw.write("hideTray:" + this.isHideTray + newLine)
            if (lastFolder != null) {
                fw.write("lastFolder:" + this.lastFolder + newLine)
            }
            fw.write("showVideoListOnlyInBrowser:" + this.isShowVideoListOnlyInBrowser + newLine)
            fw.write("zoomLevelIndex:" + this.zoomLevelIndex + newLine)
            fw.write("darkMode:" + this.isDarkMode + newLine)
            fw.write("autoResumeFailed:" + this.isAutoResumeFailed + newLine)
            fw.write("minimizeToTray:" + this.isMinimizeToTray + newLine)
            fw.write("confirmBeforeDelete:" + this.isConfirmBeforeDelete + newLine)
            fw.write("startWithSystem:" + this.isStartWithSystem + newLine)
            fw.write("showSpeedInTitle:" + this.showSpeedInTitle + newLine)

        } catch (e: Exception) {
        } finally {
            try {
                fw?.close()
            } catch (e: Exception) {
            }
        }
    }

    fun load() {
        Logger.log("Loading config...")
        var br: BufferedReader? = null
        try {
            val file = File(System.getProperty("user.home"), ".xdman/config.txt")
            if (!file.exists()) {
                return
            }
            val r = FileReader(file)
            br = BufferedReader(r)
            while (true) {
                val ln = br.readLine() ?: break
                if (ln.startsWith("#")) continue
                val index = ln.indexOf(":")
                if (index < 1) continue
                val key = ln.substring(0, index)
                val valStr = ln.substring(index + 1)
                when (key) {
                    "downloadFolder" -> this.downloadFolder = valStr
                    "temporaryFolder" -> this.temporaryFolder = valStr
                    "maxSegments" -> this.maxSegments = valStr.toInt()
                    "minSegmentSize2" -> this.minSegmentSize = valStr.toInt()
                    "networkTimeout" -> this.networkTimeout = valStr.toInt()
                    "tcpWindowSize2" -> this.tcpWindowSize = valStr.toInt()
                    "duplicateAction" -> this.duplicateAction = valStr.toInt()
                    "speedLimit" -> this.speedLimit = valStr.toInt()
                    "showDownloadWindow" -> this.isShowDownloadWindow = valStr == "true"
                    "showDownloadCompleteWindow" -> this.isShowDownloadCompleteWindow = valStr == "true"
                    "downloadAutoStart" -> this.isDownloadAutoStart = valStr == "true"
                    "minVidSize" -> this.minVidSize = valStr.toInt()
                    "parallelDownloads", "parallalDownloads" -> this.maxDownloads = valStr.toInt()
                    "blockedHosts" -> this.blockedHosts = valStr.split(",").toTypedArray()
                    "vidUrls" -> this.vidUrls = valStr.split(",").toTypedArray()
                    "fileExts" -> this.fileExts = valStr.split(",").toTypedArray()
                    "vidExts" -> this.vidExts = valStr.split(",").toTypedArray()
                    "proxyMode" -> this.proxyMode = valStr.toInt()
                    "proxyPort" -> this.proxyPort = valStr.toInt()
                    "socksPort" -> this.socksPort = valStr.toInt()
                    "proxyPac" -> this.proxyPac = valStr
                    "proxyHost" -> this.proxyHost = valStr
                    "socksHost" -> this.socksHost = valStr
                    "proxyUser" -> this.proxyUser = valStr
                    "proxyPass" -> this.proxyPass = valStr
                    "showVideoNotification" -> this.isShowVideoNotification = "true" == valStr
                    "keepAwake" -> this.isKeepAwake = "true" == valStr
                    "autoStart" -> this.isAutoStart = "true" == valStr
                    "execAntivir" -> this.isExecAntivir = "true" == valStr
                    "execCmd" -> this.isExecCmd = "true" == valStr
                    "antivirExe" -> this.antivirExe = valStr
                    "antivirCmd" -> this.antivirCmd = valStr
                    "customCmd" -> this.customCmd = valStr
                    "autoShutdown" -> this.isAutoShutdown = "true" == valStr
                    "version" -> this.isFirstRun = XDMApp.APP_VERSION != valStr
                    "language" -> this.language = valStr
                    "monitorClipboard" -> this.isMonitorClipboard = "true" == valStr
                    "categoryOther" -> this.categoryOther = valStr
                    "categoryDocuments" -> this.categoryDocuments = valStr
                    "categoryCompressed" -> this.categoryCompressed = valStr
                    "categoryMusic" -> this.categoryMusic = valStr
                    "categoryVideos" -> this.categoryVideos = valStr
                    "categoryPrograms" -> this.categoryPrograms = valStr
                    "fetchTs" -> this.isFetchTs = "true" == valStr
                    "noTransparency" -> this.isNoTransparency = "true" == valStr
                    "forceSingleFolder" -> this.isForceSingleFolder = "true" == valStr
                    "hideTray" -> this.isHideTray = "true" == valStr
                    "lastFolder" -> this.lastFolder = valStr
                    "showVideoListOnlyInBrowser" -> this.isShowVideoListOnlyInBrowser = "true" == valStr
                    "zoomLevelIndex" -> this.zoomLevelIndex = valStr.toInt()
                    "darkMode" -> this.isDarkMode = valStr == "true"
                    "autoResumeFailed" -> this.isAutoResumeFailed = valStr == "true"
                    "minimizeToTray" -> this.isMinimizeToTray = valStr == "true"
                    "confirmBeforeDelete" -> this.isConfirmBeforeDelete = valStr == "true"
                    "startWithSystem" -> this.isStartWithSystem = valStr == "true"
                    "showSpeedInTitle" -> this.showSpeedInTitle = valStr == "true"
                }
            }
        } catch (e: Exception) {
            Logger.log(e)
        } finally {
            if (!isForceSingleFolder) {
                createFolders()
            }
            try {
                br?.close()
            } catch (e: Exception) {
            }
        }
    }

    init {
        isForceSingleFolder = false
        var f = File(System.getProperty("user.home"), ".xdman")
        if (!f.exists()) {
            f.mkdirs()
        }
        dataFolder = f.absolutePath
        f = File(dataFolder, "metadata")
        if (!f.exists()) {
            f.mkdir()
        }
        this.metadataFolder = f.absolutePath
        f = File(dataFolder, "temp")
        if (!f.exists()) {
            f.mkdir()
        }

        this.temporaryFolder = f.absolutePath

        this.downloadFolder = XDMUtils.getDownloadsFolder()
        if (!File(this.downloadFolder).exists()) {
            val file = File(System.getProperty("user.home"), "Downloads")
            file.mkdirs()
            this.downloadFolder = file.absolutePath
        }

        this.isShowDownloadWindow = true
        this.maxSegments = 8
        this.minSegmentSize = 256 * 1024
        this.maxDownloads = 100
        this.minVidSize = 1 * 1024 * 1024
        this.defaultFileTypes = arrayOf("3GP", "7Z", "AVI", "BZ2", "DEB", "DOC", "DOCX", "EXE", "GZ", "ISO", "MSI", "PDF", "PPT", "PPTX", "RAR", "RPM", "XLS", "XLSX", "SIT", "SITX", "TAR", "JAR", "ZIP", "XZ")
        this.fileExts = defaultFileTypes
        this.isAutoShutdown = false
        this.blockedHosts = arrayOf("update.microsoft.com", "windowsupdate.com", "thwawte.com")
        this.defaultVideoTypes = arrayOf("MP4", "M3U8", "F4M", "WEBM", "OGG", "MP3", "AAC", "FLV", "MKV", "DIVX", "MOV", "MPG", "MPEG", "OPUS")
        this.vidExts = defaultVideoTypes
        this.vidUrls = arrayOf(".facebook.com|pagelet", "player.vimeo.com/", "instagram.com/p/")
        this.vidMime = arrayOf("video/", "audio/", "mpegurl", "f4m", "m3u8")

        this.networkTimeout = 60
        this.tcpWindowSize = 0
        this.speedLimit = 0

        this.proxyMode = 0
        this.proxyPort = 0
        this.socksPort = 0
        this.proxyPac = ""
        this.proxyHost = ""
        this.proxyUser = ""
        this.proxyPass = ""
        this.socksHost = ""
        this.isShowVideoNotification = true
        this.isShowDownloadCompleteWindow = true
        this.isFirstRun = true
        this.language = "en"
        this.isMonitorClipboard = false
        this.isNoTransparency = false
        this.isHideTray = true
    }

    fun createFolders() {
        Logger.log("Creating folders")
        categoryDocuments
        categoryMusic
        categoryCompressed
        categoryPrograms
        categoryVideos
    }

    fun isBrowserMonitoringEnabled(): Boolean = true
    fun enableMonitoring(enable: Boolean) {}
    fun addConfigListener(listener: MonitoringListener) {
        listeners.add(listener)
    }

    fun addBlockedHosts(host: String) {
        val list = ArrayList(Arrays.asList(*(blockedHosts ?: emptyArray())))
        if (list.contains(host)) {
            return
        }
        list.add(host)
        blockedHosts = list.toTypedArray()
    }

    companion object {
        private var _config: Config? = null

        @JvmStatic
        @Synchronized
        fun getInstance(): Config {
            if (_config == null) {
                _config = Config()
            }
            return _config!!
        }
    }
}
