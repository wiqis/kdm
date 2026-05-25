package xdman

import xdman.downloaders.Downloader
import xdman.downloaders.SegmentDetails
import xdman.downloaders.dash.DashDownloader
import xdman.downloaders.ftp.FtpDownloader
import xdman.downloaders.hds.HdsDownloader
import xdman.downloaders.hls.HlsDownloader
import xdman.downloaders.http.HttpDownloader
import xdman.downloaders.metadata.*
import xdman.network.http.HttpContext
import xdman.util.*
import java.io.*
import java.net.PasswordAuthentication
import java.nio.charset.Charset
import java.nio.file.Paths
import java.text.SimpleDateFormat
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

object XDMApp : DownloadListener, Comparator<String> {
    @JvmStatic
    fun getInstance(): XDMApp = this

    const val GLOBAL_LOCK_FILE = ".xdm-global-lock"
    const val APP_VERSION = "7.2.11"
    const val XDM_WINDOW_TITLE = "KDM 2026"
    const val APP_UPDAT_URL = "https://api.github.com/repos/wiqis/kdm/releases/latest"
    const val APP_UPDATE_CHK_URL = "https://subhra74.github.io/xdm/update-checker.html?v="
    const val APP_WIKI_URL = "https://github.com/wiqis/kdm/wiki"
    const val APP_HOME_URL = "https://github.com/wiqis/kdm"
    const val APP_TWITTER_URL = "https://twitter.com/XDM_subhra74"
    const val APP_FACEBOOK_URL = "https://www.facebook.com/XDM.subhra74/"
    @JvmField val ZOOM_LEVEL_STRINGS = arrayOf("Default", "50%", "75%", "100%", "125%", "150%", "200%", "250%", "300%", "350%", "400%", "450%", "500%")
    @JvmField val ZOOM_LEVEL_VALUES = doubleArrayOf(-1.0, 0.5, 0.75, 1.0, 1.25, 1.5, 2.0, 2.5, 3.0, 3.5, 4.0, 4.5, 5.0)

    private val listChangeListeners = ArrayList<ListChangeListener>()
    private val downloads = HashMap<String, DownloadEntry>()
    private val downloaders = HashMap<String, Downloader>()
    private var lastSaved = 0L
    private var qMgr: QueueManager = QueueManager.getInstance()
    private var refreshCallback: LinkRefreshCallback? = null
    private val pendingDownloads = ArrayList<String>()
    private var paramMap = HashMap<String, String>()

    var onNewDownloadRequest: ((HttpMetadata?, String?, String?) -> Unit)? = null
    var onBatchDownloadRequest: ((List<HttpMetadata>) -> Unit)? = null
    var onVideoDownloadRequest: ((HttpMetadata, String?) -> Unit)? = null
    var onAddToVideoList: ((HttpMetadata, String?, String?) -> Unit)? = null
    var onUpdateYoutubeTitle: ((String, String) -> Unit)? = null
    var onPromptCredential: ((String, String, Boolean) -> Boolean)? = null
    var onPromptFFmpegInstall: (() -> Unit)? = null
    var onShowDownloadComplete: ((String, String) -> Unit)? = null
    var onProgressUpdate: ((String, Long, Long, Int, Long, SegmentDetails?) -> Unit)? = null

    @JvmStatic
    fun start(args: Array<String>) {
        paramMap = HashMap()
        var expect = false
        var key: String? = null
        for (i in args.indices) {
            if (expect) {
                if (key != null) paramMap[key] = args[i]
                expect = false
                continue
            }
            when (args[i]) {
                "-u" -> { key = "url"; expect = true }
                "-m" -> { paramMap["background"] = "true" }
                "-i" -> { paramMap["installer"] = "true" }
                "-s" -> { key = "screen"; expect = true }
                "-o", "--output" -> { key = "output"; expect = true }
                "-q", "--quiet" -> { paramMap["quiet"] = "true" }
            }
        }
        instanceStarted()
    }

    fun showMainWindow() {
        // Signal the Compose window. Main.kt already shows the window.
    }

    init {
        Logger.log("Init app")
        Config.getInstance().setAutoShutdown(false)
        loadDownloadList()
        lastSaved = System.currentTimeMillis()
        qMgr = QueueManager.getInstance()
        qMgr.fixCorruptEntries(downloads.keys.iterator(), this)
        QueueScheduler.getInstance().start()
        HttpContext.getInstance().init()
        if (Config.getInstance().isMonitorClipboard()) {
            ClipboardMonitor.getInstance().startMonitoring()
        }
    }

    fun exit() {
        saveDownloadList()
        qMgr.saveQueues()
        Config.getInstance().save()
        System.exit(0)
    }

    fun instanceStarted() {
        Logger.log("instance starting...")
        if (!paramMap.containsKey("background")) {
            Logger.log("showing main window.")
            showMainWindow()
        }
        if (Config.getInstance().isFirstRun()) {
            if (XDMUtils.detectOS() != XDMUtils.WINDOWS) {
                XDMUtils.addToStartup()
            }
        }
        Logger.log("instance started.")
    }

    fun instanceAlreadyRunning() {
        Logger.log("instance already running")
        ParamUtils.sendParam(paramMap)
        System.exit(0)
    }

    override fun downloadFinished(id: String) {
        val ent = downloads[id] ?: return
        ent.state = XDMConstants.FINISHED
        val d = downloaders.remove(id)
        if (d != null && d.size < 0) {
            ent.size = d.downloaded
        }
        if (ent.isStartedByUser && Config.getInstance().showDownloadCompleteWindow()) {
            onShowDownloadComplete?.invoke(ent.file, getFolder(ent))
        }
        onProgressUpdate?.invoke(id, ent.size, ent.size, 100, 0, null)
        notifyListeners(null)
        saveDownloadList()
        if (Config.getInstance().isExecAntivir()) {
            if (!StringUtils.isNullOrEmptyOrBlank(Config.getInstance().antivirExe)) {
                execAntivir()
            }
        }
        processNextItem(id)
        if (isAllFinished()) {
            if (Config.getInstance().isAutoShutdown()) initShutdown()
            if (Config.getInstance().isExecCmd()) execCmd()
        }
    }

    override fun downloadFailed(id: String) {
        val d = downloaders.remove(id)
        val segDet = d?.getSegmentDetails()
        val ent = downloads[id]
        ent?.state = XDMConstants.PAUSED
        onProgressUpdate?.invoke(id, ent?.downloaded ?: 0, ent?.size ?: 0, 0, 0, segDet)
        notifyListeners(id)
        saveDownloadList()
        Logger.log("removed")
        processNextItem(id)
    }

    override fun downloadStopped(id: String) {
        val d = downloaders.remove(id)
        val segDet = d?.getSegmentDetails()
        val ent = downloads[id]
        ent?.state = XDMConstants.PAUSED
        onProgressUpdate?.invoke(id, ent?.downloaded ?: 0, ent?.size ?: 0, ent?.progress ?: 0, 0, segDet)
        notifyListeners(id)
        saveDownloadList()
        processNextItem(id)
    }

    override fun downloadConfirmed(id: String) {
        Logger.log("confirmed $id")
        val d = downloaders[id]
        val ent = downloads[id]
        if (d != null && ent != null) {
            ent.size = d.size
            if (d.isFileNameChanged) {
                ent.file = d.newFile
                ent.category = XDMUtils.findCategory(d.newFile)
                updateFileName(ent)
            }
            notifyListeners(id)
            saveDownloadList()
        }
    }

    override fun downloadUpdated(id: String) {
        try {
            val ent = downloads[id]
            val d = downloaders[id]
            if (d == null) {
                Logger.log("################# sync error ##############")
                return
            }
            if (ent != null) {
                ent.size = d.size
                ent.downloaded = d.downloaded
                ent.progress = d.progress
                ent.state = if (d.isAssembling) XDMConstants.ASSEMBLING else XDMConstants.DOWNLOADING
            }
            onProgressUpdate?.invoke(id, ent?.downloaded ?: 0, ent?.size ?: 0, ent?.progress ?: 0, d.downloadSpeed.toLong(), d.getSegmentDetails())
        } finally {
            notifyListeners(id)
            val now = System.currentTimeMillis()
            if (now - lastSaved > 5000) {
                saveDownloadList()
                lastSaved = now
            }
        }
    }

    fun addLinks(list: List<HttpMetadata>) {
        onBatchDownloadRequest?.invoke(list)
    }

    fun addDownload(metadata: HttpMetadata?, file: String?) {
        if (refreshCallback != null) {
            if (refreshCallback!!.isValidLink(metadata)) return
        }
        val fileName: String
        val folderPath: String?
        if (StringUtils.isNullOrEmptyOrBlank(file)) {
            fileName = if (metadata != null) XDMUtils.getFileName(metadata.url) else ""
            folderPath = null
        } else {
            val path = Paths.get(file!!)
            fileName = path.fileName.toString()
            val parentPath = path.parent
            folderPath = if (parentPath != null && parentPath.isAbsolute) {
                parentPath.toString()
            } else {
                val downloadFolderPath = if (Config.getInstance().isForceSingleFolder()) {
                    Config.getInstance().downloadFolder
                } else {
                    getFolder(XDMUtils.findCategory(file))
                }
                if (parentPath != null) Paths.get(downloadFolderPath, parentPath.toString()).toString()
                else downloadFolderPath
            }
        }
        if (metadata != null && (Config.getInstance().isQuietMode() || Config.getInstance().isDownloadAutoStart())) {
            createDownload(fileName, folderPath, metadata, true, "", 0, 0)
            return
        }
        onNewDownloadRequest?.invoke(metadata, fileName, folderPath)
    }

    fun addVideo(metadata: HttpMetadata, file: String?) {
        if (refreshCallback != null) {
            if (refreshCallback!!.isValidLink(metadata)) return
        }
        if (!XDMUtils.isFFmpegInstalled()) {
            onPromptFFmpegInstall?.invoke()
            return
        }
        onVideoDownloadRequest?.invoke(metadata, file)
    }

    fun addMedia(metadata: HttpMetadata, file: String?, info: String?) {
        println("video notification: " + Config.getInstance().isShowVideoNotification())
        if (Config.getInstance().isShowVideoNotification()) {
            onAddToVideoList?.invoke(metadata, file, info)
        }
    }

    fun youtubeVideoTitleUpdated(url: String, title: String) {
        if (Config.getInstance().isShowVideoNotification()) {
            onUpdateYoutubeTitle?.invoke(url, title)
        }
    }

    fun createDownload(file: String?, folder: String?, metadata: HttpMetadata, now: Boolean, queueId: String, formatIndex: Int, streamIndex: Int, category: Int = -1) {
        metadata.save()
        val ent = DownloadEntry()
        ent.id = metadata.id
        ent.outputFormatIndex = formatIndex
        ent.state = XDMConstants.PAUSED
        ent.file = file
        ent.folder = folder
        ent.tempFolder = Config.getInstance().temporaryFolder
        ent.category = if (category >= 0) category else XDMUtils.findCategory(file)
        ent.date = System.currentTimeMillis()
        putInQueue(queueId, ent)
        ent.isStartedByUser = now
        downloads[metadata.id] = ent
        saveDownloadList()
        if (!now) {
            val q = qMgr.getQueueById(queueId)
            if (q != null && q.isRunning) {
                Logger.log("Queue is running, if no pending download pickup next available download")
                q.next()
            }
        }
        if (now) {
            startDownload(metadata.id, metadata, ent, streamIndex)
        }
        notifyListeners(null)
    }

    private fun startDownload(id: String, metadata: HttpMetadata, ent: DownloadEntry, streams: Int) {
        if (!checkAndBufferRequests(id)) {
            Logger.log("starting $id with: $metadata is dash: ${metadata is DashMetadata}")
            var d: Downloader? = null
            when (metadata) {
                is DashMetadata -> {
                    Logger.log("Dash download with stream: $streams")
                    when (streams) {
                        1 -> {
                            val dm = metadata
                            dm.url = dm.url2!!
                            dm.url2 = null
                        }
                        2 -> {
                            val dm = metadata
                            dm.url2 = null
                        }
                        else -> {
                            Logger.log("Dash download created")
                            val dm = metadata
                            d = DashDownloader(id, ent.tempFolder, dm)
                        }
                    }
                }
                is HlsMetadata -> {
                    Logger.log("Hls download created")
                    d = HlsDownloader(id, ent.tempFolder, metadata as HlsMetadata)
                }
                is HdsMetadata -> {
                    Logger.log("Hds download created")
                    d = HdsDownloader(id, ent.tempFolder, metadata as HdsMetadata)
                }
            }
            if (d == null) {
                d = if (metadata.type == XDMConstants.FTP) {
                    FtpDownloader(id, ent.tempFolder, metadata)
                } else {
                    HttpDownloader(id, ent.tempFolder, metadata)
                }
            }
            d!!.setOuputMediaFormat(ent.outputFormatIndex)
            downloaders[id] = d
            d.registerListener(this)
            ent.state = XDMConstants.DOWNLOADING
            d.start()
        } else {
            Logger.log("$id: Maximum download limit reached, queueing request")
        }
    }

    fun pauseDownload(id: String) {
        val d = downloaders[id]
        d?.stop()
        d?.unregisterListener()
    }

    fun resumeDownload(id: String, startedByUser: Boolean) {
        val ent = downloads[id] ?: return
        ent.isStartedByUser = startedByUser
        if (ent.state == XDMConstants.PAUSED || ent.state == XDMConstants.FAILED) {
            if (!checkAndBufferRequests(id)) {
                ent.state = XDMConstants.DOWNLOADING
                val metadata = HttpMetadata.load(id)
                var d: Downloader? = null
                when (metadata) {
                    is DashMetadata -> {
                        val dm = metadata
                        Logger.log("Dash download- url1: ${dm.url} url2: ${dm.url2}")
                        d = DashDownloader(id, ent.tempFolder, dm)
                    }
                    is HlsMetadata -> {
                        val hm = metadata
                        Logger.log("HLS download- url1: ${hm.url}")
                        d = HlsDownloader(id, ent.tempFolder, hm)
                    }
                    is HdsMetadata -> {
                        val hm = metadata
                        Logger.log("HLS download- url1: ${hm.url}")
                        d = HdsDownloader(id, ent.tempFolder, hm)
                    }
                }
                if (d == null) {
                    Logger.log("normal download")
                    d = if (metadata?.type == XDMConstants.FTP) {
                        FtpDownloader(id, ent.tempFolder, metadata!!)
                    } else {
                        HttpDownloader(id, ent.tempFolder, metadata!!)
                    }
                }
                downloaders[id] = d
                d!!.setOuputMediaFormat(ent.outputFormatIndex)
                d.registerListener(this)
                d.resume()
            } else {
                Logger.log("$id: Maximum download limit reached, queueing request")
            }
            notifyListeners(null)
        }
    }

    fun restartDownload(id: String) {
        val ent = downloads[id] ?: return
        if (ent.state == XDMConstants.PAUSED || ent.state == XDMConstants.FAILED || ent.state == XDMConstants.FINISHED) {
            ent.state = XDMConstants.PAUSED
            clearData(ent)
            resumeDownload(id, true)
        }
    }

    fun addListener(listener: ListChangeListener) {
        synchronized(this) { listChangeListeners.add(listener) }
    }

    fun removeListener(listener: ListChangeListener) {
        synchronized(this) { listChangeListeners.remove(listener) }
    }

    private fun notifyListeners(id: String?) {
        if (id != null) {
            for (i in listChangeListeners.indices) listChangeListeners[i].listItemUpdated(id)
        } else {
            for (i in listChangeListeners.indices) listChangeListeners[i].listChanged()
        }
    }

    fun getEntry(id: String): DownloadEntry? = downloads[id]

    fun getDownloadList(category: Int, state: Int, searchText: String?, queueId: String?): List<String> {
        val idList = ArrayList<String>()
        for ((key, ent) in downloads) {
            if (state == XDMConstants.ALL || state == (if (ent.state == XDMConstants.FINISHED) XDMConstants.FINISHED else XDMConstants.UNFINISHED)) {
                if (category == XDMConstants.ALL || category == ent.category) {
                    if (queueId != null && queueId != "ALL" && queueId != ent.queueId) continue
                    if (!searchText.isNullOrEmpty() && !ent.file.contains(searchText)) continue
                    idList.add(key)
                }
            }
        }
        return idList
    }

    private fun clearData(ent: DownloadEntry) {
        if (ent == null) return
        val folder = File(ent.tempFolder, ent.id)
        folder.listFiles()?.forEach { it.delete() }
        folder.delete()
    }

    override fun getOutputFolder(id: String): String {
        val ent = downloads[id]
        return if (ent == null) Config.getInstance().categoryOther else getFolder(ent)
    }

    override fun getOutputFile(id: String, update: Boolean): String {
        val ent = downloads[id] ?: return ""
        if (update) updateFileName(ent)
        return ent.file
    }

    fun getFolder(ent: DownloadEntry): String {
        if (ent.folder != null) return ent.folder
        if (Config.getInstance().isForceSingleFolder()) return Config.getInstance().downloadFolder
        return getFolder(ent.category)
    }

    fun getFolder(category: Int): String = when (category) {
        XDMConstants.DOCUMENTS -> Config.getInstance().categoryDocuments
        XDMConstants.MUSIC -> Config.getInstance().categoryMusic
        XDMConstants.VIDEO -> Config.getInstance().categoryVideos
        XDMConstants.PROGRAMS -> Config.getInstance().categoryPrograms
        XDMConstants.COMPRESSED -> Config.getInstance().categoryCompressed
        else -> Config.getInstance().categoryOther
    }

    private fun loadDownloadList() {
        val file = File(Config.getInstance().dataFolder, "downloads.txt")
        loadDownloadList(file)
    }

    fun loadDownloadList(file: File) {
        if (!file.exists()) return
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        try {
            BufferedReader(InputStreamReader(FileInputStream(file), Charset.forName("UTF-8"))).use { reader ->
                val line = reader.readLine() ?: throw NullPointerException("Unexpected EOF")
                val count = line.trim().toInt()
                for (i in 0 until count) {
                    val fieldCount = XDMUtils.readLineSafe(reader).trim().toInt()
                    val ent = DownloadEntry()
                    for (j in 0 until fieldCount) {
                        val ln = reader.readLine() ?: return
                        val index = ln.indexOf(":")
                        if (index > 0) {
                            val key = ln.substring(0, index).trim()
                            val value = ln.substring(index + 1).trim()
                            when (key) {
                                "id" -> ent.id = value
                                "file" -> ent.file = value
                                "category" -> ent.category = value.toInt()
                                "state" -> {
                                    val state = value.toInt()
                                    ent.state = if (state == XDMConstants.FINISHED) state else XDMConstants.PAUSED
                                }
                                "folder" -> ent.folder = value
                                "date" -> ent.date = dateFormat.parse(value).time
                                "downloaded" -> ent.downloaded = value.toLong()
                                "size" -> ent.size = value.toLong()
                                "progress" -> ent.progress = value.toInt()
                                "queueid" -> ent.queueId = value
                                "formatIndex" -> ent.outputFormatIndex = value.toInt()
                                "tempfolder" -> ent.tempFolder = value
                            }
                        }
                    }
                    downloads[ent.id] = ent
                }
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    private fun saveDownloadList() {
        val file = File(Config.getInstance().dataFolder, "downloads.txt")
        saveDownloadList(file)
    }

    fun saveDownloadList(file: File) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val newLine = System.getProperty("line.separator")
        try {
            BufferedWriter(OutputStreamWriter(FileOutputStream(file), Charset.forName("UTF-8"))).use { writer ->
                writer.write(downloads.size.toString())
                writer.newLine()
                for ((_, ent) in downloads) {
                    val sb = StringBuilder()
                    var c = 0
                    sb.append("id: ${ent.id}$newLine"); c++
                    sb.append("file: ${ent.file}$newLine"); c++
                    sb.append("category: ${ent.category}$newLine"); c++
                    sb.append("state: ${ent.state}$newLine"); c++
                    if (ent.folder != null) { sb.append("folder: ${ent.folder}$newLine"); c++ }
                    sb.append("date: ${dateFormat.format(Date(ent.date))}$newLine"); c++
                    sb.append("downloaded: ${ent.downloaded}$newLine"); c++
                    sb.append("size: ${ent.size}$newLine"); c++
                    sb.append("progress: ${ent.progress}$newLine"); c++
                    if (ent.tempFolder != null) { sb.append("tempfolder: ${ent.tempFolder}$newLine"); c++ }
                    if (ent.queueId != null) { sb.append("queueid: ${ent.queueId}$newLine"); c++ }
                    sb.append("formatIndex: ${ent.outputFormatIndex}$newLine"); c++
                    writer.write("$c$newLine")
                    writer.write(sb.toString())
                }
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    fun hidePrgWnd(id: String) {
        // UI handles hiding progress window
    }

    private fun getActiveDownloadCount(): Int {
        var count = 0
        for ((_, ent) in downloads) {
            if (ent.state == XDMConstants.FINISHED || ent.state == XDMConstants.PAUSED || ent.state == XDMConstants.FAILED) continue
            count++
        }
        return count
    }

    private fun checkAndBufferRequests(id: String): Boolean {
        val actCount = getActiveDownloadCount()
        if (Config.getInstance().maxDownloads > 0 && actCount >= Config.getInstance().maxDownloads) {
            Logger.log("active: $actCount max: ${Config.getInstance().maxDownloads}")
            if (!pendingDownloads.contains(id)) pendingDownloads.add(id)
            return true
        }
        return false
    }

    private fun processNextItem(lastId: String?) {
        processPendingRequests()
        if (lastId == null) return
        val ent = getEntry(lastId) ?: return
        val queue = if ("".equals(ent.queueId)) qMgr.defaultQueue else qMgr.getQueueById(ent.queueId)
        if (queue != null && queue.isRunning) queue.next()
    }

    private fun processPendingRequests() {
        val activeCount = getActiveDownloadCount()
        val maxDownloadCount = Config.getInstance().maxDownloads
        val tobeStartedIds = ArrayList<String>()
        if (maxDownloadCount - activeCount > 0) {
            for (i in 0 until minOf(maxDownloadCount, pendingDownloads.size)) {
                tobeStartedIds.add(pendingDownloads[i])
            }
        }
        if (tobeStartedIds.isNotEmpty()) {
            for (id in tobeStartedIds) {
                pendingDownloads.remove(id)
                val ent = getEntry(id)
                if (ent != null) resumeDownload(id, ent.isStartedByUser)
            }
        }
    }

    fun queueItemPending(queueId: String?): Boolean {
        if (queueId == null) return false
        for (id in pendingDownloads) {
            val ent = getEntry(id)
            if (ent == null || ent.queueId == null) continue
            if (ent.queueId == queueId) return true
        }
        return false
    }

    fun getQueueList(): ArrayList<DownloadQueue> = qMgr.queueList
    fun getQueueById(queueId: String?): DownloadQueue? = qMgr.getQueueById(queueId)

    private fun putInQueue(queueId: String?, ent: DownloadEntry) {
        val q = getQueueById(queueId)
        val id = ent.id
        if (q == null) {
            Logger.log("No queue found for: '$queueId'")
            return
        }
        val qid = ent.queueId
        Logger.log("Adding to: '$queueId'")
        if (q.queueId != qid) {
            val oldQ = getQueueById(qid)
            if (oldQ != null) oldQ.removeFromQueue(id)
            ent.queueId = queueId
            q.addToQueue(id)
        }
    }

    override fun compare(key1: String, key2: String): Int {
        val ent1 = getEntry(key1) ?: return -1
        val ent2 = getEntry(key2) ?: return 1
        return when {
            ent1.date > ent2.date -> 1
            ent1.date < ent2.date -> -1
            else -> 0
        }
    }

    fun isAllFinished(): Boolean {
        if (getActiveDownloadCount() != 0) return false
        if (pendingDownloads.size != 0) return false
        for (i in QueueManager.getInstance().queueList.indices) {
            val q = QueueManager.getInstance().queueList[i]
            if (q.hasPendingItems()) return false
        }
        return true
    }

    private fun initShutdown() {
        Logger.log("Initiating shutdown")
        when (XDMUtils.detectOS()) {
            XDMUtils.LINUX -> LinuxUtils.initShutdown()
            XDMUtils.WINDOWS -> WinUtils.initShutdown()
            XDMUtils.MAC -> MacUtils.initShutdown()
        }
    }

    fun deleteDownloads(ids: List<String>, outflie: Boolean): Int {
        var c = 0
        for (id in ids) {
            val ent = getEntry(id) ?: continue
            if (ent.state == XDMConstants.FINISHED || ent.state == XDMConstants.PAUSED || ent.state == XDMConstants.FAILED) {
                downloads.remove(id)
                pendingDownloads.remove(id)
                val qId = ent.queueId
                if (qId != null) {
                    val q = getQueueById(qId)
                    if (q != null && q.queueId.isNotEmpty()) q.removeFromQueue(id)
                }
                deleteFiles(ent, outflie)
                c++
            }
        }
        saveDownloadList()
        notifyListeners(null)
        return ids.size - c
    }

    private fun deleteFiles(ent: DownloadEntry?, outfile: Boolean) {
        if (ent == null) return
        val id = ent.id
        Logger.log("Deleting metadata for $id")
        File(Config.getInstance().metadataFolder, id).delete()
        val df = File(ent.tempFolder, id)
        df.listFiles()?.forEach { it.delete() }
        df.delete()
        if (outfile) {
            File(getFolder(ent), ent.file).delete()
        }
    }

    fun getURL(id: String): String {
        return try {
            val metadata = HttpMetadata.load(id)
            if (metadata is DashMetadata) {
                "${metadata.url}\n${metadata.url2}"
            } else {
                metadata?.url ?: ""
            }
        } catch (e: Exception) {
            Logger.log(e)
            ""
        }
    }

    fun registerRefreshCallback(callback: LinkRefreshCallback?) { refreshCallback = callback }
    fun unregisterRefreshCallback() { refreshCallback = null }

    fun deleteCompleted() {
        val allKeys = downloads.keys.toList()
        val idList = ArrayList<String>()
        for (id in allKeys) {
            val ent = downloads[id]
            if (ent?.state == XDMConstants.FINISHED) idList.add(id)
        }
        deleteDownloads(idList, false)
    }

    fun promptCredential(id: String, msg: String, proxy: Boolean): Boolean {
        val ent = getEntry(id) ?: return false
        if (!ent.isStartedByUser) return false
        val callback = onPromptCredential ?: return false
        return callback(id, msg, proxy)
    }

    fun getCredential(msg: String, proxy: Boolean): PasswordAuthentication? {
        return null // Compose UI handles credentials via promptCredential callback
    }

    private fun execCmd() {
        if (!StringUtils.isNullOrEmptyOrBlank(Config.getInstance().customCmd)) {
            XDMUtils.exec(Config.getInstance().customCmd)
        }
    }

    private fun execAntivir() {
        XDMUtils.exec("${Config.getInstance().antivirExe} ${Config.getInstance().antivirCmd ?: ""}")
    }

    private fun updateFileName(ent: DownloadEntry) {
        if (Config.getInstance().duplicateAction == XDMConstants.DUP_ACT_OVERWRITE) return
        Logger.log("checking for same named file on disk...")
        val id = ent.id
        val outputFolder = getOutputFolder(id)
        var f = File(outputFolder, ent.file)
        var c = 1
        val ext = XDMUtils.getExtension(f.absolutePath) ?: ""
        val f2 = XDMUtils.getFileNameWithoutExtension(ent.file)
        while (f.exists()) {
            f = File(outputFolder, "${f2}_$c$ext")
            c++
        }
        Logger.log("Updating file name- old: ${ent.file} new: ${f.name}")
        ent.file = f.name
    }

    fun importList(file: File) { loadDownloadList(file) }
    fun exportList(file: File) { saveDownloadList(file) }

    private var pendingNotification = -1

    fun notifyComponentUpdate() { pendingNotification = UpdateChecker.COMP_UPDATE_AVAILABLE }
    fun notifyComponentInstall() { pendingNotification = UpdateChecker.COMP_NOT_INSTALLED }
    fun notifyAppUpdate() { pendingNotification = UpdateChecker.APP_UPDATE_AVAILABLE }
    fun clearNotifications() { pendingNotification = -1 }
    fun getNotification(): Int = pendingNotification

    fun openPreview(id: String) {
        val ent = getEntry(id)
        if (ent != null && (ent.category == XDMConstants.VIDEO || ent.category == XDMConstants.MUSIC)) {
            if (XDMUtils.isFFmpegInstalled()) {
                openPreviewPlayer(id)
            }
        } else {
            openTempFolder(id)
        }
    }

    private fun openPreviewPlayer(id: String) {
        XDMUtils.browseURL("http://127.0.0.1:9614/preview/media/$id")
    }

    private fun openTempFolder(id: String) {
        val ent = getEntry(id) ?: return
        val df = File(ent.tempFolder, id)
        try { XDMUtils.openFolder(null, df.absolutePath) }
        catch (e: Exception) { Logger.log(e) }
    }

    fun showPrgWnd(id: String) {
        val ent = getEntry(id) ?: return
        if (ent.state == XDMConstants.FINISHED || ent.state == XDMConstants.PAUSED || ent.state == XDMConstants.FAILED) return
        val d = downloaders[id]
        onProgressUpdate?.invoke(id, ent.downloaded, ent.size, ent.progress, 0, d?.getSegmentDetails())
    }

    fun fileNameChanged(id: String) { notifyListeners(id) }

    fun getDownloads(): Map<String, DownloadEntry> = downloads
    fun getSegmentDetails(id: String): SegmentDetails? = downloaders[id]?.getSegmentDetails()
}
