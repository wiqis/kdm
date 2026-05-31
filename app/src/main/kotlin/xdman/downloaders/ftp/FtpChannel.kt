package xdman.downloaders.ftp

import xdman.XDMConstants
import xdman.downloaders.AbstractChannel
import xdman.downloaders.Segment
import xdman.network.ftp.FtpClient
import xdman.network.http.JavaClientRequiredException
import xdman.util.Logger
import xdman.util.XDMUtils
import java.io.InputStream
import java.io.IOException
import java.net.Authenticator
import java.net.PasswordAuthentication

class FtpChannel : AbstractChannel {
    private var url: String
    private var hc: FtpClient? = null
    private var inp: InputStream? = null
    private var redirected = false
    private var length: Long = 0

    constructor(chunk: Segment, url: String) : super(chunk) {
        this.url = url
    }

    override fun connectImpl(): Boolean {
        var sleepInterval = 0
        var isRedirect: Boolean
        if (stop) {
            closeImpl()
            return false
        }

        if (chunk!!.length < 0 && chunk!!.downloaded > 0) {
            errorCode = XDMConstants.ERR_NO_RESUME
            closeImpl()
            Logger.log("server does not support resuming")
            return false
        }
        try {
            chunk!!.reopenStream()
        } catch (e: IOException) {
            Logger.log(e)
            closeImpl()
            errorCode = XDMConstants.ERR_NO_RESUME
            return false
        }

        var passwd = PasswordAuthentication("anonymous", "anonymous".toCharArray())

        while (!stop) {
            isRedirect = false
            try {
                Logger.log("ftp Connecting to: $url ${chunk!!.tag} offset ${chunk!!.startOffset + chunk!!.downloaded}")
                hc = FtpClient(url)

                val startOff = chunk!!.startOffset + chunk!!.downloaded
                if (startOff > 0) {
                    hc!!.offset = startOff
                }

                hc!!.user = passwd.userName
                hc!!.password = String(passwd.password)
                hc!!.connect()

                if (stop) {
                    closeImpl()
                    return false
                }

                val code = hc!!.statusCode
                Logger.log("$chunk: $code")

                if (code != 200 && code != 206 && code != 416 && code != 413 && code != 401 && code != 408 && code != 407 && code != 503) {
                    errorCode = XDMConstants.ERR_INVALID_RESP
                    closeImpl()
                    return false
                }

                if (code == 407 || code == 401) {
                    Logger.log("asking for password")
                    val proxy = code == 407
                    passwd = Authenticator.requestPasswordAuthentication(null, hc!!.port, "ftp", "", "ftp") ?: run {
                            if (!chunk!!.promptCredential(hc!!.host!!, proxy)) {
                            errorCode = XDMConstants.ERR_INVALID_RESP
                            closeImpl()
                            return false
                        } else {
                            val pwd = Authenticator.requestPasswordAuthentication(null, hc!!.port, "ftp", "", "ftp")
                            Logger.log("Passwd: $pwd")
                            throw JavaClientRequiredException()
                        }
                    }
                }

                if (stop) {
                    closeImpl()
                    return false
                }

                if ((chunk!!.downloaded + chunk!!.startOffset) > 0 && code != 206) {
                    closeImpl()
                    errorCode = XDMConstants.ERR_NO_RESUME
                    return false
                }

                length = hc!!.contentLength

                if (hc!!.contentLength > 0 && XDMUtils.getFreeSpace(null) < hc!!.contentLength) {
                    Logger.log("Disk is full")
                    errorCode = XDMConstants.DISK_FAIURE
                    closeImpl()
                    return false
                }

                inp = hc!!.inputStream
                Logger.log("Connection success")
                return true

            } catch (e: JavaClientRequiredException) {
                sleepInterval = 0
            } catch (e: Exception) {
                Logger.log(chunk)
                Logger.log(e)
                if (isRedirect) {
                    closeImpl()
                    continue
                }
                sleepInterval = 5000
            }

            closeImpl()
            try {
                Thread.sleep(sleepInterval.toLong())
            } catch (_: Exception) {
            }
        }

        Logger.log("return as $errorCode")
        return false
    }

    override fun getInputStreamImpl(): InputStream? = inp

    override fun getLengthImpl(): Long = length

    override fun closeImpl() {
        hc?.dispose()
    }

    fun isFinished(): Boolean = false

    fun isRedirected(): Boolean = redirected

    fun getRedirectUrl(): String? = null
}
