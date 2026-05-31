package xdman.downloaders

import xdman.Config
import xdman.XDMConstants
import xdman.downloaders.http.HttpChannel
import xdman.downloaders.metadata.DashMetadata
import xdman.mediaconversion.FFmpeg
import xdman.mediaconversion.MediaConversionListener
import xdman.mediaconversion.MediaFormats
import xdman.util.FormatUtilities
import xdman.util.Logger
import xdman.util.StringUtils
import xdman.util.XDMUtils
import java.io.*
import java.util.*

abstract class SegmentDownloader(id: String, folder: String) : Downloader(), MediaConversionListener {
    private var init = false
    private var MIN_CHUNK_SIZE = 256 * 1024
    private var assembleFinished = false
    private var totalAssembled: Long = 0

    init {
        this.id = id
        this.folder = File(folder, id).absolutePath
        this.length = -1
        this.MAX_COUNT = Config.getInstance().maxSegments
        this.MIN_CHUNK_SIZE = Config.getInstance().minSegmentSize
        this.lastDownloaded = downloaded
        this.prevTime = System.currentTimeMillis()
        this.eta = "---"
    }

    override fun start() {
        Logger.log("creating folder $folder")
        File(folder).mkdirs()
        chunks = ArrayList()
        try {
            val c1 = SegmentImpl(this, folder)
            if (metadata is DashMetadata) {
                c1.tag = "T1"
            }
            c1.length = -1
            c1.startOffset = 0
            c1.downloaded = 0
            chunks.add(c1)
            c1.download(this)
        } catch (e: IOException) {
            this.errorCode = XDMConstants.RESUME_FAILED
            this.listener!!.downloadFailed(id)
        }
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
            this.lastDownloaded = downloaded
            this.prevTime = System.currentTimeMillis()
            Logger.log("Restore success")
            init = true
            val c1 = findInactiveChunk()
            if (c1 != null) {
                try {
                    c1.download(this)
                } catch (e: Exception) {
                    Logger.log(e)
                    if (!stopFlag) {
                        Logger.log(e)
                        this.errorCode = XDMConstants.RESUME_FAILED
                        listener!!.downloadFailed(this.id)
                        return
                    }
                }
            } else if (allFinished()) {
                assembleAsync()
            } else {
                Logger.log("Internal error: no inactive/incomplete chunk found while resuming!")
            }
        } catch (e: Exception) {
            Logger.log(e)
            this.errorCode = XDMConstants.RESUME_FAILED
            listener!!.downloadFailed(this.id)
        }
    }

    @Synchronized
    @Throws(IOException::class)
    private fun createChunk() {
        if (stopFlag) return
        val activeCount = activeChunkCount
        Logger.log("active count:$activeCount")
        if (activeCount == MAX_COUNT) return

        var rem = MAX_COUNT - activeCount
        rem -= retryFailedChunks(rem)

        if (rem > 0) {
            val c1 = findMaxChunk()
            val c = splitChunk(c1)
            if (c != null) {
                Logger.log("creating chunk $c")
                chunks.add(c)
                c.download(this)
            }
        }
    }

    private fun findMaxChunk(): Segment? {
        if (stopFlag) return null
        var size = -1L
        var id: String? = null
        for (i in chunks.indices) {
            val c = chunks[i]
            if (c.isActive) {
                val rem = c.length - c.downloaded
                if (rem > size) {
                    id = c.id
                    size = rem
                }
            }
        }
        return if (size < MIN_CHUNK_SIZE) null else getById(id!!)
    }

    private fun mergeChunk(c1: Segment, c2: Segment) {
        c1.length = c1.length + c2.length
    }

    @Throws(IOException::class)
    private fun splitChunk(c: Segment?): Segment? {
        if (c == null || stopFlag) return null
        val rem = c.length - c.downloaded
        val offset = c.startOffset + c.length - rem / 2
        val len = rem / 2
        Logger.log("Changing length from: ${c.length} to ${c.length - rem / 2}")
        c.length = c.length - rem / 2
        val c2 = SegmentImpl(this, folder)
        if (metadata is DashMetadata) {
            c2.tag = "T1"
        }
        c2.length = len
        c2.startOffset = offset
        return c2
    }

    private fun findNextNeedyChunk(chunk: Segment): Segment? {
        if (stopFlag) return null
        val offset = chunk.startOffset + chunk.length
        for (i in chunks.indices) {
            val c = chunks[i]
            if (c.downloaded == 0L && !c.isFinished && c.startOffset == offset) {
                return c
            }
        }
        return null
    }

    @Synchronized
    @Throws(IOException::class)
    private fun onComplete(id: String): Boolean {
        if (allFinished() || length < 0) {
            finished = true
            updateStatus()
            try {
                assemble()
                if (!assembleFinished) throw IOException("Assemble failed")
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
        val chunk = getById(id)!!
        Logger.log("Complete: $chunk ${chunk.downloaded} ${chunk.length}")
        val nextNeedyChunk = findNextNeedyChunk(chunk)
        if (nextNeedyChunk != null) {
            Logger.log("****************Needy chunk found!!!")
            Logger.log("Stopping: $nextNeedyChunk")
            nextNeedyChunk.stop()
            chunks.remove(nextNeedyChunk)
            nextNeedyChunk.dispose()
            mergeChunk(chunk, nextNeedyChunk)
            createChunk()
            return false
        }
        clearChannel(chunk)
        createChunk()
        return true
    }

    @Synchronized
    @Throws(IOException::class)
    override fun chunkInitiated(id: String) {
        if (stopFlag) return
        if (!init) {
            val c = getById(id)!!
            this.length = c.length
            init = true
            Logger.log("size: ${this.length}")
            if (c.channel is HttpChannel) {
                getLastModifiedDate(c)
            }
            saveState()
            chunkConfirmed(c)
            listener!!.downloadConfirmed(this.id)
        }
        if (length > 0) {
            createChunk()
        }
    }

    @Synchronized
    @Throws(IOException::class)
    override fun chunkComplete(id: String): Boolean {
        if (finished) return true
        if (stopFlag) return true
        saveState()
        return onComplete(id)
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
            synchronized(this) {
                val activeCount = activeChunkCount
                if (activeCount < MAX_COUNT) {
                    val rem = MAX_COUNT - activeCount
                    try {
                        retryFailedChunks(rem)
                    } catch (e: Exception) {
                        Logger.log(e)
                    }
                }
            }
        }
    }

    @Synchronized
    override fun chunkFailed(id: String, reason: String) {
        super.chunkFailed(id, reason)
    }

    @Throws(IOException::class)
    private fun assemble() {
        var `in`: InputStream? = null
        var out: OutputStream? = null
        totalAssembled = 0L
        assembling = true
        assembleFinished = false
        val outFileFinal = getOutputFileName(true)
        val outFileName = if (outputFormat == 0) UUID.randomUUID().toString() + "_" + outFileFinal
        else UUID.randomUUID().toString()
        val outputFolder = if (outputFormat == 0) getOutputFolder() else folder
        XDMUtils.mkdirs(getOutputFolder())
        var outFile = File(outputFolder, outFileName)
        var ffOutFile: File? = null
        try {
            if (stopFlag) return
            val buf = ByteArray(1024 * 1024)
            Logger.log("assembling... ")
            java.util.Collections.sort(chunks, SegmentComparator())
            out = FileOutputStream(outFile)
            for (i in chunks.indices) {
                Logger.log("chunk $i $stopFlag")
                val c = chunks[i]
                `in` = FileInputStream(File(folder, c.id))
                var rem = c.length
                while (true) {
                    val x = (if (rem > 0) (if (rem > buf.size) buf.size else rem) else buf.size).toInt()
                    val r = `in`!!.read(buf, 0, x)
                    if (stopFlag) return
                    if (r == -1) {
                        if (length > 0) throw IllegalArgumentException("Assemble EOF")
                        else break
                    }
                    out!!.write(buf, 0, r)
                    if (stopFlag) return
                    if (length > 0) {
                        rem -= r
                        if (rem == 0L) break
                    }
                    totalAssembled += r
                    val now = System.currentTimeMillis()
                    if (now - lastUpdated > 1000) {
                        updateStatus()
                        lastUpdated = now
                    }
                }
                `in`!!.close()
            }
            out!!.close()
            setLastModifiedDate(outFile)
            updateStatus()
            if (outputFormat != 0) {
                XDMUtils.mkdirs(getOutputFolder())
                converting = true
                ffOutFile = File(getOutputFolder(), UUID.randomUUID().toString() + "_" + getOutputFileName(true))
                this.ffmpeg = FFmpeg(listOf(outFile.absolutePath), ffOutFile!!.absolutePath, this, MediaFormats.getSupportedFormats()!![outputFormat]!!, outputFormat == 0)
                val ret = ffmpeg!!.convert()
                Logger.log("FFmpeg exit code: $ret")
                if (ret != 0) throw IOException("FFmpeg failed")
                else {
                    val len = ffOutFile!!.length()
                    if (len > 0) this.length = len
                }
            }
            val realFile = File(getOutputFolder(), getOutputFileName(true))
            if (realFile.exists()) realFile.delete()
            if (ffOutFile != null) {
                outFile.delete()
                outFile = ffOutFile
            }
            outFile.renameTo(realFile)
            setLastModifiedDate(outFile)
            assembleFinished = true
        } catch (e: Exception) {
            Logger.log(e)
            throw IOException(e)
        } finally {
            if (`in` != null) {
                try { `in`!!.close() } catch (_: Exception) {}
            }
            if (out != null) {
                try { out!!.close() } catch (_: Exception) {}
            }
            if (!assembleFinished) {
                outFile.delete()
                ffOutFile?.delete()
            }
        }
    }

    override abstract fun createChannel(segment: Segment): AbstractChannel

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

    private fun saveState() {
        if (length < 0) return
        val sb = StringBuilder()
        sb.append("$length\n")
        sb.append("$downloaded\n")
        sb.append("${chunks.size}\n")
        for (i in chunks.indices) {
            val seg = chunks[i]
            sb.append("${seg.id}\n")
            sb.append("${seg.length}\n")
            sb.append("${seg.startOffset}\n")
            sb.append("${seg.downloaded}\n")
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
                val chunkCount = br.readLine().toInt()
                for (i in 0 until chunkCount) {
                    val cid = XDMUtils.readLineSafe(br)
                    val len = br.readLine().toLong()
                    val off = br.readLine().toLong()
                    val dwn = br.readLine().toLong()
                    val seg = SegmentImpl(folder, cid, off, len, dwn)
                    if (metadata is DashMetadata) {
                        seg.tag = "T1"
                    }
                    Logger.log("id: ${seg.id}\nlength: ${seg.length}\noffset: ${seg.startOffset}\ndownload: ${seg.downloaded}")
                    chunks.add(seg)
                }
                this.lastModified = br.readLine()
            }
            return true
        } catch (e: Exception) {
            Logger.log("Failed to load saved state")
            Logger.log(e)
        }
        return false
    }

    protected abstract fun chunkConfirmed(c: Segment)

    override fun shouldCleanup(): Boolean = assembleFinished

    private fun updateStatus() {
        try {
            val now = System.currentTimeMillis()
            if (converting) {
                progress = this.convertPrg
            } else if (this.assembling) {
                val len = if (length > 0) length else downloaded
                progress = ((totalAssembled * 100) / len).toInt()
            } else {
                var downloaded2 = 0L
                if (segDet == null) {
                    segDet = SegmentDetails()
                }
                if (segDet!!.capacity < chunks.size) {
                    segDet!!.extend(chunks.size - segDet!!.capacity)
                }
                segDet!!.chunkCount = chunks.size.toLong()
                downloadSpeed = 0f
                for (i in chunks.indices) {
                    val s = chunks[i]
                    downloaded2 += s.downloaded
                    val info = segDet!!.chunkUpdates[i]
                    info.downloaded = s.downloaded
                    info.start = s.startOffset
                    info.length = s.length
                    downloadSpeed += s.transferRate
                }
                this.downloaded = downloaded2
                if (length > 0) {
                    progress = ((downloaded * 100) / length).toInt()
                    val diff = downloaded - lastDownloaded
                    val timeSpend = now - prevTime
                    if (timeSpend > 0) {
                        val rate = (diff.toFloat() / timeSpend) * 1000
                        this.eta = FormatUtilities.getETA((length - downloaded).toDouble(), rate) ?: "---"
                        lastDownloaded = downloaded
                        prevTime = now
                    }
                }
            }
            listener!!.downloadUpdated(id)
        } catch (e: Exception) {
            Logger.log(e)
        }
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

    override fun progress(progress: Int) {
        this.convertPrg = progress
        val now = System.currentTimeMillis()
        if (now - lastUpdated > 1000) {
            updateStatus()
            lastUpdated = now
        }
    }

}
