package xdman.network

import java.io.IOException
import java.io.InputStream

class FixedRangeInputStream(private val baseStream: InputStream, private var rem: Long) : InputStream() {

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        var length = len
        if (rem == 0L) {
            return -1
        }
        if (rem > 0 && length > rem) {
            length = rem.toInt()
        }
        val x = baseStream.read(b, off, length)
        if (x == -1) {
            if (rem > 0) {
                throw IOException("Unexpected eof")
            } else {
                return -1
            }
        }
        if (rem > 0) {
            rem -= x
        }
        return x
    }

    @Throws(IOException::class)
    override fun read(): Int {
        if (rem == 0L) {
            return -1
        }
        val x = baseStream.read()
        if (x == -1) {
            if (rem > 0) {
                throw IOException("Unexpected eof")
            } else {
                return -1
            }
        }
        if (rem > 0) {
            rem--
        }
        return x
    }

    @Throws(IOException::class)
    override fun close() {
        baseStream.close()
    }

    fun isStreamFinished(): Boolean {
        return rem == 0L
    }
}
