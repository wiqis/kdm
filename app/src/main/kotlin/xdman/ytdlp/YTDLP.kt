package xdman.ytdlp

import org.json.simple.JSONArray
import org.json.simple.JSONObject
import org.json.simple.parser.JSONParser
import xdman.util.Logger
import java.io.File

data class YTFormat(
    val formatId: String,
    val ext: String,
    val height: Int,
    val width: Int,
    val tbr: Float,
    val filesize: Long,
    val vcodec: String,
    val acodec: String,
    val fps: Int,
    val url: String?,
    val formatNote: String,
    val hasAudio: Boolean,
    val hasVideo: Boolean,
    val filesizeApprox: Long
) {
    val isAudioOnly: Boolean get() = hasAudio && !hasVideo
    val isVideoOnly: Boolean get() = hasVideo && !hasAudio
    val isCombined: Boolean get() = hasAudio && hasVideo
    val qualityLabel: String get() {
        if (isAudioOnly) return "Audio only (${ext})"
        val h = if (height > 0) "${height}p" else ""
        val note = if (formatNote.isNotBlank()) " $formatNote" else ""
        val fpsLabel = if (fps > 30) " ${fps}fps" else ""
        val audioInfo = if (isVideoOnly) " [no audio]" else ""
        return "${h}${fpsLabel}${audioInfo}${note} • ${ext}"
    }
}

data class YTVideoInfo(
    val id: String,
    val title: String,
    val duration: Long,
    val thumbnail: String,
    val webpageUrl: String,
    val formats: List<YTFormat>,
    val durationStr: String
) {
    val bestCombinedFormat: YTFormat? get() = formats.filter { it.isCombined }.maxByOrNull { it.height }
    val bestVideoOnly: YTFormat? get() = formats.filter { it.isVideoOnly }.maxByOrNull { it.height }
    val bestAudioOnly: YTFormat? get() = formats.filter { it.isAudioOnly }.maxByOrNull { it.tbr }
}

data class YTPlaylistEntry(
    val id: String,
    val title: String,
    val url: String,
    val duration: Long?,
    val durationStr: String
)

data class YTPlaylistInfo(
    val id: String,
    val title: String,
    val entries: List<YTPlaylistEntry>,
    val webpageUrl: String
)

object YTDLP {
    private val parser = JSONParser()

    private fun runYTDLP(vararg args: String): String {
        val cmd = arrayListOf(YTDLPManager.getPath())
        cmd.addAll(args)
        val proc = ProcessBuilder(cmd)
            .redirectErrorStream(true)
            .start()
        return proc.inputStream.bufferedReader().readText().trim()
    }

    fun getVideoInfo(url: String): YTVideoInfo {
        val jsonStr = runYTDLP(
            "--dump-json",
            "--no-warnings",
            "--no-playlist",
            url
        )
        val json = parser.parse(jsonStr) as JSONObject
        return parseVideoInfo(json)
    }

    private fun parseVideoInfo(json: JSONObject): YTVideoInfo {
        val id = json["id"] as String
        val title = json["title"] as String
        val duration = json["duration"] as? Long ?: 0L
        val thumbnail = json["thumbnail"] as? String ?: ""
        val webpageUrl = json["webpage_url"] as? String ?: ""
        val durationSecs = duration

        val formatsArr = json["formats"] as? JSONArray ?: JSONArray()
        val formats = formatsArr.mapNotNull { fObj ->
            val f = fObj as? JSONObject ?: return@mapNotNull null
            try {
                val formatId = f["format_id"] as? String ?: return@mapNotNull null
                val ext = f["ext"] as? String ?: "unknown"
                val height = (f["height"] as? Long)?.toInt() ?: 0
                val width = (f["width"] as? Long)?.toInt() ?: 0
                val tbr = (f["tbr"] as? Number)?.toFloat() ?: 0f
                val filesize = (f["filesize"] as? Long) ?: 0L
                val vcodec = f["vcodec"] as? String ?: "none"
                val acodec = f["acodec"] as? String ?: "none"
                val fps = (f["fps"] as? Number)?.toInt() ?: 0
                val url = f["url"] as? String
                val formatNote = f["format_note"] as? String ?: ""
                val filesizeApprox = (f["filesize_approx"] as? Long) ?: 0L

                YTFormat(
                    formatId = formatId,
                    ext = ext,
                    height = height,
                    width = width,
                    tbr = tbr,
                    filesize = filesize,
                    vcodec = vcodec,
                    acodec = acodec,
                    fps = fps,
                    url = url,
                    formatNote = formatNote,
                    hasAudio = acodec != "none",
                    hasVideo = vcodec != "none",
                    filesizeApprox = filesizeApprox
                )
            } catch (e: Exception) {
                Logger.log(e)
                null
            }
        }

        val durationStr = formatDuration(durationSecs)
        return YTVideoInfo(id, title, durationSecs, thumbnail, webpageUrl, formats, durationStr)
    }

    fun getPlaylistInfo(url: String, flat: Boolean = true): YTPlaylistInfo {
        val args = mutableListOf("--dump-json", "--no-warnings")
        if (flat) args.add("--flat-playlist")
        args.add(url)
        val output = runYTDLP(*args.toTypedArray())

        val entries = mutableListOf<YTPlaylistEntry>()
        var playlistId = ""
        var playlistTitle = ""
        var webpageUrl = ""

        output.lines().forEach { line ->
            if (line.isBlank()) return@forEach
            try {
                val json = parser.parse(line) as JSONObject
                val entriesArr = json["entries"] as? JSONArray
                if (entriesArr != null) {
                    // Playlist-level JSON with entries array
                    playlistId = json["id"] as? String ?: ""
                    playlistTitle = json["title"] as? String ?: ""
                    webpageUrl = json["webpage_url"] as? String ?: ""
                    entriesArr.forEach { eObj ->
                        val e = eObj as? JSONObject ?: return@forEach
                        val eid = e["id"] as? String ?: return@forEach
                        val eTitle = e["title"] as? String ?: "Unknown"
                        val eDur = e["duration"] as? Long
                        entries.add(YTPlaylistEntry(
                            id = eid,
                            title = eTitle,
                            url = "https://youtube.com/watch?v=$eid",
                            duration = eDur,
                            durationStr = if (eDur != null) formatDuration(eDur) else ""
                        ))
                    }
                } else {
                    // Individual video JSON (from --dump-json with playlist, outputs one per line)
                    val eid = json["id"] as? String ?: return@forEach
                    val eTitle = json["title"] as? String ?: "Unknown"
                    val eDur = json["duration"] as? Long
                    entries.add(YTPlaylistEntry(
                        id = eid,
                        title = eTitle,
                        url = "https://youtube.com/watch?v=$eid",
                        duration = eDur,
                        durationStr = if (eDur != null) formatDuration(eDur) else ""
                    ))
                }
            } catch (e: Exception) {
                Logger.log(e)
            }
        }

        // If flat playlist gave entries but no playlist-level info, try getting it
        if (playlistTitle.isBlank() && entries.isNotEmpty()) {
            try {
                val infoJson = runYTDLP("--dump-json", "--no-warnings", "--flat-playlist", "--playlist-items", "1", url)
                val info = parser.parse(infoJson.lines().first()) as? JSONObject
                if (info != null) {
                    playlistId = info["playlist_id"] as? String ?: info["id"] as? String ?: ""
                    playlistTitle = info["playlist_title"] as? String ?: ""
                    webpageUrl = info["webpage_url"] as? String ?: url
                }
            } catch (_: Exception) {}
        }

        return YTPlaylistInfo(playlistId, playlistTitle, entries, webpageUrl)
    }

    fun getDirectUrl(videoUrl: String, formatId: String): String? {
        return try {
            val result = runYTDLP("-f", formatId, "--get-url", "--no-warnings", "--no-playlist", videoUrl)
            if (result.startsWith("http://") || result.startsWith("https://")) result else null
        } catch (e: Exception) {
            Logger.log(e)
            null
        }
    }

    fun getDirectUrls(videoUrl: String, formatIds: List<String>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        for (fid in formatIds) {
            getDirectUrl(videoUrl, fid)?.let { result[fid] = it }
        }
        return result
    }

    private fun formatDuration(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
