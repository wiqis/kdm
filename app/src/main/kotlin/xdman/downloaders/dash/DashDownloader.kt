package xdman.downloaders.dash

import xdman.Config
import xdman.XDMConstants
import xdman.downloaders.AbstractChannel
import xdman.downloaders.Downloader
import xdman.downloaders.Segment
import xdman.downloaders.SegmentComparator
import xdman.downloaders.SegmentDetails
import xdman.downloaders.SegmentImpl
import xdman.downloaders.SegmentInfo
import xdman.downloaders.http.HttpChannel
import xdman.downloaders.metadata.DashMetadata
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

class DashDownloader(id: String, folder: String, private var dashMetadata: DashMetadata) : Downloader(), MediaConversionListener {
    private var MIN_CHUNK_SIZE = 256 * 1024
    private var len1: Long = 0
    private var len2: Long = 0
    private var assembleFinished = false
    private var totalAssembled: Long = 0

    init {
        this.id = id
        this.folder = File(folder, id).absolutePath
        this.length = -1
        this.MAX_COUNT = Config.getInstance().maxSegments
        this.MIN_CHUNK_SIZE = Config.getInstance().minSegmentSize
        this.eta = "---"
    }

    override fun start() {
        Logger.log("creating folder $folder")
        File(folder).mkdirs()
        this.lastDownloaded = downloaded
        this.prevTime = System.currentTimeMillis()
        chunks = Collections.synchronizedList(ArrayList())
        try {
            val c1 = SegmentImpl(this, folder)
            c1.tag = "T1"
            c1.length = -1
            c1.startOffset = 0
            c1.downloaded = 0
            chunks.add(c1)

            val c2 = SegmentImpl(this, folder)
            c2.tag = "T2"
            c2.length = -1
            c2.startOffset = 0
            c2.downloaded = 0
            chunks.add(c2)

            c1.download(this)
        } catch (e: IOException) {
            this.errorCode = XDMConstants.RESUME_FAILED
            this.listener!!.downloadFailed(id)
        }
    }

    override fun createChannel(segment: Segment): AbstractChannel {
        val len = if ("T1" == segment.tag) dashMetadata.len1 else dashMetadata.len2
        val url = if ("T1" == segment.tag) dashMetadata.url else dashMetadata.url2 ?: ""
        return HttpChannel(
            segment,
            url,
            if ("T1" == segment.tag) dashMetadata.headers else dashMetadata.headers2,
            len,
            isJavaClientRequired
        )
    }

    @Synchronized
    @Throws(IOException::class)
    override fun chunkInitiated(id: String) {
        if (stopFlag) return
        val c = getById(id) ?: return
        if (isFirstChunk(c)) {
            getLastModifiedDate(c)
            if (c.tag == "T1") {
                this.len1 = c.length
            } else if (c.tag == "T2") {
                this.len2 = c.length
            }
            saveState()
        }

        if (this.length < 1 && this.len1 > 0 && this.len2 > 0) {
            this.length = len1 + len2
            Logger.log("length set - this.len1: $len1 this.len2: $len2")
            listener!!.downloadConfirmed(this.id)
        } else {
            Logger.log("this.len1: $len1 this.len2: $len2")
        }

        if ("T1" == c.tag && this.len1 > 0) {
            createChunk()
        }
        if ("T2" == c.tag && this.len2 > 0) {
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
                    } catch (e: IOException) {
                        Logger.log(e)
                    }
                }
            }
        }
    }

    override fun shouldCleanup(): Boolean = assembleFinished

    private fun assemble() {
        val tf1 = File(folder, "T1")
        val tf2 = File(folder, "T2")
        var outFile: File? = null
        XDMUtils.mkdirs(getOutputFolder())
        try {
            assembleFinished = false
            val list1 = ArrayList<Segment>()
            val list2 = ArrayList<Segment>()
            for (sc in chunks) {
                if (sc.tag == "T1") {
                    list1.add(sc)
                } else {
                    list2.add(sc)
                }
            }

            assemblePart(tf1, list1)
            if (stopFlag) return
            assemblePart(tf2, list2)
            if (stopFlag) return

            val inputFiles = ArrayList<String>()
            inputFiles.add(tf1.absolutePath)
            inputFiles.add(tf2.absolutePath)

            this.converting = true
            outFile = File(getOutputFolder(), UUID.randomUUID().toString() + "_" + getOutputFileName(true))

            this.ffmpeg = FFmpeg(inputFiles, outFile!!.absolutePath, this, MediaFormats.getSupportedFormats()!![outputFormat]!!, outputFormat == 0)
            val ret = ffmpeg!!.convert()
            Logger.log("FFmpeg exit code: $ret")

            if (ret != 0) {
                throw IOException("FFmpeg failed")
            } else {
                val length2 = outFile!!.length()
                if (length2 > 0) {
                    this.length = length2
                }
                setLastModifiedDate(outFile!!)
            }

            val realFile = File(getOutputFolder(), getOutputFileName(true))
            if (realFile.exists()) {
                realFile.delete()
            }
            outFile!!.renameTo(realFile)

            assembleFinished = true
        } finally {
            if (!assembleFinished) {
                tf1.delete()
                tf2.delete()
                if (outFile != null) {
                    outFile.delete()
                }
            }
        }
    }

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
                if (length > 0) {
                    if (segDet == null) {
                        segDet = SegmentDetails()
                    }
                    if (segDet!!.capacity < chunks.size) {
                        segDet!!.extend(chunks.size - segDet!!.capacity)
                    }
                    segDet!!.chunkCount = chunks.size.toLong()
                }
                downloadSpeed = 0f
                for (i in chunks.indices) {
                    val s = chunks[i]
                    downloaded2 += s.downloaded
                    if (length > 0) {
                        var off: Long = 0
                        if (s.tag == "T2") {
                            off = len1
                        }
                        val info: SegmentInfo = segDet!!.chunkUpdates[i]
                        info.downloaded = s.downloaded
                        info.start = s.startOffset + off
                        info.length = s.length
                    }
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

    private fun assemblePart(file: File, list: ArrayList<Segment>) {
        var `in`: InputStream? = null
        var out: OutputStream? = null
        totalAssembled = 0L
        assembling = true
        Logger.log("Combining $file ${list.size}")
        try {
            if (stopFlag) return
            val buf = ByteArray(8192 * 8)
            Logger.log("assembling... $stopFlag")
            Collections.sort(list, SegmentComparator())
            out = FileOutputStream(file)
            for (i in list.indices) {
                Logger.log("chunk $i $stopFlag")
                val c = list[i]
                `in` = FileInputStream(File(folder, c.id))
                var rem = c.length
                while (true) {
                    val x = (if (rem > 0) (if (rem > buf.size) buf.size else rem) else buf.size).toInt()
                    val r = `in`!!.read(buf, 0, x)
                    if (stopFlag) {
                        return
                    }
                    if (r == -1) {
                        if (length > 0) {
                            `in`!!.close()
                            out!!.close()
                            throw IllegalArgumentException("Assemble EOF")
                        } else {
                            break
                        }
                    }
                    out!!.write(buf, 0, r)
                    if (stopFlag) {
                        return
                    }
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
        }
    }

    private fun isFirstChunk(s: Segment): Boolean {
        var c = 0
        for (ss in chunks) {
            if (ss.tag == s.tag) {
                c++
            }
        }
        return c == 1
    }

    @Synchronized
    @Throws(IOException::class)
    private fun onComplete(id: String): Boolean {
        if (allFinished()) {
            finished = true
            updateStatus()
            try {
                assemble()
                if (!assembleFinished) {
                    throw IOException("Assemble failed")
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
            this.prevTime = System.currentTimeMillis()

            if (allFinished()) {
                assembleAsync()
                return
            }

            var c1: Segment? = null
            for (i in chunks.indices) {
                val c = chunks[i]
                if (c.isFinished || c.isActive) continue
                if (c.tag == "T1") {
                    c1 = c
                    break
                }
            }

            var c2: Segment? = null
            for (i in chunks.indices) {
                val c = chunks[i]
                if (c.isFinished || c.isActive) continue
                if (c.tag == "T2") {
                    c2 = c
                    break
                }
            }

            if (c1 != null) {
                try {
                    c1.download(this)
                } catch (e: IOException) {
                    Logger.log(e)
                }
            }

            if (c2 != null) {
                try {
                    if (c1 == null) {
                        c2.download(this)
                    }
                } catch (e: IOException) {
                    Logger.log(e)
                }
            }

            if (c1 == null && c2 == null) {
                Logger.log("Internal error: no inactive/incomplete chunk found while resuming!")
            }
        } catch (e: Exception) {
            Logger.log(e)
            this.errorCode = XDMConstants.RESUME_FAILED
            listener!!.downloadFailed(this.id)
            return
        }
    }

    override val type: Int get() = XDMConstants.DASH
    override val isFileNameChanged: Boolean get() = false
    override val newFile: String? get() = null
    override val metadata: HttpMetadata? get() = this.dashMetadata

    private fun saveState() {
        if (chunks.size < 1) return
        val sb = StringBuilder()
        sb.append("$length\n")
        sb.append("$downloaded\n")
        sb.append("$len1\n")
        sb.append("$len2\n")
        sb.append("${chunks.size}\n")
        for (i in chunks.indices) {
            val seg = chunks[i]
            sb.append("${seg.id}\n")
            sb.append("${seg.length}\n")
            sb.append("${seg.startOffset}\n")
            sb.append("${seg.downloaded}\n")
            sb.append("${seg.tag}\n")
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
        var file = File(folder, "state.txt")
        if (!file.exists()) {
            file = getBackupFile(folder) ?: return false
        }
        try {
            BufferedReader(FileReader(file)).use { br ->
                this.length = br.readLine().toLong()
                this.downloaded = br.readLine().toLong()
                this.len1 = br.readLine().toLong()
                this.len2 = br.readLine().toLong()
                val chunkCount = br.readLine().toInt()
                for (i in 0 until chunkCount) {
                    val cid = XDMUtils.readLineSafe(br)
                    val len = br.readLine().toLong()
                    val off = br.readLine().toLong()
                    val dwn = br.readLine().toLong()
                    val tag = XDMUtils.readLineSafe(br)
                    val seg = SegmentImpl(folder, cid, off, len, dwn)
                    seg.tag = tag
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
                if (!assembleFinished) {
                    throw IOException("Assemble not finished successfully")
                }
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

    @Synchronized
    @Throws(IOException::class)
    private fun createChunk() {
        if (stopFlag) return
        val activeCount = activeChunkCount
        Logger.log("active count:$activeCount")
        if (activeCount == MAX_COUNT) {
            Logger.log("Maximum chunk created")
            return
        }

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
        c2.tag = c.tag
        c2.length = len
        c2.startOffset = offset
        return c2
    }

    private fun findNextNeedyChunk(chunk: Segment): Segment? {
        if (stopFlag) return null
        val offset = chunk.startOffset + chunk.length
        for (i in chunks.indices) {
            val c = chunks[i]
            if (c.downloaded == 0L) {
                if (!c.isFinished) {
                    if (c.startOffset == offset && chunk.tag == c.tag) {
                        return c
                    }
                }
            }
        }
        return null
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
