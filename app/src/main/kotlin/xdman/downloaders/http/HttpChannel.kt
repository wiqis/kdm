package xdman.downloaders.http

import xdman.XDMConstants
import xdman.downloaders.AbstractChannel
import xdman.downloaders.Segment
import xdman.network.ProxyResolver
import xdman.network.http.*
import xdman.util.Logger
import xdman.util.XDMUtils
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

open class HttpChannel : AbstractChannel {
    @JvmField
    var url: String
    @JvmField
    var headers: HeaderCollection? = null
    @JvmField
    var hc: HttpClient? = null
    @JvmField
    var inp: InputStream? = null
    @JvmField
    var javaClientRequired: Boolean
    @JvmField
    var firstLength: Long = 0
    @JvmField
    var totalLength: Long = 0
    @JvmField
    var redirected: Boolean = false
    @JvmField
    var redirectUrl: String? = null

    constructor(chunk: Segment, url: String, headers: HeaderCollection?, totalLength: Long, javaClientRequired: Boolean) : super(chunk) {
        this.url = url
        this.headers = headers
        this.totalLength = totalLength
        this.javaClientRequired = javaClientRequired
    }

    override fun connectImpl(): Boolean {
        var sleepInterval = 0
        var isRedirect: Boolean
        if (stop) {
            closeImpl()
            return false
        }

        if ("HLS" != chunk!!.tag) {
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
        } else {
            try {
                chunk!!.reopenStream()
                chunk!!.resetStream()
                chunk!!.downloaded = 0
            } catch (e: IOException) {
                Logger.log("Stream rest failed")
                Logger.log(e)
            }
        }
        while (!stop) {
            isRedirect = false
            try {
                Logger.log("Connecting to: $url ${chunk!!.tag}")
                val wp = ProxyResolver.resolve(url)
                if (wp != null) {
                    javaClientRequired = true
                }
                hc = if (javaClientRequired) {
                    JavaHttpClient(url)
                } else {
                    XDMHttpClient(url)
                }
                if (headers != null) {
                    val headerIt = headers!!.getAll()
                    val cookies: MutableList<String> = ArrayList()
                    while (headerIt.hasNext()) {
                        val header = headerIt.next()
                        if (header.name.lowercase(Locale.getDefault()) == "cookie") {
                            cookies.add(header.value)
                            continue
                        }
                        hc!!.addHeader(header.name, header.value)
                    }
                    hc!!.addHeader("Cookie", java.lang.String.join(";", cookies))
                }

                val length = chunk!!.length
                val startOff = chunk!!.startOffset + chunk!!.downloaded
                val endOff = startOff + length - chunk!!.downloaded
                val expectedLength = endOff - startOff

                if (length > 0 && expectedLength > 0) {
                    Logger.log("$chunk requesting:- Range:bytes=$startOff-${endOff - 1}")
                    hc!!.setHeader("Range", "bytes=$startOff-${endOff - 1}")
                } else {
                    hc!!.setHeader("Range", "bytes=0-")
                }

                Logger.log("Initating connection")
                hc!!.connect()

                if (stop) {
                    closeImpl()
                    return false
                }

                val code = hc!!.statusCode
                Logger.log("$chunk: $code")

                if (code in 300..399) {
                    closeImpl()
                    if (totalLength > 0) {
                        errorCode = XDMConstants.ERR_INVALID_RESP
                        Logger.log("$chunk Redirecting twice")
                        return false
                    } else {
                        url = hc!!.getResponseHeader("location") ?: ""
                        Logger.log("$chunk location: $url")
                        if (!url.startsWith("http")) {
                            url = if (!url.startsWith("/")) "/$url" else url
                            url = "http://" + hc!!.host + url
                        }
                        url = url.replace(" ", "%20")
                        isRedirect = true
                        redirected = true
                        redirectUrl = url
                        throw Exception("Redirecting to: $url")
                    }
                }

                if (code != 200 && code != 206 && code != 416 && code != 413 && code != 401 && code != 408 && code != 407 && code != 503) {
                    errorCode = XDMConstants.ERR_INVALID_RESP
                    closeImpl()
                    return false
                }

                if (code == 407 || code == 401) {
                    if (javaClientRequired) {
                        Logger.log("asking for password")
                        val proxy = code == 407
                        if (!chunk!!.promptCredential(getHostName(hc!!.host), proxy)) {
                            errorCode = XDMConstants.ERR_INVALID_RESP
                            closeImpl()
                            return false
                        }
                    }
                    throw JavaClientRequiredException()
                }

                if ("T1" == chunk!!.tag || "T2" == chunk!!.tag) {
                    if ("text/plain" == hc!!.getResponseHeader("content-type")) {
                        val bout = ByteArrayOutputStream()
                        val inStr = hc!!.inputStream
                        Logger.log(inStr)
                        val len = hc!!.contentLength
                        var read = 0
                        Logger.log("reading url of length: $len")
                        while (true) {
                            if (len > 0 && read.toLong() == len) break
                            val x = inStr.read()
                            if (x == -1) {
                                if (len > 0) throw IOException("Unable to read url: unexpected EOF")
                                else break
                            }
                            read++
                            print(x.toChar())
                            bout.write(x)
                        }
                        url = String(bout.toByteArray(), Charset.forName("ASCII"))
                        isRedirect = true
                        throw Exception("Youtube text redirect to: $url")
                    }
                }

                if ((chunk!!.downloaded + chunk!!.startOffset) > 0 && code != 206) {
                    closeImpl()
                    errorCode = XDMConstants.ERR_NO_RESUME
                    return false
                }

                if ("HLS" == chunk!!.tag) {
                    firstLength = -1
                } else {
                    firstLength = hc!!.contentLength
                }

                if (length > 0) {
                    if (firstLength != expectedLength) {
                        Logger.log("$chunk length mismatch: expected: $expectedLength got: $firstLength")
                        errorCode = XDMConstants.ERR_NO_RESUME
                        closeImpl()
                        return false
                    }
                }

                if (hc!!.contentLength > 0 && XDMUtils.getFreeSpace(null) < hc!!.contentLength) {
                    Logger.log("Disk is full")
                    errorCode = XDMConstants.DISK_FAIURE
                    closeImpl()
                    return false
                }

                if (code != 200 && code != 206) {
                    errorCode = XDMConstants.ERR_INVALID_RESP
                    closeImpl()
                    return false
                }

                inp = hc!!.inputStream
                Logger.log("Connection success")
                return true

            } catch (e: JavaClientRequiredException) {
                Logger.log("java client required")
                javaClientRequired = true
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

    override fun getLengthImpl(): Long = firstLength

    override fun closeImpl() {
        hc?.dispose()
    }

    val isFinished: Boolean
        get() = if (hc is XDMHttpClient) (hc as XDMHttpClient).isFinished() else false

    fun isJavaClientRequired(): Boolean = this.javaClientRequired

    fun isRedirected(): Boolean = redirected

    fun getHeader(name: String): String? = hc?.getResponseHeader(name)

    private fun getHostName(hostPort: String): String = hostPort.split(":")[0]
}
