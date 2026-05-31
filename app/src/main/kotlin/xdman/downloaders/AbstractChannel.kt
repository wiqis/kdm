package xdman.downloaders

import xdman.downloaders.http.HttpChannel
import xdman.util.Logger
import java.io.InputStream

abstract class AbstractChannel(@JvmField protected var chunk: Segment) : Runnable {
    @JvmField
    protected var `in`: InputStream? = null
    private val buf = ByteArray(8 * 8192)
    @JvmField
    @Volatile
    protected var stop = false
    @JvmField
    protected var errorMessage: String? = null
    private var closed = false
    private var t: Thread? = null
    @JvmField
    @Volatile
    var errorCode = 0

    fun open() {
        t = Thread(this)
        t!!.name = this.chunk.id
        t!!.start()
    }

    protected abstract fun connectImpl(): Boolean
    protected abstract fun getInputStreamImpl(): InputStream?
    protected abstract fun getLengthImpl(): Long
    protected abstract fun closeImpl()

    private fun connect(): Boolean {
        try {
            chunk.chunkListener!!.synchronize()
        } catch (e: NullPointerException) {
            Logger.log("stopped chunk $chunk")
            return false
        }
        if (connectImpl()) {
            `in` = getInputStreamImpl()
            val length = getLengthImpl()
            if (chunk.length < 0) {
                Logger.log("Setting length of ${chunk.id} to: $length")
                chunk.length = length
            }
            return true
        }
        return false
    }

    override fun run() {
        try {
            while (!stop) {
                if (!connect()) {
                    if (!stop) {
                        chunk.transferFailed(errorMessage)
                    }
                    close()
                    break
                }
                chunk.transferInitiated()
                if (if (chunk.length > 0) copyStream1() else copyStream2()) {
                    Logger.log("Copy Stream finished")
                    break
                } else {
                    Logger.log("Copy Stream not finished")
                }
            }
        } catch (e: Exception) {
            Logger.log("Internal problem: $e")
            Logger.log(e)
            if (!stop) {
                chunk.transferFailed(errorMessage)
            }
        } finally {
            close()
        }
    }

    private fun close() {
        if (closed) return
        closeImpl()
        closed = true
    }

    fun stop() {
        stop = true
        chunk = null as Segment
        this.t?.interrupt()
    }

    private fun copyStream1(): Boolean {
        Logger.log("Receiving by copyStream1")
        try {
            while (!stop) {
                chunk.chunkListener!!.synchronize()
                val rem = chunk.length - chunk.downloaded
                if (rem == 0L) {
                    if (this is HttpChannel) {
                        if ((this as HttpChannel).isFinished) {
                            close()
                        }
                    } else {
                        close()
                    }
                    if (chunk.transferComplete()) {
                        Logger.log("$chunk complete and closing ${chunk.downloaded} ${chunk.length}")
                        return true
                    }
                }
                if (stop) return false

                val diff = (if (rem > buf.size) buf.size else rem).toInt()
                val x = `in`!!.read(buf, 0, diff)
                if (stop) return false
                if (x == -1) {
                    Logger.log("Unexpected eof")
                    throw Exception("Unexpected eof - downloaded: ${chunk.downloaded} expected: ${chunk.length}")
                }
                chunk.outStream!!.write(buf, 0, x)
                if (stop) return false
                chunk.downloaded = chunk.downloaded + x
                chunk.transferring()
            }
            return false
        } catch (e: Exception) {
            Logger.log(e)
            return false
        } finally {
            close()
        }
    }

    private fun copyStream2(): Boolean {
        Logger.log("Receiving by copyStream2")
        try {
            while (!stop) {
                chunk.chunkListener!!.synchronize()
                val x = `in`!!.read(buf, 0, buf.size)
                if (stop) return false
                if (x == -1) {
                    chunk.transferComplete()
                    return true
                }
                chunk.outStream!!.write(buf, 0, x)
                if (stop) return false
                chunk.downloaded = chunk.downloaded + x
                chunk.transferring()
            }
            return false
        } catch (e: Exception) {
            Logger.log(e)
            return false
        } finally {
            close()
        }
    }

}
