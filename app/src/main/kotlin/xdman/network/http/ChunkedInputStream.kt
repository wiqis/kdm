package xdman.network.http

import java.io.IOException
import java.io.InputStream

class ChunkedInputStream(private val `in`: InputStream) : InputStream() {
    private var buffer = StringBuffer(16)
    private var state = CHUNK_LEN
    private var chunkSize = 0
    private var pos = 0
    private var eof = false
    private var closed = false

    @Throws(IOException::class)
    override fun read(): Int {
        if (this.closed) {
            throw IOException("Attempted read from closed stream.")
        }
        if (this.eof) {
            return -1
        }
        if (state != CHUNK_DATA) {
            nextChunk()
            if (this.eof) {
                return -1
            }
        }
        val b = `in`.read()
        if (b != -1) {
            pos++
            if (pos >= chunkSize) {
                state = CHUNK_CRLF
            }
        }
        return b
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (closed) {
            throw IOException("Attempted read from closed stream.")
        }

        if (eof) {
            return -1
        }
        if (state != CHUNK_DATA) {
            nextChunk()
            if (eof) {
                return -1
            }
        }
        val readLen = minOf(len, chunkSize - pos)
        val bytesRead = `in`.read(b, off, readLen)
        if (bytesRead != -1) {
            pos += bytesRead
            if (pos >= chunkSize) {
                state = CHUNK_CRLF
            }
            return bytesRead
        } else {
            eof = true
            throw IllegalArgumentException("Truncated chunk ( expected size: $chunkSize; actual size: $pos)")
        }
    }

    @Throws(IOException::class)
    override fun read(b: ByteArray): Int {
        return read(b, 0, b.size)
    }

    @Throws(IOException::class)
    private fun nextChunk() {
        chunkSize = getChunkSize()
        if (chunkSize < 0) {
            throw IllegalArgumentException("Negative chunk size")
        }
        state = CHUNK_DATA
        pos = 0
        if (chunkSize == 0) {
            eof = true
            parseTrailerHeaders()
        }
    }

    @Throws(IOException::class)
    private fun getChunkSize(): Int {
        if (state == CHUNK_CRLF) {
            this.buffer = StringBuffer()
            var i = readLine(this.`in`, this.buffer)
            if (i == -1) {
                return 0
            }
            if (this.buffer.length != 0) {
                throw IllegalArgumentException("Unexpected content at the end of chunk")
            }
            state = CHUNK_LEN
        }
        if (state == CHUNK_LEN) {
            this.buffer = StringBuffer()
            val i = readLine(this.`in`, this.buffer)
            if (i == -1) {
                return 0
            }
            var separator = this.buffer.toString().indexOf(';')
            if (separator < 0) {
                separator = this.buffer.length
            }
            try {
                return this.buffer.substring(0, separator).trim().toInt(16)
            } catch (e: NumberFormatException) {
                throw IllegalArgumentException("Bad chunk header")
            }
        }
        throw IllegalStateException("Inconsistent codec state")
    }

    @Throws(IOException::class)
    private fun parseTrailerHeaders() {
        while (true) {
            val buf = StringBuffer()
            val i = readLine(`in`, buf)
            if (i == -1) break
            if (buf.length < 1) break
        }
    }

    @Throws(IOException::class)
    override fun close() {
        if (!closed) {
            try {
                if (!eof) {
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (read(buffer) >= 0) {
                    }
                }
            } finally {
                eof = true
                closed = true
            }
        }
    }

    companion object {
        private const val CHUNK_LEN = 1
        private const val CHUNK_DATA = 2
        private const val CHUNK_CRLF = 3
        private const val BUFFER_SIZE = 2048

        @JvmStatic
        @Throws(IOException::class)
        fun readLine(`in`: InputStream, buf: StringBuffer): Int {
            var gotCR = false
            while (true) {
                val x = `in`.read()
                if (x == -1) return if (buf.length > 0) buf.length else -1
                if (x == '\n'.code) {
                    if (gotCR) {
                        return buf.length
                    }
                }
                if (x == '\r'.code) {
                    gotCR = true
                } else {
                    gotCR = false
                }
                if (x != '\r'.code) buf.append(x.toChar())
            }
        }
    }
}
