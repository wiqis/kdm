package xdman.downloaders.metadata

import xdman.Config
import xdman.XDMConstants
import xdman.network.http.HeaderCollection
import xdman.network.http.HttpHeader
import xdman.util.Logger
import xdman.util.StringUtils
import java.io.*
import java.util.*

open class HttpMetadata {
    @JvmField
    var id: String

    @JvmField
    var url: String = ""

    @JvmField
    var headers: HeaderCollection

    private var size: Long = 0
    private var ydlUrl: String? = null

    constructor() {
        id = UUID.randomUUID().toString()
        headers = HeaderCollection()
    }

    protected constructor(id: String) {
        this.id = id
        headers = HeaderCollection()
    }

    open fun derive(): HttpMetadata {
        Logger.log("derive normal metadata")
        val md = HttpMetadata()
        md.headers = headers
        md.url = url
        md.size = size
        return md
    }

    open val type: Int
        get() = if (url.startsWith("ftp")) XDMConstants.FTP else XDMConstants.HTTP

    fun getUrl(): String = url
    fun setUrl(url: String) { this.url = url }

    fun getHeaders(): HeaderCollection = headers
    fun setHeaders(headers: HeaderCollection) { this.headers = headers }

    fun getId(): String = id

    fun getSize(): Long = size
    fun setSize(size: Long) { this.size = size }

    fun getYdlUrl(): String? = ydlUrl
    fun setYdlUrl(ydlUrl: String?) { this.ydlUrl = ydlUrl }

    fun save() {
        var fw: FileOutputStream? = null
        try {
            val sb = StringBuilder()
            if (url == null) throw NullPointerException("url is null")
            sb.append("type: ${type}\n")
            sb.append("url: $url\n")
            sb.append("size: $size\n")
            if (headers != null) {
                val headerIterator = headers.getAll()
                while (headerIterator.hasNext()) {
                    val header = headerIterator.next()
                    sb.append("header: ${header.name}:${header.value}\n")
                }
            }
            if (type == XDMConstants.HDS) {
                sb.append("bitrate: ${(this as HdsMetadata).bitRate}\n")
            }
            if (type == XDMConstants.DASH) {
                sb.append("url2: ${(this as DashMetadata).url2}\n")
                sb.append("len1: ${(this as DashMetadata).len1}\n")
                sb.append("len2: ${(this as DashMetadata).len2}\n")
                if ((this as DashMetadata).headers2 != null) {
                    val headerIterator = (this as DashMetadata).headers2!!.getAll()
                    while (headerIterator.hasNext()) {
                        val header = headerIterator.next()
                        sb.append("header2: ${header.name}:${header.value}\n")
                    }
                }
            }
            if (!StringUtils.isNullOrEmptyOrBlank(ydlUrl)) {
                sb.append("ydlUrl: $ydlUrl")
            }

            val metadataFolder = File(Config.getInstance().metadataFolder)
            if (!metadataFolder.exists()) {
                metadataFolder.mkdirs()
            }
            val file = File(metadataFolder, id)
            fw = FileOutputStream(file)
            fw.write(sb.toString().toByteArray())
            fw.close()
        } catch (e: Exception) {
            Logger.log(e)
            if (fw != null) {
                try {
                    fw.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    companion object {
        @JvmStatic
        fun load(id: String): HttpMetadata? {
            Logger.log("loading metadata: $id")
            var br: BufferedReader? = null
            var metadata: HttpMetadata? = null
            var type: Int
            try {
                br = BufferedReader(FileReader(File(Config.getInstance().metadataFolder, id)))
                var ln = br.readLine() ?: run {
                    Logger.log("invalid metadata, file is empty")
                    return@load null
                }
                var index = ln.indexOf(":")
                if (index < 0) {
                    Logger.log("invalid metadata file starting with: $ln")
                    return@load null
                }
                var key = ln.substring(0, index).trim().lowercase()
                var `val` = ln.substring(index + 1).trim()
                if (key == "type") {
                    type = `val`.toInt()
                    metadata = when (type) {
                        XDMConstants.HTTP, XDMConstants.FTP -> HttpMetadata(id)
                        XDMConstants.HLS -> HlsMetadata(id)
                        XDMConstants.HDS -> HdsMetadata(id)
                        XDMConstants.DASH -> DashMetadata(id)
                        else -> {
                            Logger.log("invalid metadata file starting with: $ln")
                            return@load null
                        }
                    }
                } else {
                    Logger.log("invalid metadata file starting with: $ln")
                    return@load null
                }
                while (true) {
                    ln = br.readLine() ?: break
                    index = ln.indexOf(":")
                    if (index < 0) continue
                    key = ln.substring(0, index).trim().lowercase()
                    `val` = ln.substring(index + 1).trim()
                    when (key) {
                        "url" -> metadata!!.setUrl(`val`)
                        "size" -> metadata!!.setSize(`val`.toLong())
                        "header" -> {
                            val index2 = `val`.indexOf(":")
                            if (index2 >= 0) {
                                val key1 = `val`.substring(0, index2).trim()
                                val val1 = `val`.substring(index2 + 1).trim()
                                metadata!!.headers.addHeader(key1, val1)
                            }
                        }
                        "header2" -> {
                            val index2 = `val`.indexOf(":")
                            if (index2 >= 0) {
                                val key1 = `val`.substring(0, index2).trim()
                                val val1 = `val`.substring(index2 + 1).trim()
                                (metadata as DashMetadata).headers2!!.addHeader(key1, val1)
                            }
                        }
                        "url2" -> (metadata as DashMetadata).url2 = `val`
                        "len1" -> (metadata as DashMetadata).len1 = `val`.toLong()
                        "len2" -> (metadata as DashMetadata).len2 = `val`.toLong()
                        "bitrate" -> (metadata as HdsMetadata).bitRate = `val`.toInt()
                        "ydlurl" -> {
                            Logger.log("ydurl: $`val`")
                            metadata!!.setYdlUrl(`val`)
                        }
                    }
                }
                br.close()
            } catch (e: Exception) {
                Logger.log(e)
            } finally {
                if (br != null) {
                    try {
                        br.close()
                    } catch (_: Exception) {
                    }
                }
            }
            return metadata
        }
    }
}
