package xdman.downloaders.metadata.manifests

import xdman.util.Logger
import xdman.util.StringUtils
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI
import java.util.*

class M3U8Manifest(file: String, private var playlistUrl: String) {
    private var duration: Float = 0f
    private val mediaUrls: ArrayList<String> = ArrayList()
    private var masterPlaylist: Boolean = false
    private var encrypted: Boolean = false
    private val mediaProperties: ArrayList<M3U8MediaInfo> = ArrayList()

    init {
        val urlList = parseManifest(file)
        makeMediaUrls(urlList)
    }

    fun getMediaUrls(): ArrayList<String> = mediaUrls
    fun getMediaProperty(index: Int): M3U8MediaInfo = mediaProperties[index]

    @Throws(Exception::class)
    private fun makeMediaUrls(list: ArrayList<String>) {
        var baseUrl = ""
        var uri: URI? = null
        for (i in list.indices) {
            val item = list[i]
            var itemUrl = resolveURL(playlistUrl, item)
            if (itemUrl == null) {
                if (item.startsWith("/")) {
                    if (StringUtils.isNullOrEmpty(baseUrl)) {
                        if (uri == null) uri = URI(this.playlistUrl)
                        baseUrl = uri!!.scheme + "://" + uri!!.host + "" + (if (uri!!.port > 0) ":${uri!!.port}" else "")
                    }
                    itemUrl = baseUrl + item
                } else if (item.startsWith("http://") || item.startsWith("https://")) {
                    itemUrl = item
                } else {
                    val index = this.playlistUrl.lastIndexOf('/')
                    itemUrl = this.playlistUrl.substring(0, index) + "/"
                    itemUrl += item
                }
            }
            mediaUrls.add(itemUrl)
        }
    }

    private fun resolveURL(playlistUrl: String, segmentUrl: String): String? {
        try {
            Logger.log("Manifest Segment parsing ")
            if (!(segmentUrl.startsWith("http://") || segmentUrl.startsWith("https://"))) {
                val uri = URI(playlistUrl)
                val str = uri.resolve(segmentUrl).normalize().toString()
                Logger.log("Manifest Segment parsing: $str")
                return str
            } else {
                return segmentUrl
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.log(e)
        }
        return null
    }

    @Throws(IOException::class)
    private fun parseManifest(file: String): ArrayList<String> {
        val urlList = ArrayList<String>()
        var r: BufferedReader? = null
        try {
            r = BufferedReader(InputStreamReader(FileInputStream(file)))
            var expect = false
            while (true) {
                val line = r.readLine() ?: break
                val highline = line.uppercase().trim()
                if (highline.length < 1) continue

                if (highline.startsWith("#EXT-X-KEY")) {
                    Logger.log("Encrypted segment detected: $line")
                }
                if (expect) {
                    urlList.add(line.trim())
                    expect = false
                }
                if (highline.startsWith("#EXT-X-STREAM-INF")) {
                    masterPlaylist = true
                    expect = true
                    val arr = highline.split(":")
                    if (arr.size > 1) {
                        mediaProperties.add(M3U8MediaInfo.parse(arr[1].trim()))
                    }
                }
                if (highline.startsWith("#EXTINF")) {
                    masterPlaylist = false
                    expect = true
                    try {
                        val arr = highline.split(":")
                        if (arr.size > 1) {
                            mediaProperties.add(M3U8MediaInfo.parse(arr[1].trim()))
                            val str = arr[1].trim().split(",")[0]
                            duration += str.toFloat()
                        }
                    } catch (e: Exception) {
                        Logger.log(e)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.log(e)
            throw IOException("Unable to parse menifest")
        } finally {
            try {
                r?.close()
            } catch (_: Exception) {
            }
        }
        return urlList
    }

    fun getDuration(): Float = duration
    fun isMasterPlaylist(): Boolean = masterPlaylist
    fun isEncrypted(): Boolean = encrypted

    class M3U8MediaInfo {
        var resolution: String = ""
        var bandwidth: String = ""

        override fun toString(): String = "bw: $bandwidth res: $resolution"

        companion object {
            fun parse(str: String): M3U8MediaInfo {
                val arr = str.split(",")
                val info = M3U8MediaInfo()
                for (j in arr.indices) {
                    try {
                        val ss = arr[j].uppercase()
                        if (ss.startsWith("RESOLUTION")) {
                            if (ss.contains("=")) info.resolution = ss.split("=")[1].trim()
                        }
                        if (ss.startsWith("BANDWIDTH")) {
                            if (ss.contains("=")) {
                                info.bandwidth = ss.split("=")[1].trim()
                                try {
                                    val bps = info.bandwidth.toInt()
                                    info.bandwidth = "${bps / 1000} kbps"
                                } catch (_: Exception) {
                                }
                            }
                        }
                    } catch (_: Exception) {
                    }
                }
                return info
            }
        }
    }
}
