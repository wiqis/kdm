package xdman.videoparser

import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import xdman.network.http.HttpHeader
import xdman.util.FormatUtilities
import xdman.util.Logger
import xdman.util.StringUtils
import java.io.InputStream
import java.io.InputStreamReader

object YdlResponse {
    const val DASH_HTTP = 99
    const val HTTP = 98
    const val HLS = 97
    const val HDS = 96

    private const val DASH_VIDEO_ONLY = 23
    private const val DASH_AUDIO_ONLY = 24

    @JvmStatic
    fun parse(`in`: InputStream): ArrayList<YdlVideo> {
        val parser = JSONParser()
        val obj = parser.parse(InputStreamReader(`in`, "utf-8")) as JSONObject
        var entries = obj["entries"] as? JSONArray
        if (entries == null) {
            Logger.log("no playlist entry")
            entries = JSONArray()
            entries.add(obj)
        }
        val playList = ArrayList<YdlVideo>()
        for (i in 0 until entries.size) {
            val jsobj = entries[i] as? JSONObject
            if (jsobj != null) {
                val v = getPlaylistEntry(jsobj)
                if (v != null) {
                    playList.add(v)
                } else {
                    Logger.log("Parsing failed")
                }
            }
        }
        Logger.log("Playlist size: " + playList.size)
        return playList
    }

    @JvmStatic
    fun getPlaylistEntry(obj: JSONObject): YdlVideo? {
        if (obj == null) {
            return null
        }
        val formatList = ArrayList<YdlFormat>()
        val formats = obj["formats"] as? JSONArray
        if (formats != null) {
            for (i in 0 until formats.size) {
                Logger.log("Parsing format info")
                val formatObj = formats[i] as JSONObject
                val protocol = getString(formatObj["protocol"])
                val format = YdlFormat()
                format.protocol = protocol
                format.url = getString(formatObj["url"])
                format.acodec = getString(formatObj["acodec"])
                format.vcodec = getString(formatObj["vcodec"])
                format.width = getInt(formatObj["width"])
                format.height = getInt(formatObj["height"])
                format.ext = getString(formatObj["ext"])
                if ("mpd".equals(format.ext, ignoreCase = true)) {
                    continue
                }
                format.formatNote = getString(formatObj["format_note"])
                format.format = getString(formatObj["format"])
                val sabr = "${formatObj["abr"]}"
                format.abr = try {
                    sabr.toInt()
                } catch (_: Exception) {
                    -1
                }

                val jsHeaders = formatObj["http_headers"] as? JSONObject
                if (jsHeaders != null) {
                    format.headers = ArrayList()
                    for (keyObj in jsHeaders.keys) {
                        val key = keyObj as String
                        val value = jsHeaders[key] as String
                        format.headers!!.add(HttpHeader(key, value))
                    }
                }
                if (protocol == "http_dash_segments") {
                    val baseUrl = formatObj["fragment_base_url"] as? String
                    val fragmentArr = formatObj["fragments"] as JSONArray
                    val fragments = arrayOfNulls<String>(fragmentArr.size)
                    for (j in fragments.indices) {
                        val frag = fragmentArr[j] as JSONObject
                        val url = frag["url"] as? String
                        fragments[j] = url ?: "$baseUrl${frag["path"] as String}"
                    }
                    @Suppress("UNCHECKED_CAST")
                    format.fragments = fragments as Array<String>
                }
                formatList.add(format)
            }
        } else {
            val url = getString(obj["url"])
            if (url != null) {
                val format = YdlFormat()
                format.protocol = getString(obj["protocol"])
                format.url = url
                format.acodec = getString(obj["acodec"])
                format.vcodec = getString(obj["vcodec"])
                try {
                    format.width = getInt(obj["width"])
                } catch (_: Exception) {
                    format.width = -1
                }
                try {
                    format.height = getInt(obj["height"])
                } catch (_: Exception) {
                    format.width = -1
                }

                format.ext = getString(obj["ext"])
                format.formatNote = getString(obj["format_note"])
                format.format = getString(obj["format"])
                val sabr = "${obj["abr"]}"
                format.abr = try {
                    sabr.toInt()
                } catch (_: Exception) {
                    -1
                }
                formatList.add(format)
            }
        }

        Logger.log("Format list count: " + formatList.size)

        val mediaList = ArrayList<YdlMediaFormat>()

        for (i in formatList.indices) {
            val fmt = formatList[i]
            if (fmt.protocol == "http_dash_segments") {
                continue
            }
            val type = getVideoType(fmt)

            if (type == DASH_VIDEO_ONLY) {
                for (j in formatList.indices) {
                    val fmt2 = formatList[j]
                    val type2 = getVideoType(fmt2)
                    if (type2 == DASH_AUDIO_ONLY) {
                        val media = YdlMediaFormat()
                        media.type = DASH_HTTP

                        if (fmt.protocol == fmt2.protocol) {
                            media.audioSegments = arrayOf(fmt2.url!!)
                            media.abr = fmt2.abr
                            media.videoSegments = arrayOf(fmt.url!!)
                            if (fmt.headers != null) {
                                media.headers.addAll(fmt.headers!!)
                            }
                            if (fmt2.headers != null) {
                                media.headers2.addAll(fmt2.headers!!)
                            }

                            if ("${fmt.ext}" == "${fmt2.ext}"
                                || ("${fmt.ext}" == "mp4" && "${fmt2.ext}" == "m4a")
                            ) {
                                media.ext = fmt.ext
                            } else {
                                media.ext = "mkv"
                            }
                            media.width = fmt.width
                            media.height = fmt.height
                            media.format = createFormat(
                                media.ext ?: "", fmt.format, fmt2.format, fmt2.acodec ?: "", fmt.vcodec ?: "",
                                fmt.width, fmt.height, fmt2.abr
                            )
                            println("${media.format} ${media.url}")
                            checkAndAddMedia(media, mediaList)
                        }
                    }
                }
            } else if (type != DASH_AUDIO_ONLY) {
                val media = YdlMediaFormat()
                when {
                    fmt.protocol == "m3u8" || fmt.protocol == "m3u8_native" -> media.type = HLS
                    fmt.protocol == "f4m" -> media.type = HDS
                    fmt.protocol == "http" || fmt.protocol == "https" -> media.type = HTTP
                    else -> {
                        Logger.log("unsupported protocol: " + fmt.protocol)
                        continue
                    }
                }
                media.url = fmt.url
                media.ext = fmt.ext
                media.width = fmt.width
                media.height = fmt.height

                media.format = createFormat(
                    media.ext ?: "", fmt.format, null, fmt.acodec ?: "", fmt.vcodec ?: "", fmt.width, fmt.height, -1
                )
                println("${media.format} ${media.url}")
                if (fmt.headers != null) {
                    media.headers.addAll(fmt.headers!!)
                }

                checkAndAddMedia(media, mediaList)
            }
        }
        Logger.log("VIDEO----" + obj["title"])
        for (i in mediaList.indices) {
            Logger.log(mediaList[i].type.toString() + " " + mediaList[i].format)
        }

        val pl = YdlVideo()
        pl.mediaFormats.addAll(mediaList)
        pl.mediaFormats.sortWith(compareByDescending<YdlMediaFormat> { it.width }.thenByDescending { it.abr })

        val stitle = obj["title"] as? String
        if (!StringUtils.isNullOrEmptyOrBlank(stitle)) {
            pl.title = stitle
        }

        var thumbnail = obj["thumbnail"] as? String
        if (thumbnail != null && thumbnail != "none" && thumbnail != "null") {
            pl.thumbnail = thumbnail
        }

        if (pl.thumbnail == null) {
            val thumbnails = obj["thumbnails"] as? JSONArray
            if (thumbnails != null) {
                for (i in 0 until thumbnails.size) {
                    Logger.log("Parsing thumbnails info")
                    val thumbnailObj = thumbnails[i] as JSONObject
                    thumbnail = thumbnailObj["url"] as? String
                    if (thumbnail != null && thumbnail != "none" && thumbnail != "null") {
                        pl.thumbnail = thumbnail
                        break
                    }
                }
            }
        }

        val sdur = "${obj["duration"]}"
        if (sdur != "none" && sdur != "null") {
            pl.duration = try {
                sdur.toLong()
            } catch (_: Exception) {
                -1
            }
        }

        return pl
    }

    private fun checkAndAddMedia(fmt: YdlMediaFormat, mediaList: ArrayList<YdlMediaFormat>) {
        for (i in mediaList.indices) {
            val m = mediaList[i]
            if (fmt.type == m.type) {
                if (fmt.type == DASH_HTTP) {
                    var sameAudio = false
                    var sameVideo = false
                    if (fmt.audioSegments == null) {
                        if (m.audioSegments == null) {
                            sameAudio = true
                        }
                    } else {
                        if (m.audioSegments != null) {
                            if (fmt.audioSegments!!.size == m.audioSegments!!.size) {
                                sameAudio = true
                                for (j in fmt.audioSegments!!.indices) {
                                    if (fmt.audioSegments!![j] != m.audioSegments!![j]) {
                                        sameAudio = false
                                        break
                                    }
                                }
                            }
                        }
                    }
                    if (fmt.videoSegments == null) {
                        if (m.videoSegments == null) {
                            sameVideo = true
                        }
                    } else {
                        if (m.videoSegments != null) {
                            if (fmt.videoSegments!!.size == m.videoSegments!!.size) {
                                sameVideo = true
                                for (j in fmt.videoSegments!!.indices) {
                                    if (fmt.videoSegments!![j] != m.videoSegments!![j]) {
                                        sameVideo = false
                                        break
                                    }
                                }
                            }
                        }
                    }
                    if (sameAudio && sameVideo) {
                        return
                    }
                } else {
                    if (m.url == fmt.url) {
                        return
                    }
                }
            }
        }
        mediaList.add(fmt)
    }

    private fun getVideoType(fmt: YdlFormat): Int {
        var fmtNote: String? = null
        var acodec: String? = null
        var vcodec: String? = null
        if (fmt.formatNote != null) {
            fmtNote = fmt.formatNote!!.lowercase()
            if (fmtNote == "none" || fmtNote!!.length < 1) {
                fmtNote = null
            }
        }
        if (fmtNote == null) {
            fmtNote = ""
        }
        if (fmt.acodec != null) {
            acodec = fmt.acodec!!.lowercase()
            if (acodec == "none" || acodec!!.length < 1) {
                acodec = null
            }
        }
        if (fmt.vcodec != null) {
            vcodec = fmt.vcodec!!.lowercase()
            if (vcodec == "none" || vcodec!!.length < 1) {
                vcodec = null
            }
        }

        if (fmtNote!!.contains("dash audio")) return DASH_AUDIO_ONLY
        if (fmtNote.contains("dash video")) return DASH_VIDEO_ONLY
        if (acodec == null && vcodec == null) return -1
        if (acodec != null && vcodec != null) return -1
        if (acodec != null && vcodec == null) return DASH_AUDIO_ONLY
        if (vcodec != null && acodec == null) return DASH_VIDEO_ONLY
        return -1
    }

    private fun getInt(obj: Any?): Int {
        if (obj == null) return -1
        if (obj.toString().contains("none")) return -1
        return Integer.parseInt("$obj")
    }

    private fun getString(obj: Any?): String? = obj as? String

    @JvmStatic
    fun nvl(str: String?): String = str ?: ""

    @JvmStatic
    fun createFormat(
        ext: String, fmt1: String?, fmt2: String?, acodec: String?, vcodec: String?,
        width: Int, height: Int, abr: Int
    ): String {
        val sb = StringBuilder()
        var ext2 = nvl(ext)
        if (ext2.isNotEmpty()) {
            sb.append(ext2.uppercase())
        }

        if (height > 0) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append("${height}p")
        }

        if (abr > 0) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append("${abr}k")
        }

        var acodec2 = nvl(acodec)
        if (acodec2.contains("none")) acodec2 = ""

        var vcodec2 = nvl(vcodec)
        if (vcodec2.contains("none")) vcodec2 = ""

        if (acodec2.isNotEmpty()) {
            if (sb.isNotEmpty()) sb.append(" ")
            sb.append(FormatUtilities.getFriendlyCodec(acodec2))
        }

        if (vcodec2.isNotEmpty()) {
            if (sb.isNotEmpty()) {
                sb.append(if (acodec2.isNotEmpty()) "/" else " ")
            }
            sb.append(FormatUtilities.getFriendlyCodec(vcodec2))
        }

        return sb.toString()
    }

    class YdlVideo {
        var title: String? = null
        var mediaFormats: ArrayList<YdlMediaFormat> = ArrayList()
        var index: Int = 0
        var thumbnail: String? = null
        var duration: Long = 0
    }

    class YdlMediaFormat {
        var type: Int = 0
        var url: String? = null
        var audioSegments: Array<String>? = null
        var videoSegments: Array<String>? = null
        var format: String? = null
        var ext: String? = null
        var headers: ArrayList<HttpHeader> = ArrayList()
        var headers2: ArrayList<HttpHeader> = ArrayList()
        var width: Int = 0
        var height: Int = 0
        var abr: Int = 0

        override fun toString(): String = format ?: ""
    }

    internal class YdlFormat {
        var url: String? = null
        var format: String? = null
        var fragments: Array<String>? = null
        var formatNote: String? = null
        var width: Int = 0
        var height: Int = 0
        var protocol: String? = null
        var ext: String? = null
        var acodec: String? = null
        var vcodec: String? = null
        var abr: Int = 0
        var headers: ArrayList<HttpHeader>? = null
    }
}
