package xdman.network.ftp

import org.apache.commons.net.ftp.FTPClient as ApacheFTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import xdman.util.Logger
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.net.URISyntaxException

class FtpClient(private var url: String) {
    var offset: Long = 0
    var statusCode: Int = 0
    var user: String? = null
    var password: String? = null

    val statusMessage: String?
        get() = _statusMessage

    val contentLength: Long
        get() = length

    val inputStream: InputStream
        @Throws(IOException::class)
        get() = fc!!.retrieveFileStream(file)

    val host: String?
        get() = _host

    val port: Int
        get() = fc!!.passivePort

    private var _statusMessage: String? = null
    private var fc: ApacheFTPClient? = null
    private var dir: String? = null
    private var file: String? = null
    private var _host: String? = null
    private var path: String? = null
    private var length: Long = 0

    @Throws(IOException::class)
    fun connect() {
        Logger.log("Initiate ftp: $url")
        val ftpuri: URI = try {
            URI(url)
        } catch (e: URISyntaxException) {
            Logger.log(e)
            throw IOException(e)
        }
        _host = ftpuri.host
        val port = ftpuri.port
        path = ftpuri.path
        Logger.log("Path: $path")
        getPath()
        fc = ApacheFTPClient()
        Logger.log("Connecting ftp: $_host:$port")
        if (port > 0)
            fc!!.connect(_host, port)
        else
            fc!!.connect(_host)
        Logger.log("Loggin in")
        fc!!.login(user, password)
        var reply = fc!!.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            this.statusCode = 401
            this._statusMessage = fc!!.replyString
            fc!!.disconnect()
            return
        }
        Logger.log("Going binary")
        fc!!.setFileType(ApacheFTPClient.BINARY_FILE_TYPE)
        reply = fc!!.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            statusCode = 403
            _statusMessage = fc!!.replyString
            fc!!.disconnect()
            return
        }
        Logger.log("Going passive")
        fc!!.enterLocalPassiveMode()
        Logger.log("cd $dir")
        fc!!.changeWorkingDirectory(dir)
        reply = fc!!.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            statusCode = 403
            _statusMessage = fc!!.replyString
            fc!!.disconnect()
            return
        }

        Logger.log("Listing files")
        val files = fc!!.listFiles(dir)
        reply = fc!!.replyCode
        if (!FTPReply.isPositiveCompletion(reply)) {
            statusCode = 403
            _statusMessage = fc!!.replyString
            fc!!.disconnect()
            return
        }
        for (i in files.indices) {
            val f = files[i]
            if (f.name == file) {
                this.length = f.size
                Logger.log("Length retrived: $length")
                break
            }
        }
        this.statusCode = 200

        if (offset > 0 && length > 0) {
            Logger.log("Setting offset")
            fc!!.restartOffset = offset
            if (!FTPReply.isPositiveCompletion(reply)) {
                throw IOException(fc!!.replyString)
            }
            this.length -= offset
            Logger.log("Length after seek: $length")
            this.statusCode = 206
        }
    }

    @Throws(IOException::class)
    fun close() {
        fc!!.disconnect()
    }

    private fun getPath() {
        val pos = path!!.lastIndexOf("/")
        if (pos < 0) return
        dir = path!!.substring(0, pos)
        if (dir!!.length < 1) dir = "/"
        if (pos == path!!.length - 1) return
        if (pos < path!!.length - 1) {
            file = path!!.substring(pos + 1)
        }
    }

    fun dispose() {
        try {
            fc!!.disconnect()
        } catch (e: Exception) {
            Logger.log(e)
        }
    }
}
