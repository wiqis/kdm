package xdman.downloaders

import xdman.Config
import xdman.DownloadListener
import xdman.XDMApp
import xdman.XDMConstants
import xdman.downloaders.http.HttpChannel
import xdman.downloaders.metadata.HttpMetadata
import xdman.mediaconversion.FFmpeg
import xdman.util.HttpDateParser
import xdman.util.Logger
import xdman.util.StringUtils
import java.io.File
import java.io.IOException

abstract class Downloader : SegmentListener {
    @JvmField
    @Volatile
    protected var stopFlag = false
    @JvmField
    protected var isJavaClientRequired = false
    @JvmField
    protected var length: Long = 0
    @JvmField
    protected var folder: String = ""
    @JvmField
    protected var id: String = ""
    @JvmField
    protected var finished = false
    @JvmField
    protected var MAX_COUNT = 8
    @JvmField
    protected var listener: DownloadListener? = null
    @JvmField
    var downloaded: Long = 0
    @JvmField
    protected var lastDownloaded: Long = 0
    @JvmField
    protected var prevTime: Long = 0
    @JvmField
    var progress: Int = 0
    @JvmField
    protected var lastUpdated: Long = 0
    @JvmField
    protected var lastSaved: Long = 0
    @JvmField
    var assembling = false
    @JvmField
    var downloadSpeed: Float = 0f
    @JvmField
    var eta: String = ""
    @JvmField
    var segDet: SegmentDetails? = null
    @JvmField
    var errorCode: Int = 0
    @JvmField
    protected var outputFormat = 0
    @JvmField
    var converting = false
    @JvmField
    protected var convertPrg = 0
    @JvmField
    protected var lastModified: String? = null
    @JvmField
    protected var ffmpeg: FFmpeg? = null

    @JvmField
    protected var chunks: ArrayList<Segment> = ArrayList()

    override val size: Long get() = length

    abstract fun start()
    abstract fun stop()
    abstract fun resume()
    abstract val type: Int

    abstract val isFileNameChanged: Boolean
    abstract val newFile: String?
    abstract val metadata: HttpMetadata?

    val segmentDetails: SegmentDetails?
        get() = segDet

    fun setOuputMediaFormat(format: Int) {
        this.outputFormat = format
    }

    @Synchronized
    @Throws(IOException::class)
    protected fun retryFailedChunks(rem: Int): Int {
        if (stopFlag) return 0
        var count = 0
        var totalInactive = findTotalInactiveChunk()
        Logger.log("Total inactive chunks: $totalInactive")
        if (totalInactive > rem) {
            totalInactive = rem
        }
        if (totalInactive > 0) {
            for (i in 0 until totalInactive) {
                val c = findInactiveChunk()
                if (c != null) {
                    c.download(this)
                    count++
                } else {
                    Logger.log("$$$ debug rem:$rem")
                }
            }
        }
        return count
    }

    protected fun findInactiveChunk(): Segment? {
        if (stopFlag) return null
        for (i in chunks.indices) {
            val c = chunks[i]
            if (c.isFinished || c.isActive) continue
            return c
        }
        return null
    }

    protected fun findTotalInactiveChunk(): Int {
        var count = 0
        for (i in chunks.indices) {
            val c = chunks[i]
            if (c.isFinished || c.isActive) continue
            count++
        }
        return count
    }

    override val activeChunkCount: Int
        get() {
            var count = 0
            for (i in chunks.indices) {
                if (chunks[i].isActive) count++
            }
            return count
        }

    fun registerListener(listener: DownloadListener) {
        this.listener = listener
    }

    fun unregisterListener() {
        this.listener = null
    }

    protected fun allFinished(): Boolean {
        if (chunks.isNotEmpty()) {
            for (i in chunks.indices) {
                if (!chunks[i].isFinished) return false
            }
            return true
        }
        return false
    }

    protected fun getById(id: String): Segment? {
        for (i in chunks.indices) {
            if (chunks[i].id == id) return chunks[i]
        }
        return null
    }

    override fun cleanup() {
        val dir = File(folder)
        val files = dir.listFiles()
        if (files != null) {
            for (i in files.indices) {
                Logger.log("Delete: ${files[i]} [${files[i].length()}] ${files[i].delete()}")
            }
        }
        File(folder).delete()
    }

    @Synchronized
    override fun synchronize() {
    }

    @Synchronized
    override fun chunkFailed(id: String, reason: String) {
        if (stopFlag) return
        var err = 0
        for (i in chunks.indices) {
            val chunk = chunks[i]
            if (chunk.isActive) return
            if (chunk.errorCode != 0) {
                err = chunk.errorCode
            }
        }
        this.errorCode = when {
            err == XDMConstants.ERR_INVALID_RESP -> {
                if (downloaded > 0) {
                    if (length > 0) {
                        if (chunks.size > 1) XDMConstants.ERR_SESSION_FAILED
                        else XDMConstants.ERR_NO_RESUME
                    } else {
                        XDMConstants.ERR_NO_RESUME
                    }
                } else {
                    XDMConstants.ERR_INVALID_RESP
                }
            }
            else -> {
                Logger.log("Setting final error code: $err")
                err
            }
        }
        this.listener!!.downloadFailed(this.id)
        Logger.log("failed")
    }

    protected fun getOutputFileName(updated: Boolean): String {
        return listener!!.getOutputFile(id, updated)
    }

    protected fun getOutputFolder(): String {
        return listener!!.getOutputFolder(id)
    }

    override fun promptCredential(msg: String, proxy: Boolean): Boolean {
        return XDMApp.getInstance().promptCredential(id, msg, proxy)
    }

    protected fun getBackupFile(folder: String): File? {
        val f = File(folder)
        val files = f.listFiles()
        if (files == null || files.size < 1) return null
        for (file in files) {
            if (file.name.endsWith(".bak")) return file
        }
        return null
    }

    fun setLastModifiedDate(outFile: File) {
        if (Config.getInstance().isFetchTs) {
            try {
                println("setting date")
                val lastModified = HttpDateParser.parseHttpDate(this.lastModified)
                if (lastModified != null) {
                    println("setting date file $lastModified")
                    val `val` = outFile.setLastModified(lastModified.time)
                    println("rename: $`val` ${java.util.Date(outFile.lastModified())}")
                }
            } catch (e: Exception) {
                Logger.log(e)
            }
        }
    }

    fun getLastModifiedDate(c: Segment) {
        if (StringUtils.isNullOrEmpty(lastModified)) {
            try {
                this.lastModified = (c.channel as HttpChannel).getHeader("last-modified")
            } catch (e: Exception) {
                Logger.log(e)
            }
        }
    }

    protected fun clearChannel(s: Segment?) {
        s?.clearChannel()
    }
}
