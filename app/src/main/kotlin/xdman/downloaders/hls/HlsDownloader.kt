package xdman.downloaders.hls

import xdman.Config
import xdman.XDMConstants
import xdman.downloaders.AbstractChannel
import xdman.downloaders.Downloader
import xdman.downloaders.Segment
import xdman.downloaders.SegmentDetails
import xdman.downloaders.SegmentImpl
import xdman.downloaders.SegmentInfo
import xdman.downloaders.http.HttpChannel
import xdman.downloaders.metadata.HlsMetadata
import xdman.downloaders.metadata.HttpMetadata
import xdman.mediaconversion.FFmpeg
import xdman.mediaconversion.MediaConversionListener
import xdman.mediaconversion.MediaFormats
import xdman.util.FormatUtilities
import xdman.util.Logger
import xdman.util.StringUtils
import xdman.util.XDMUtils
import java.io.*
import java.util.*

class HlsDownloader(id: String, folder: String, private var hlsMetadata: HlsMetadata) : Downloader(), MediaConversionListener, HlsEncryptedSouce {
    private val items: ArrayList<HlsPlaylistItem> = ArrayList()
    private var manifestSegment: Segment? = null
    private var totalAssembled: Long = 0
    private var newFileName: String? = null
    private var assembleFinished = false
    private var lastProgress = 0
    private var totalDuration = 0f
    private var playlist: HlsPlaylist? = null
    private var keyMap: MutableMap<String, ByteArray>? = null

    init {
        this.id = id
        this.folder = File(folder, id).absolutePath
        this.length = -1
        this.MAX_COUNT = Config.getInstance().maxSegments
        chunks = Collections.synchronizedList(ArrayList())
        this.eta = "---"
    }

    override fun start() {
        Logger.log("creating folder $folder")
        File(folder).mkdirs()
        this.lastDownloaded = downloaded
        this.prevTime = System.currentTimeMillis()
        try {
            manifestSegment = SegmentImpl(this, folder)
            manifestSegment!!.tag = "MF"
            manifestSegment!!.length = -1
            manifestSegment!!.startOffset = 0
            manifestSegment!!.downloaded = 0
            manifestSegment!!.tag = "HLS"
            manifestSegment!!.download(this)
        } catch (e: IOException) {
            this.errorCode = XDMConstants.RESUME_FAILED
            this.listener!!.downloadFailed(id)
        }
    }

    override fun chunkInitiated(id: String) {
        if (id != manifestSegment!!.id) {
            Logger.log("Non manifest segment: $id manifest seg: ${manifestSegment!!.id}")
            processSegments()
        } else {
            isJavaClientRequired = (manifestSegment!!.channel as HttpChannel).javaClientRequired
            super.getLastModifiedDate(manifestSegment!!)
        }
    }

    override fun chunkComplete(id: String): Boolean {
        if (finished) return true
        if (stopFlag) return true

        if (id == manifestSegment!!.id) {
            Logger.log("Manifest segment complete: $id")
            if (initOrUpdateSegments()) {
                listener!!.downloadConfirmed(this.id)
                Logger.log("confirmed")
            } else {
                if (!stopFlag) {
                    this.errorCode = XDMConstants.ERR_INVALID_RESP
                    listener!!.downloadFailed(this.id)
                    return true
                }
            }
        } else {
            val s = getById(id) ?: return true
            if (s.length < 0) {
                s.length = s.downloaded
            }

            if (allFinished()) {
                saveState()
                finished = true
                updateStatus()
                try {
                    assemble()
                    if (!assembleFinished) {
                        throw IOException("Assemble not finished successfully")
                    }
                    Logger.log("********Download finished*********")
                    updateStatus()
                    listener!!.downloadFinished(this.id)
                } catch (e: Exception) {
                    if (!stopFlag) {
                        Logger.log(e)
                        this.errorCode = XDMConstants.ERR_ASM_FAILED
                        listener!!.downloadFailed(this.id)
                    }
                }
                listener = null
                return true
            }
        }
        val s = getById(id)
        clearChannel(s)
        processSegments()
        return true
    }

    override fun chunkUpdated(id: String) {
        if (manifestSegment != null && id == manifestSegment!!.id) {
            return
        }
        if (stopFlag) return
        val now = System.currentTimeMillis()
        if (now - lastSaved > 5000) {
            synchronized(this) { saveState() }
            lastSaved = now
        }
        if (now - lastUpdated > 1000) {
            updateStatus()
            lastUpdated = now
            synchronized(this) { processSegments() }
        }
    }

    override fun createChannel(segment: Segment): AbstractChannel {
        for (i in chunks.indices) {
            if (segment == chunks[i]) {
                val item = items[i]
                if (keyMap != null && item.keyUrl != null) {
                    Logger.log("Creating encrypted channel")
                    return EncryptedHlsChannel(segment, item.url!!, hlsMetadata.headers, -1, isJavaClientRequired, this, item.keyUrl)
                } else {
                    return HttpChannel(segment, item.url!!, hlsMetadata.headers, -1, isJavaClientRequired)
                }
            }
        }
        Logger.log("Create manifest channel")
        return HttpChannel(segment, hlsMetadata.url, hlsMetadata.headers, -1, isJavaClientRequired)
    }

    override fun shouldCleanup(): Boolean = assembleFinished

    private fun initOrUpdateSegments(): Boolean {
        return try {
            this.playlist = PlaylistParser.parse(File(folder, manifestSegment!!.id).absolutePath, hlsMetadata.url)

            if (this.playlist == null) {
                Logger.log("Manifest either invalid or have unsupported DRM")
                return false
            }
            this.totalDuration = playlist!!.duration
            Logger.log("Total duration")
            val pitems = playlist!!.items
            if (pitems == null) {
                Logger.log("Manifest either invalid or have unsupported DRM")
                return false
            }
            if (pitems.size < 1) {
                Logger.log("Manifest contains no media")
                return false
            }
            if (items.size > 0 && items.size != pitems.size) {
                Logger.log("Manifest media count mismatch- expected: ${items.size} got: ${pitems.size}")
                return false
            }
            if (items.isNotEmpty()) {
                items.clear()
            }

            if (playlist!!.isEncrypted) {
                keyMap = HashMap()
            }

            Logger.log("Pleylist items: ${pitems.size}")

            for (item in pitems) {
                Logger.log(item)
                val item2 = HlsPlaylistItem(item.url, item.keyUrl, item.IV, null, null, item.duration)
                this.items.add(item2)
            }

            var newExtension: String? = null
            Logger.log("Chunk size: ${chunks.size}")
            if (chunks.size < 1) {
                Logger.log("Creating chunk")
                for (i in items.indices) {
                    if (newExtension == null && outputFormat == 0) {
                        newExtension = findExtension(items[i].url)
                        if (newExtension != null) {
                            Logger.log("HLS: found new extension: $newExtension")
                            this.newFileName = getOutputFileName(true).replace(".ts", newExtension)
                        } else {
                            newExtension = ".ts"
                        }
                    }

                    val s2 = SegmentImpl(this, folder)
                    s2.tag = "HLS"
                    s2.length = -1
                    Logger.log("Adding chunk: $s2")
                    Logger.log("Adding")
                    chunks.add(s2)
                }
                Logger.log("Segments created")
            }
            true
        } catch (e: Exception) {
            Logger.log(e)
            false
        }
    }

    @Synchronized
    private fun processSegments() {
        Logger.log("HLS: process segment")
        val activeCount = activeChunkCount
        Logger.log("active: $activeCount")
        if (activeCount < MAX_COUNT) {
            val rem = MAX_COUNT - activeCount
            try {
                retryFailedChunks(rem)
            } catch (e: IOException) {
                Logger.log(e)
            }
        }
    }

    private fun updateStatus() {
        try {
            val now = System.currentTimeMillis()
            if (this.eta == null) {
                this.eta = "---"
            }
            if (converting) {
                progress = this.convertPrg
            } else if (assembling) {
                val len = if (length > 0) length else downloaded
                progress = ((totalAssembled * 100) / len).toInt()
            } else {
                var downloaded2 = 0L
                var processedSegments = 0
                var partPrg = 0
                downloadSpeed = 0f
                for (i in chunks.indices) {
                    val s = chunks[i]
                    downloaded2 += s.downloaded
                    downloadSpeed += s.transferRate
                    if (s.isFinished) {
                        processedSegments++
                    } else if (s.downloaded > 0 && s.length > 0) {
                        val prg2 = ((s.downloaded * 100) / s.length).toInt()
                        partPrg += prg2
                    }
                }
                this.downloaded = downloaded2
                if (chunks.size > 0) {
                    progress = ((processedSegments * 100) / chunks.size)
                    progress += (partPrg / chunks.size)
                    if (segDet == null) {
                        segDet = SegmentDetails()
                        if (segDet!!.capacity < chunks.size) {
                            segDet!!.extend(chunks.size - segDet!!.capacity)
                        }
                        segDet!!.chunkCount = chunks.size.toLong()
                    }
                    val info: SegmentInfo = segDet!!.chunkUpdates[0]
                    info.downloaded = progress.toLong()
                    info.length = 100
                    info.start = 0
                    val timeSpend = now - prevTime
                    if (timeSpend > 0) {
                        val prgDiff = progress - lastProgress
                        if (prgDiff > 0) {
                            val eta = (timeSpend * (100 - progress) / 1000 * prgDiff)
                            lastProgress = progress
                            this.eta = FormatUtilities.hms(eta.toInt())
                        }
                        prevTime = now
                        lastDownloaded = downloaded
                    }
                }
            }
            listener!!.downloadUpdated(id)
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    override fun stop() {
        stopFlag = true
        saveState()
        for (i in chunks.indices) {
            chunks[i].stop()
        }
        this.ffmpeg?.stop()
        listener!!.downloadStopped(id)
        listener = null
    }

    override fun resume() {
        try {
            stopFlag = false
            Logger.log("Resuming")
            if (!restoreState()) {
                Logger.log("Starting from beginning")
                start()
                return
            }
            Logger.log("Restore success")
            this.lastDownloaded = downloaded
            this.lastProgress = this.progress
            this.prevTime = System.currentTimeMillis()
            if (allFinished()) {
                assembleAsync()
            } else {
                Logger.log("Starting")
                start()
            }
        } catch (e: Exception) {
            Logger.log(e)
            this.errorCode = XDMConstants.RESUME_FAILED
            listener!!.downloadFailed(this.id)
        }
    }

    override val type: Int get() = XDMConstants.HLS
    override val isFileNameChanged: Boolean get() = newFileName != null
    override val newFile: String? get() = newFileName
    override val metadata: HttpMetadata? get() = this.hlsMetadata

    private fun saveState() {
        if (chunks.size < 1) return
        val sb = StringBuilder()
        sb.append("$length\n")
        sb.append("$downloaded\n")
        sb.append("${totalDuration.toLong()}\n")
        sb.append("${items.size}\n")
        Logger.log("url saved of size: ${items.size}")
        for (i in items.indices) {
            val url = items[i].url
            Logger.log("Saveing url: $url")
            sb.append("$url\n")
        }
        sb.append("${chunks.size}\n")
        for (i in chunks.indices) {
            val seg = chunks[i]
            sb.append("${seg.id}\n")
            if (seg.isFinished) {
                sb.append("${seg.length}\n")
                sb.append("${seg.startOffset}\n")
                sb.append("${seg.downloaded}\n")
            } else {
                sb.append("-1\n")
                sb.append("${seg.startOffset}\n")
                sb.append("${seg.downloaded}\n")
            }
        }
        if (!StringUtils.isNullOrEmptyOrBlank(lastModified)) {
            sb.append("$lastModified\n")
        }

        sb.append(keyMap != null)

        if (keyMap != null) {
            for (item in items) {
                val hasKey = !StringUtils.isNullOrEmptyOrBlank(item.keyUrl)
                sb.append(hasKey)
                if (hasKey) {
                    sb.append("${item.keyUrl}\n")
                }
                val hasIV = !StringUtils.isNullOrEmptyOrBlank(item.IV)
                sb.append(hasIV)
                if (hasIV) {
                    sb.append("${item.IV}\n")
                }
            }

            sb.append("${keyMap!!.size}\n")
            for ((key, value) in keyMap!!) {
                sb.append("$key\n")
                sb.append(Base64.getEncoder().encodeToString(value))
            }
        }

        try {
            val tmp = File(folder, "${System.currentTimeMillis()}.tmp")
            val out = File(folder, "state.txt")
            FileOutputStream(tmp).use { fs -> fs.write(sb.toString().toByteArray()) }
            out.delete()
            tmp.renameTo(out)
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    private fun restoreState(): Boolean {
        chunks = Collections.synchronizedList(ArrayList())
        var file = File(folder, "state.txt")
        if (!file.exists()) {
            file = getBackupFile(folder) ?: return false
        }
        try {
            BufferedReader(FileReader(file)).use { br ->
                this.length = br.readLine().toLong()
                this.downloaded = br.readLine().toLong()
                this.totalDuration = br.readLine().toLong().toFloat()
                val urlCount = br.readLine().toInt()
                Logger.log("Loading urls: $urlCount")
                for (i in 0 until urlCount) {
                    val url = XDMUtils.readLineSafe(br)
                    val item = HlsPlaylistItem()
                    item.url = url
                    items.add(item)
                    Logger.log("loading url: $url")
                }
                val chunkCount = br.readLine().toInt()
                for (i in 0 until chunkCount) {
                    val cid = XDMUtils.readLineSafe(br)
                    val len = br.readLine().toLong()
                    val off = br.readLine().toLong()
                    val dwn = br.readLine().toLong()
                    val seg = SegmentImpl(folder, cid, off, len, dwn)
                    seg.tag = "HLS"
                    Logger.log("id: ${seg.id}\nlength: ${seg.length}\noffset: ${seg.startOffset}\ndownload: ${seg.downloaded}")
                    chunks.add(seg)
                }
                this.lastModified = XDMUtils.readLineSafe(br)

                val strHasMoreInfo = br.readLine()
                if (strHasMoreInfo != null && strHasMoreInfo == "true") {
                    for (i in 0 until urlCount) {
                        val item = items[i]
                        if (br.readLine() == "true") {
                            item.keyUrl = XDMUtils.readLineSafe(br)
                        }
                        if (br.readLine() == "true") {
                            item.IV = XDMUtils.readLineSafe(br)
                        }
                    }

                    val keys = br.readLine().toInt()
                    for (i in 0 until keys) {
                        val keyUrl = XDMUtils.readLineSafe(br)
                        Logger.log("Keydata: $keyUrl")
                        val keyData = XDMUtils.readLineSafe(br)
                        val data = Base64.getDecoder().decode(keyData)
                        if (keyMap == null) {
                            keyMap = HashMap()
                        }
                        keyMap!![keyUrl] = data
                    }
                }
            }
            return true
        } catch (e: Exception) {
            Logger.log("Failed to load saved state")
            Logger.log(e)
        }
        return false
    }

    private fun assembleAsync() {
        Thread {
            finished = true
            try {
                assemble()
                if (!assembleFinished) throw IOException("Assemble not finished successfully")
                Logger.log("********Download finished*********")
                updateStatus()
                cleanup()
                listener!!.downloadFinished(id)
            } catch (e: Exception) {
                if (!stopFlag) {
                    Logger.log(e)
                    errorCode = XDMConstants.ERR_ASM_FAILED
                    listener!!.downloadFailed(id)
                }
            }
        }.start()
    }

    private fun findExtension(urlStr: String?): String? {
        var newExtension: String? = null
        val fileName = XDMUtils.getFileName(urlStr)
        if (!StringUtils.isNullOrEmptyOrBlank(fileName)) {
            val ext = XDMUtils.getExtension(fileName)
            if (!StringUtils.isNullOrEmptyOrBlank(ext) && ext!!.length > 1) {
                if (!ext.lowercase().contains("ts")) {
                    newExtension = ext.lowercase()
                    if (newExtension.contains("m4s")) {
                        Logger.log("HLS extension: MP4")
                        newExtension = ".mp4"
                    }
                    if (!newExtension.contains("mp4")) {
                        newExtension = ".mkv"
                    }
                }
            }
        }
        return newExtension
    }

    @Throws(IOException::class)
    private fun assemble() {
        var ffOutFile: File? = null
        XDMUtils.mkdirs(getOutputFolder())
        try {
            assembleFinished = false
            val sb = StringBuilder()
            for (s in chunks) {
                sb.append("file '${File(folder, s.id)}'\r\n")
            }

            val hlsFile = File(folder, "$id-hls.txt")

            try {
                FileOutputStream(hlsFile).use { hlsTextStream ->
                    hlsTextStream.write(sb.toString().toByteArray())
                }
            } catch (_: Exception) {
            }

            this.converting = true
            val inputFiles = ArrayList<String>()
            inputFiles.add(hlsFile.absolutePath)
            ffOutFile = File(getOutputFolder(), UUID.randomUUID().toString() + "_" + getOutputFileName(true))
            this.ffmpeg = FFmpeg(inputFiles, ffOutFile!!.absolutePath, this, MediaFormats.getSupportedFormats()!![outputFormat]!!, outputFormat == 0)
            ffmpeg!!.hls = true
            ffmpeg!!.setHLSDuration(totalDuration)
            val ret = ffmpeg!!.convert()
            Logger.log("FFmpeg exit code: $ret")

            if (ret != 0) {
                throw IOException("FFmpeg failed")
            } else {
                val len = ffOutFile!!.length()
                if (len > 0) {
                    this.length = len
                }
            }

            val realFile = File(getOutputFolder(), getOutputFileName(true))
            if (realFile.exists()) {
                realFile.delete()
            }
            ffOutFile!!.renameTo(realFile)

            assembleFinished = true
        } finally {
            if (!assembleFinished) {
                ffOutFile?.delete()
            }
        }
    }

    override fun progress(progress: Int) {
        this.convertPrg = progress
        val now = System.currentTimeMillis()
        if (now - lastUpdated > 1000) {
            updateStatus()
            lastUpdated = now
        }
    }

    override fun hasKey(keyUrl: String?): Boolean {
        return if (keyMap == null || keyUrl == null) false
        else keyMap!![keyUrl] != null
    }

    override fun setKey(keyUrl: String, data: ByteArray) {
        if (keyMap != null && !keyMap!!.containsKey(keyUrl)) {
            keyMap!![keyUrl] = data
        }
    }

    override fun getKey(keyUrl: String): ByteArray? {
        return keyMap?.get(keyUrl)
    }

    override fun getIV(url: String): String? {
        if (StringUtils.isNullOrEmptyOrBlank(url)) {
            return null
        }
        for (item in items) {
            if (url == item.url) {
                return item.IV
            }
        }
        return null
    }
}
