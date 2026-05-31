package xdman.downloaders

import xdman.Config
import xdman.util.Logger
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.UUID

class SegmentImpl : Segment {
    @Volatile
    override var length: Long = 0
    override var startOffset: Long = 0
    @Volatile
    override var downloaded: Long = 0
    @Volatile
    override var outStream: RandomAccessFile? = null
    override var id: String = ""
    @Volatile
    private var cl: SegmentListener? = null
    @Volatile
    private var _channel: AbstractChannel? = null

    private var bytesRead1: Long = 0
    private var bytesRead2: Long = 0
    private var time1: Long = 0
    private var time2: Long = 0
    private var transRate: Float = 0f
    private var config: Config = Config.getInstance()
    @Volatile
    private var stop = false
    override var errorCode: Int = 0
    override var tag: Any? = null
    private var folder: String = ""

    override var chunkListener: SegmentListener?
        get() = cl
        set(value) { cl = value }

    override var channel: AbstractChannel?
        get() = _channel
        set(value) { _channel = value }

    @Throws(IOException::class)
    constructor(cl: SegmentListener, folder: String) {
        id = UUID.randomUUID().toString()
        this.cl = cl
        this.folder = folder
        this.time1 = System.currentTimeMillis()
        this.time2 = time1
        this.config = Config.getInstance()
        outStream = RandomAccessFile(File(folder, id), "rw")
        Logger.log("File opened $id")
    }

    @Throws(IOException::class)
    constructor(folder: String, id: String, off: Long, len: Long, dwn: Long) {
        this.id = id
        this.startOffset = off
        this.folder = folder
        this.length = len
        this.downloaded = dwn
        this.time1 = System.currentTimeMillis()
        this.time2 = time1
        this.bytesRead1 = dwn
        this.bytesRead2 = dwn
        try {
            outStream = RandomAccessFile(File(folder, id), "rw")
            outStream!!.seek(dwn)
            Logger.log("File opened $id")
        } catch (e: IOException) {
            Logger.log(e)
            outStream?.close()
            throw IOException(e)
        }
        this.config = Config.getInstance()
    }

    override val isFinished: Boolean
        get() = (length - downloaded) == 0L

    override val isActive: Boolean
        get() = this._channel != null

    override val transferRate: Float
        get() {
            try {
                if (isFinished) return 0f
            } catch (_: Exception) {
            }
            return transRate
        }

    @Throws(IOException::class)
    override fun transferComplete(): Boolean {
        if (stop) return true
        if (length < 0) {
            length = downloaded
        }
        if (cl!!.chunkComplete(id)) {
            try {
                outStream?.close()
            } catch (e: IOException) {
                Logger.log(e)
            }
            _channel = null
            if (cl != null) {
                if (cl!!.shouldCleanup()) {
                    cl!!.cleanup()
                }
            }
            return true
        }
        return false
    }

    override fun clearChannel() {
        this._channel = null
    }

    @Throws(IOException::class)
    override fun transferInitiated() {
        if (stop) return
        cl!!.chunkInitiated(id)
        time2 = System.currentTimeMillis()
    }

    override fun transferFailed(reason: String?) {
        if (stop) return
        if (outStream != null) {
            try {
                outStream?.close()
                outStream = null
            } catch (e: IOException) {
                Logger.log(e)
            }
        }
        this.errorCode = _channel?.errorCode ?: 0
        Logger.log("$id notifying failure $this._channel")
        this._channel = null
        cl?.chunkFailed(id, reason!!)
        cl = null
    }

    @Throws(IOException::class)
    override fun download(listenre: SegmentListener) {
        this.cl = listenre
        _channel = cl!!.createChannel(this)
        _channel!!.open()
    }

    override fun stop() {
        stop = true
        dispose()
    }

    override fun dispose() {
        cl = null
        _channel?.stop()
        if (outStream != null) {
            try {
                outStream?.close()
            } catch (e: IOException) {
                Logger.log(e)
            }
        }
    }

    override fun toString(): String = id

    override fun transferring() {
        if (stop) return
        cl?.chunkUpdated(id)
        calculateTransferRate()
        throttle()
    }

    private fun calculateTransferRate() {
        val now = System.currentTimeMillis()
        val timeDiff = now - time1
        val bytesDiff = this.downloaded - bytesRead1
        if (timeDiff > 1000) {
            transRate = (bytesDiff.toFloat() / timeDiff) * 1000
            bytesRead1 = this.downloaded
            time1 = now
        }
    }

    private fun throttle() {
        try {
            if (config.speedLimit < 1) return
            if (cl!!.activeChunkCount < 1) return
            val maxBpms = (config.speedLimit * 1024) / (cl!!.activeChunkCount * 1000)
            val now = System.currentTimeMillis()
            val timeSpentInReal = now - time2
            if (timeSpentInReal > 0) {
                time2 = now
                val bytesDownloaded = downloaded - bytesRead2
                bytesRead2 = downloaded
                val timeShouldRequired = bytesDownloaded / maxBpms
                if (timeShouldRequired > timeSpentInReal) {
                    val timeNeedToSleep = timeShouldRequired - timeSpentInReal
                    Thread.sleep(timeNeedToSleep)
                }
            }
        } catch (_: Exception) {
        }
    }

    fun getErrorMsg(): String? = null

    @Throws(IOException::class)
    override fun resetStream() {
        outStream!!.seek(0)
        outStream!!.setLength(0)
    }

    @Throws(IOException::class)
    override fun reopenStream() {
        if (outStream != null) return
        try {
            outStream = RandomAccessFile(File(folder, id), "rw")
            outStream!!.seek(downloaded)
            Logger.log("File opened $id")
        } catch (e: IOException) {
            Logger.log(e)
            outStream?.close()
            throw IOException(e)
        }
    }

    override fun promptCredential(msg: String, proxy: Boolean): Boolean {
        return cl!!.promptCredential(msg, proxy)
    }
}
