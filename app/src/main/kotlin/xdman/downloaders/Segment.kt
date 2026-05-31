package xdman.downloaders

import java.io.IOException
import java.io.RandomAccessFile

interface Segment {
    var length: Long
    var startOffset: Long
    var downloaded: Long
    var outStream: RandomAccessFile?
    var id: String
    var chunkListener: SegmentListener?
    var channel: AbstractChannel?
    val transferRate: Float
    var errorCode: Int
    var tag: Any?
    val isFinished: Boolean
    val isActive: Boolean

    @Throws(IOException::class)
    fun transferComplete(): Boolean

    @Throws(IOException::class)
    fun transferInitiated()

    fun transferring()

    fun transferFailed(reason: String?)

    @Throws(IOException::class)
    fun download(listenre: SegmentListener)

    fun stop()
    fun dispose()

    @Throws(IOException::class)
    fun resetStream()

    @Throws(IOException::class)
    fun reopenStream()

    fun promptCredential(msg: String, proxy: Boolean): Boolean
    fun clearChannel()
}
