package xdman.downloaders.hls

import xdman.XDMConstants
import xdman.downloaders.Segment
import xdman.downloaders.http.HttpChannel
import xdman.network.ProxyResolver
import xdman.network.http.*
import xdman.util.Logger
import xdman.util.XDMUtils
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.IOException
import java.math.BigInteger
import java.security.spec.AlgorithmParameterSpec
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class EncryptedHlsChannel : HttpChannel {
    private var keyUrl: String? = null
    private var mediaUrl: String? = null
    private var source: HlsEncryptedSouce? = null

    constructor(
        chunk: Segment, url: String, headers: HeaderCollection?, totalLength: Long,
        javaClientRequired: Boolean, source: HlsEncryptedSouce, keyurl: String?
    ) : super(chunk, url, headers, totalLength, javaClientRequired) {
        this.source = source
        this.url = url
        this.mediaUrl = url
        this.keyUrl = keyurl
    }

    override fun connectImpl(): Boolean {
        var sleepInterval = 0
        var isRedirect = false
        if (stop) {
            closeImpl()
            return false
        }

        try {
            chunk!!.reopenStream()
            chunk!!.resetStream()
            chunk!!.downloaded = 0
        } catch (e: IOException) {
            Logger.log("Stream rest failed")
            Logger.log(e)
        }

        var isKey = !source!!.hasKey(keyUrl)
        if (isKey) {
            Logger.log("Retrieving key")
            url = keyUrl!!
        }

        while (!stop) {
            isRedirect = false
            try {
                Logger.log("Connecting to: $url ${chunk!!.tag}")
                val wp = ProxyResolver.resolve(url)
                if (wp != null) {
                    javaClientRequired = true
                }

                if (javaClientRequired) {
                    hc = JavaHttpClient(url)
                } else {
                    hc = XDMHttpClient(url)
                }

                if (headers != null) {
                    val headerIt = headers!!.getAll()
                    while (headerIt.hasNext()) {
                        val header = headerIt.next()
                        hc!!.addHeader(header.name, header.value)
                    }
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
                        url = hc!!.getResponseHeader("location")!!
                        Logger.log("$chunk location: $url")
                        if (!url.startsWith("http")) {
                            if (!url.startsWith("/")) {
                                url = "/$url"
                            }
                            url = "http://${hc!!.host}$url"
                        }
                        url = url.replace(" ", "%20")
                        isRedirect = true
                        redirected = true
                        redirectUrl = url
                        throw Exception("Redirecting to: $url")
                    }
                }

                if (code != 200 && code != 206 && code != 416 && code != 413 && code != 401 && code != 408
                    && code != 407 && code != 503
                ) {
                    errorCode = XDMConstants.ERR_INVALID_RESP
                    closeImpl()
                    return false
                }

                if (code == 407 || code == 401) {
                    if (javaClientRequired) {
                        Logger.log("asking for password")
                        val proxy = code == 407
                        if (!chunk!!.promptCredential(hc!!.host, proxy)) {
                            errorCode = XDMConstants.ERR_INVALID_RESP
                            closeImpl()
                            return false
                        }
                    }
                    throw JavaClientRequiredException()
                }

                if (code == 200 || code == 206) {
                    if (isKey) {
                        val bout = ByteArrayOutputStream()
                        val inStr = hc!!.inputStream
                        println(inStr)
                        val len = hc!!.contentLength
                        var read = 0
                        println("reading url of length: $len")
                        while (true) {
                            if (len > 0 && read.toLong() == len) break
                            val x = inStr!!.read()
                            if (x == -1) {
                                if (len > 0) {
                                    throw IOException("Unable to read url: unexpected EOF")
                                } else {
                                    break
                                }
                            }
                            read++
                            bout.write(x)
                        }
                        val buf = bout.toByteArray()
                        isKey = false
                        this.url = mediaUrl!!
                        source!!.setKey(keyUrl!!, buf)
                        isRedirect = true
                        throw Exception("Youtube text redirect to: $url")
                    }
                }

                firstLength = -1

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
                val ivStr = source!!.getIV(mediaUrl!!)
                val key = source!!.getKey(keyUrl!!)
                try {
                    inp = getCypherStream(inp!!, key!!, getIV(ivStr))
                } catch (e: Exception) {
                    e.printStackTrace()
                    errorCode = XDMConstants.ERR_INVALID_RESP
                    closeImpl()
                    return false
                }
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

    private fun getIV(str: String?): ByteArray {
        var s = str
        if (s != null && s.lowercase().startsWith("0x")) {
            s = s.substring(2)
        }
        val ivData = BigInteger(s, 16).toByteArray()
        val ivDataWithPadding = ByteArray(16)
        val offset = if (ivData.size > 16) ivData.size - 16 else 0
        System.arraycopy(ivData, offset, ivDataWithPadding, ivDataWithPadding.size - ivData.size + offset, ivData.size - offset)
        return ivDataWithPadding
    }

    @Throws(Exception::class)
    private fun getCypherStream(inp: InputStream, key: ByteArray, iv: ByteArray): InputStream {
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val cipherKey = SecretKeySpec(key, "AES")
        val cipherIV: AlgorithmParameterSpec = IvParameterSpec(iv)
        cipher.init(Cipher.DECRYPT_MODE, cipherKey, cipherIV)
        return CipherInputStream(inp, cipher)
    }
}
