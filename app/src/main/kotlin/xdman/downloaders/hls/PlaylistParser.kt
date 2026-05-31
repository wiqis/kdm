package xdman.downloaders.hls

import xdman.util.FormatUtilities
import xdman.util.Logger
import xdman.util.StringUtils
import xdman.util.XDMUtils
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.net.URI

object PlaylistParser {
    @JvmStatic
    fun parse(file: String, playlistUrl: String): HlsPlaylist? {
        val playlist = HlsPlaylist()
        var keyUrl: String? = null
        var IV: String? = null
        var url: String? = null
        var resolution: String? = null
        var bandwidth: String? = null
        var sMediaSequence: String? = null
        var isMasterPlaylist = false
        var isEncryptedPlaylist = false
        var isEncryptedSegment = false
        var mediaSequence = 0
        var duration = ""
        var totalDuration = 0.0f
        var lastUrl: String? = null
        var hasByteRange = false
        val items = ArrayList<HlsPlaylistItem>()
        try {
            BufferedReader(InputStreamReader(FileInputStream(file))).use { r ->
                if (!XDMUtils.readLineSafe(r).startsWith("#EXTM3U")) {
                    throw IOException("Not a valid HLS manifest")
                }
                var prefixLine = ""
                while (true) {
                    var line = r.readLine() ?: break
                    line = line.trim()
                    if (line.length < 1) continue
                    if (line.endsWith("\\")) {
                        prefixLine = line.substring(0, line.length - 1)
                        continue
                    } else {
                        if (prefixLine.isNotEmpty()) {
                            line = "$prefixLine $line"
                            prefixLine = ""
                        }
                    }

                    if (!line.startsWith("#")) {
                        var segSeq = -1
                        if (sMediaSequence != null) {
                            segSeq = sMediaSequence.toInt()
                            if (mediaSequence == 0) {
                                mediaSequence = segSeq
                            }
                        } else {
                            segSeq = mediaSequence
                        }
                        url = line
                        if (!(hasByteRange && lastUrl != null && url == lastUrl)) {
                            val item = HlsPlaylistItem(
                                getAbsUrl(url, playlistUrl),
                                if (isEncryptedSegment) getAbsUrl(keyUrl, playlistUrl) else null,
                                if (isEncryptedSegment) getIV(IV, mediaSequence) else null,
                                resolution, bandwidth, duration
                            )
                            items.add(item)
                            mediaSequence++
                        }

                        url = null
                        sMediaSequence = null
                        resolution = null
                        bandwidth = null
                        try {
                            if (!StringUtils.isNullOrEmptyOrBlank(duration)) {
                                totalDuration += duration.toFloat()
                            }
                        } catch (_: Exception) {
                        }
                        duration = ""
                    } else if (line.startsWith("#EXT")) {
                        when {
                            line.startsWith("#EXT-X-STREAM-INF:") -> {
                                isMasterPlaylist = true
                                val attribSet = getKeyString(line)
                                if (!StringUtils.isNullOrEmptyOrBlank(attribSet)) {
                                    val attrs = attribSet!!.split(",").toTypedArray()
                                    resolution = getAttrValue(attrs, "RESOLUTION")
                                    resolution = FormatUtilities.getResolution(resolution)
                                    bandwidth = getAttrValue(attrs, "BANDWIDTH")
                                    try {
                                        val bw = bandwidth!!.toInt()
                                        bandwidth = "${bw / 1000}k"
                                    } catch (_: Exception) {
                                        bandwidth = ""
                                    }
                                }
                            }
                            line.startsWith("#EXTINF:") -> {
                                val attribSet = getKeyString(line)
                                if (!StringUtils.isNullOrEmptyOrBlank(attribSet)) {
                                    val attrs = attribSet!!.split(",").toTypedArray()
                                    if (attrs.isNotEmpty()) {
                                        val sDuration = attrs[0].trim()
                                        duration = sDuration
                                    }
                                }
                            }
                            line.startsWith("#EXT-X-BYTERANGE:") -> {
                                hasByteRange = true
                                if (isEncryptedPlaylist) {
                                    throw IOException("Encryption is not supported with byte range")
                                }
                            }
                            line.startsWith("#EXT-X-MEDIA-SEQUENCE:") -> {
                                val attribSet = getKeyString(line)
                                if (!StringUtils.isNullOrEmptyOrBlank(attribSet)) {
                                    val attrs = attribSet!!.split(",").toTypedArray()
                                    if (attrs.isNotEmpty()) {
                                        sMediaSequence = attrs[0]
                                    }
                                }
                            }
                            line.startsWith("#EXT-X-KEY:") -> {
                                val attribSet = getKeyString(line)
                                if (!StringUtils.isNullOrEmptyOrBlank(attribSet)) {
                                    val attrs = attribSet!!.split(",").toTypedArray()
                                    if (attrs.isNotEmpty()) {
                                        val method = getAttrValue(attrs, "METHOD")
                                        keyUrl = getAttrValue(attrs, "URI")
                                        if (keyUrl != null) {
                                            keyUrl = keyUrl!!.replace("\"", "")
                                        }
                                        println("Method: $method URI: $keyUrl")
                                        if (method != null) {
                                            if (method == "AES-128" || method == "NONE") {
                                                if (method == "AES-128") {
                                                    isEncryptedPlaylist = true
                                                    isEncryptedSegment = true
                                                    IV = getAttrValue(attrs, "IV")
                                                    val keyFormat = getAttrValue(attrs, "KEYFORMAT")
                                                    if (keyFormat != null && keyFormat != "identity") {
                                                        println("Unsupported encryption method: $method/keyformat: $keyFormat")
                                                        return null
                                                    }
                                                } else {
                                                    isEncryptedSegment = false
                                                    println("Non encrypted")
                                                }
                                            } else {
                                                println("Unsupported encryption method: $method")
                                                return null
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            playlist.items = items
            playlist.isEncrypted = isEncryptedPlaylist
            playlist.isMaster = isMasterPlaylist
            playlist.duration = totalDuration
            return playlist
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    private fun getKeyString(line: String): String? {
        val index = line.indexOf(':')
        return if (index < 0) null else line.substring(index + 1)
    }

    private fun getValue(line: String): String? {
        val index = line.indexOf('=')
        return if (index < 0) null else line.substring(index + 1)
    }

    private fun getAttrValue(attrs: Array<String>, name: String): String? {
        for (attr in attrs) {
            val attrib = attr.trim()
            if (attrib.startsWith(name)) {
                return getValue(attr)
            }
        }
        return null
    }

    private fun getAbsUrl(chunkUrl: String?, playlistUrl: String): String? {
        return buildURL(playlistUrl, chunkUrl)
    }

    private fun buildURL(playlistUrl: String, segmentUrl: String?): String? {
        if (segmentUrl == null) return null
        return try {
            if (!(segmentUrl.startsWith("http://") || segmentUrl.startsWith("https://"))) {
                val uri = URI(playlistUrl)
                uri.resolve(segmentUrl).normalize().toString()
            } else {
                segmentUrl
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Logger.log(e)
            null
        }
    }

    private fun getIV(iv: String?, sequence: Int): String {
        return if (StringUtils.isNullOrEmptyOrBlank(iv)) {
            Integer.toHexString(sequence)
        } else {
            iv!!
        }
    }
}
