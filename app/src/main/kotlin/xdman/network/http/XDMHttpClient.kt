package xdman.network.http

import xdman.network.*
import xdman.util.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket

class XDMHttpClient(url: String) : HttpClient() {
    private var _url: ParsedURL = ParsedURL.parse(url)!!
    private var socket: Socket? = null
    private var statusLine: String? = null
    private var length: Long = -1
    private var `in`: FixedRangeInputStream? = null
    private var keepAliveSupported = false
    private var closed = false

    fun isFinished(): Boolean {
        try {
            return `in`!!.isStreamFinished() && keepAliveSupported
        } catch (_: Exception) {
        }
        return false
    }

    override fun dispose() {
        if (closed) return
        closed = true
        try {
            if (`in`!!.isStreamFinished() && keepAliveSupported) {
                releaseSocket()
                return
            }
        } catch (_: Exception) {
        }
        try {
            this.socket!!.close()
        } catch (_: Exception) {
        }
    }

    @get:Throws(IOException::class)
    override val inputStream: InputStream
        get() = `in`!!

    @Throws(IOException::class)
    override fun connect() {
        try {
            val port = _url.port
            val portStr = if (port == 80 || port == 443) "" else ":$port"
            requestHeaders.setValue("host", _url.host + portStr)
            var sock = KeepAliveConnectionCache.getInstance().getReusableSocket(_url.host, _url.port)
            var reusing = false
            if (sock == null) {
                Logger.log("Creating new socket")
                this.socket = createSocket()
            } else {
                reusing = true
                Logger.log("Reusing existing socket")
                this.socket = sock
            }
            val sockOut: OutputStream = socket!!.getOutputStream()
            val sockIn: InputStream = socket!!.getInputStream()
            val reqLine = "GET " + _url.pathAndQuery + " HTTP/1.1"
            val reqBuf = StringBuffer()
            reqBuf.append("$reqLine\r\n")
            requestHeaders.appendToBuffer(reqBuf)
            reqBuf.append("\r\n")

            Logger.log("Sending request:\n$reqBuf")

            sockOut.write(StringUtils.getBytes(reqBuf))
            sockOut.flush()
            statusLine = NetUtils.readLine(sockIn)

            val arr = statusLine!!.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            this.statusCode = arr[1].trim { it <= ' ' }.toInt()
            this.statusMessage = if (arr.size > 2) arr[2].trim { it <= ' ' } else ""

            Logger.log(statusLine)

            responseHeaders.loadFromStream(sockIn)
            length = NetUtils.getContentLength(responseHeaders)
            val b2 = StringBuffer()
            responseHeaders.appendToBuffer(b2)
            Logger.log(b2)

            `in` = FixedRangeInputStream(NetUtils.getInputStream(responseHeaders, socket!!.getInputStream()), length)

            if (reusing) {
                Logger.log("Socket reuse successfull")
            }

            keepAliveSupported = !"close".equals(responseHeaders.getValue("connection"), ignoreCase = true)

        } catch (e: HostUnreachableException) {
            e.printStackTrace()
            throw NetworkException("Unable to connect to server")
        } catch (e: Exception) {
            e.printStackTrace()
            throw NetworkException(e.message ?: "Unknown error")
        }
    }

    private fun releaseSocket() {
        Logger.log("Releasing socket for reuse")
        KeepAliveConnectionCache.getInstance().putSocket(socket, _url.host, _url.port)
    }

    @Throws(IOException::class)
    private fun createSocket(): Socket {
        var socket = SocketFactory.createSocket(_url.host, _url.port)
        if (_url.protocol.equals("https", ignoreCase = true)) {
            socket = SocketFactory.wrapSSL(socket, _url.host, _url.port)
        }
        return socket
    }

    @get:Throws(IOException::class)
    override val contentLength: Long
        get() = length

    override val host: String
        get() = _url.host + ":" + _url.port
}
