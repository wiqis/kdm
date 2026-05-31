package xdman.downloaders.hds

import xdman.Config
import xdman.XDMConstants
import xdman.downloaders.AbstractChannel
import xdman.downloaders.Downloader
import xdman.downloaders.Segment
import xdman.downloaders.SegmentDetails
import xdman.downloaders.SegmentImpl
import xdman.downloaders.SegmentInfo
import xdman.downloaders.http.HttpChannel
import xdman.downloaders.metadata.HdsMetadata
import xdman.downloaders.metadata.HttpMetadata
import xdman.downloaders.metadata.manifests.F4MManifest
import xdman.mediaconversion.FFmpeg
import xdman.mediaconversion.MediaConversionListener
import xdman.mediaconversion.MediaFormats
import xdman.util.FormatUtilities
import xdman.util.Logger
import xdman.util.StringUtils
import xdman.util.XDMUtils
import java.io.*
import java.util.*

class HdsDownloader(id: String, folder: String, private var hdsMetadata: HdsMetadata) : Downloader(), MediaConversionListener {
    private val urlList: ArrayList<String> = ArrayList()
    private var manifestSegment: Segment? = null
    private var totalAssembled: Long = 0
    private var newFileName: String? = null
    private var assembleFinished = false
    private var lastProgress = 0
    private var totalDuration = 0f

    private val flvSig = byteArrayOf('F'.code.toByte(), 'L'.code.toByte(), 'V'.code.toByte(), 0x01, 0x05, 0x00, 0x00, 0x00, 0x09, 0x00, 0x00, 0x00, 0x00)
    private val b = ByteArray(8192)

    init {
        this.id = id
        this.folder = File(folder, id).absolutePath
        this.length = -1
        this.MAX_COUNT = Config.getInstance().maxSegments
        chunks = ArrayList()
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
            if (initOrUpdateSegments()) {
                listener!!.downloadConfirmed(this.id)
            } else {
                if (!stopFlag) {
                    this.errorCode = XDMConstants.ERR_INVALID_RESP
                    listener!!.downloadFailed(this.id)
                }
                return true
            }
        } else {
            val s = getById(id) ?: return true
            if (s.length < 0) {
                s.length = s.downloaded
            }

            if (allFinished()) {
                finished = true
                var len = 0L
                for (ss in chunks) {
                    len += ss.length
                }
                if (len > 0) {
                    this.length = len
                }
                saveState()
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
                val md = HdsMetadata()
                md.url = urlList[i]
                md.headers = hdsMetadata.headers
                return HttpChannel(segment, md.url, md.headers, -1, isJavaClientRequired)
            }
        }
        Logger.log("Create manifest channel")
        return HttpChannel(segment, hdsMetadata.url, hdsMetadata.headers, -1, isJavaClientRequired)
    }

    override fun shouldCleanup(): Boolean = assembleFinished

    private fun initOrUpdateSegments(): Boolean {
        return try {
            val mf = F4MManifest(hdsMetadata.url, File(folder, manifestSegment!!.id).absolutePath)
            mf.selectedBitRate = hdsMetadata.bitRate.toLong()
            this.totalDuration = mf.duration.toFloat()
            Logger.log("Total duration $totalDuration")
            val urls = mf.getMediaUrls()
            if (urls.size < 1) {
                Logger.log("Manifest contains no media")
                return false
            }
            if (urlList.isNotEmpty() && urlList.size != urls.size) {
                Logger.log("Manifest media count mismatch- expected: ${urlList.size} got: ${urls.size}")
                return false
            }
            if (urlList.isNotEmpty()) {
                urlList.clear()
            }
            urlList.addAll(urls)

            var newExtension: String? = null

            if (chunks.size < 1) {
                for (i in urlList.indices) {
                    if (newExtension == null && outputFormat == 0) {
                        newExtension = findExtension(urlList[i])
                        Logger.log("HDS: found new extension: $newExtension")
                        if (newExtension != null) {
                            this.newFileName = getOutputFileName(false).replace(".flv", newExtension)
                        } else {
                            newExtension = ".flv"
                        }
                    }

                    Logger.log("HDS: Newfile name: $newFileName")

                    val s2 = SegmentImpl(this, folder)
                    s2.tag = "HLS"
                    s2.length = -1
                    Logger.log("Adding chunk: $s2")
                    chunks.add(s2)
                }
            }
            true
        } catch (e: Exception) {
            Logger.log(e)
            false
        }
    }

    @Synchronized
    private fun processSegments() {
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
    override val metadata: HttpMetadata? get() = this.hdsMetadata

    private fun saveState() {
        if (chunks.size < 1) return
        val sb = StringBuilder()
        sb.append("$length\n")
        sb.append("$downloaded\n")
        sb.append("${totalDuration.toLong()}\n")
        sb.append("${urlList.size}\n")
        for (url in urlList) {
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
        chunks = ArrayList()
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
                for (i in 0 until urlCount) {
                    val url = XDMUtils.readLineSafe(br)
                    urlList.add(url)
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

    private fun findExtension(urlStr: String): String? {
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
                }
            }
        }
        return newExtension
    }

    @Throws(IOException::class)
    private fun assemble() {
        var `in`: InputStream? = null
        var out: OutputStream? = null
        totalAssembled = 0L
        assembling = true
        assembleFinished = false
        var ffOutFile: File? = null
        var outFile: File? = null

        XDMUtils.mkdirs(getOutputFolder())

        val outFileName = if (outputFormat == 0) UUID.randomUUID().toString() + "_" + getOutputFileName(true)
        else UUID.randomUUID().toString()
        val outputFolder = if (outputFormat == 0) getOutputFolder() else folder
        outFile = File(outputFolder, outFileName)

        try {
            if (stopFlag) return
            Logger.log("assembling... ")
            out = FileOutputStream(outFile)
            out!!.write(flvSig)
            for (s in chunks) {
                val inFile = File(folder, s.id)
                `in` = FileInputStream(inFile)
                var streamPos = 0L
                val streamLen = inFile.length()
                while (streamPos < streamLen) {
                    if (stopFlag) return
                    var boxsize = readInt32(`in`!!)
                    streamPos += 4
                    val boxType = readStringBytes(`in`!!, 4)
                    streamPos += 4
                    if (boxsize == 1L) {
                        boxsize = readInt64(`in`!!) - 16
                        streamPos += 8
                    } else {
                        boxsize -= 8
                    }
                    if (boxType == "mdat") {
                        var boxsz = boxsize
                        while (boxsz > 0) {
                            if (stopFlag) return
                            val c = (if (boxsz > b.size) b.size else boxsz).toInt()
                            val x = `in`!!.read(b, 0, c)
                            if (x == -1) throw IOException("Unexpected EOF")
                            out!!.write(b, 0, x)
                            boxsz -= x
                            totalAssembled += x
                            val now = System.currentTimeMillis()
                            if (now - lastUpdated > 1000) {
                                updateStatus()
                                lastUpdated = now
                            }
                        }
                    } else {
                        `in`!!.skip(boxsize)
                    }
                    streamPos += boxsize
                }
                `in`!!.close()
            }
            out!!.close()

            Logger.log("Output format: $outputFormat")

            if (outputFormat != 0) {
                this.converting = true
                ffOutFile = File(getOutputFolder(), UUID.randomUUID().toString() + "_" + getOutputFileName(true))

                this.ffmpeg = FFmpeg(listOf(outFile!!.absolutePath), ffOutFile!!.absolutePath, this, MediaFormats.getSupportedFormats()!![outputFormat]!!, outputFormat == 0)
                val ret = ffmpeg!!.convert()
                Logger.log("FFmpeg exit code: $ret")

                if (ret != 0) {
                    throw IOException("FFmpeg failed")
                } else {
                    val len = ffOutFile!!.length()
                    if (len > 0) {
                        this.length = len
                    }
                    setLastModifiedDate(ffOutFile!!)
                }
            }

            val realFile = File(getOutputFolder(), getOutputFileName(true))
            if (realFile.exists()) {
                realFile.delete()
            }

            if (ffOutFile != null) {
                outFile!!.delete()
                outFile = ffOutFile
            }
            outFile!!.renameTo(realFile)

            assembleFinished = true
        } catch (e: Exception) {
            Logger.log(e)
        } finally {
            try { out?.close() } catch (_: Exception) {}
            try { `in`?.close() } catch (_: Exception) {}
            if (!assembleFinished) {
                outFile?.delete()
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

    @Throws(IOException::class)
    private fun readInt32(s: InputStream): Long {
        val bytesData = ByteArray(4)
        if (s.read(bytesData, 0, bytesData.size) != bytesData.size) {
            throw IOException("Invalid F4F box")
        }
        val iValLo = ((bytesData[3].toInt() and 0xff) + ((bytesData[2].toInt() and 0xff) * 256)).toLong()
        val iValHi = ((bytesData[1].toInt() and 0xff) + ((bytesData[0].toInt() and 0xff) * 256)).toLong()
        return iValLo + iValHi * 65536
    }

    @Throws(IOException::class)
    private fun readInt64(s: InputStream): Long {
        val iValHi = readInt32(s)
        val iValLo = readInt32(s)
        return iValLo + iValHi * 4294967296L
    }

    @Throws(IOException::class)
    private fun readStringBytes(s: InputStream, len: Long): String {
        val resultValue = StringBuilder(4)
        for (i in 0 until len) {
            resultValue.append(s.read().toChar())
        }
        return resultValue.toString()
    }
}
