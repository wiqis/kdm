package xdman.downloaders

import java.io.IOException

interface SegmentListener {
    @Throws(IOException::class)
    fun chunkInitiated(id: String)

    fun chunkFailed(id: String, reason: String)

    @Throws(IOException::class)
    fun chunkComplete(id: String): Boolean

    fun chunkUpdated(id: String)

    fun synchronize()

    fun createChannel(segment: Segment): AbstractChannel

    fun cleanup()

    val size: Long

    fun shouldCleanup(): Boolean

    val activeChunkCount: Int

    fun promptCredential(msg: String, proxy: Boolean): Boolean
}
