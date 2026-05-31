package xdman.mediaconversion

import java.io.BufferedReader
import java.io.PrintStream

class Format {
    var group: String? = null
    var desc: String? = null
    var ext: String? = null
    var vidExtra: String? = null

    var videoCodecs: MutableList<String>? = null
    var defautVideoCodec: String? = null
    var resolutions: MutableList<String>? = null
    var defaultResolution: String? = null
    var aspectRatio: MutableList<String>? = null
    var defaultAspectRatio: String? = null
    var videoBitrate: MutableList<String>? = null
    var defaultVideoBitrate: String? = null
    var frameRate: MutableList<String>? = null
    var defaultFrameRate: String? = null
    var audioCodecs: MutableList<String>? = null
    var defautAudioCodec: String? = null
    var audioSampleRate: MutableList<String>? = null
    var defaultAudioSampleRate: String? = null
    var audioBitrate: MutableList<String>? = null
    var defaultAudioBitrate: String? = null
    var audioChannel: MutableList<String>? = null
    var defaultAudioChannel: String? = null

    fun getDefautValue(list: List<String>, defaultValue: String): String? {
        for (str in list) {
            if (str == defaultValue) return str
        }
        return if (list.isNotEmpty()) list[0] else null
    }

    override fun toString(): String = desc ?: ""

    fun write(out: PrintStream) {
        out.println("name: $desc")
        out.println("group: $group")
        out.println("resolutions: ${getList(resolutions)}")
        out.println("video_codecs:${getList(videoCodecs)}")
        out.println("framerates:${getList(frameRate)}")
        out.println("video_bitrates:${getList(videoBitrate)}")
        out.println("aspect_ratios:${getList(aspectRatio)}")
        out.println("audio_codecs:${getList(audioCodecs)}")
        out.println("audio_bitrates:${getList(audioBitrate)}")
        out.println("audio_samplerates:${getList(audioSampleRate)}")
        out.println("audio_channels:${getList(audioChannel)}")
        out.println("default_resolution:$defaultResolution")
        out.println("default_video_codec:$defautVideoCodec")
        out.println("default_framerate:$defaultFrameRate")
        out.println("default_video_bitrate:$defaultVideoBitrate")
        out.println("default_aspect_ratio:$defaultAspectRatio")
        out.println("default_audio_codec:$defautAudioCodec")
        out.println("default_audio_bitrate:$defaultAudioBitrate")
        out.println("default_samplerate:$defaultAudioSampleRate")
        out.println("default_channel:$defaultAudioChannel")
        out.println()
    }

    companion object {
        const val MPEG4_XVID_MAX_RESOLUTION = "640x480"
        const val MPEG4_XVID_MAX_VIDEO_BR = "2500"
        const val MPEG4_XVID_MAX_FRAME_RATE = "30"

        fun getBitRate(s: String): String? {
            return try {
                java.lang.Double.parseDouble(s)
                "${s}k"
            } catch (e: Exception) {
                null
            }
        }

        fun getSize(name: String?): String? {
            if (name != null) {
                val n = name.lowercase()
                if (n.contains("x")) return n
            }
            return null
        }

        fun getAspec(name: String?): String? {
            if (name != null) {
                val n = name.lowercase()
                if (n.contains("/")) return n.replace("/", ":")
            }
            return null
        }

        fun getCodecName(name: String?): String? {
            if (name == null) return null
            return when (name.lowercase()) {
                "vp8" -> "vp8"
                "vp9" -> "vp9"
                "wmv1", "wmv" -> "wmv1"
                "wmv v8", "wmv2" -> "wmv2"
                "wmv v9", "wmv3" -> "wmv2"
                "ffv1" -> "ffv1"
                "flv" -> "flv"
                "gif" -> "gif"
                "xvid" -> "libxvid"
                "h263" -> "h263"
                "h264", "x264" -> "libx264"
                "x265" -> "libx265"
                "h263p" -> "h263p"
                "huffyuv" -> "huffyuv"
                "libtheora", "theora" -> "libtheora"
                "mjpeg" -> "mjpeg"
                "mpeg1", "mpeg1video" -> "mpeg1video"
                "mpeg2", "mpeg2video" -> "mpeg2video"
                "mpeg4" -> "mpeg4"
                "msmpeg4" -> "msmpeg4v1"
                "msmpeg4v2" -> "msmpeg4v2"
                "wma 9.2" -> "wmapro"
                "aac", "aac_low", "aac_ltp", "faac", "aac_main" -> "aac"
                "alac" -> "alac"
                "ac3" -> "ac3"
                "ape" -> "ape"
                "dca" -> "dts"
                "flac" -> "flac"
                "mp2" -> "mp2"
                "mp3" -> "libmp3lame"
                "ogg", "vorbis" -> "libvorbis"
                "opencore_amrnb" -> "libopencore_amrnb"
                "pcm" -> "pcm_u8"
                "pcm_s16be" -> "pcm_s16be"
                "wmav1" -> "wmav1"
                "wmav2" -> "wmav2"
                else -> null
            }
        }

        fun read(br: BufferedReader): Format? {
            val format = Format()
            while (true) {
                val ln = br.readLine() ?: return null
                val index = ln.indexOf(":")
                if (index < 0) break
                val key = ln.substring(0, index).trim()
                val value = ln.substring(index + 1).trim()
                when (key) {
                    "name" -> format.desc = value
                    "ext" -> format.ext = value
                    "group" -> format.group = value
                    "resolutions" -> format.resolutions = toList(value)
                    "video_extra" -> format.vidExtra = value
                    "video_codecs" -> format.videoCodecs = toList(value)
                    "framerates" -> format.frameRate = toList(value)
                    "video_bitrates" -> format.videoBitrate = toList(value)
                    "audio_codecs" -> format.audioCodecs = toList(value)
                    "aspect_ratios" -> format.aspectRatio = toList(value)
                    "audio_bitrates" -> format.audioBitrate = toList(value)
                    "audio_samplerates" -> format.audioSampleRate = toList(value)
                    "audio_channels" -> format.audioChannel = toList(value)
                    "default_resolution" -> format.defaultResolution = value
                    "default_video_codec" -> format.defautVideoCodec = value
                    "default_framerate" -> format.defaultFrameRate = value
                    "default_video_bitrate" -> format.defaultVideoBitrate = value
                    "default_aspect_ratio" -> format.defaultAspectRatio = value
                    "default_audio_codec" -> format.defautAudioCodec = value
                    "default_audio_bitrate" -> format.defaultAudioBitrate = value
                    "default_samplerate" -> format.defaultAudioSampleRate = value
                    "default_channel" -> format.defaultAudioChannel = value
                }
            }
            return format
        }

        fun toList(str: String?): MutableList<String> {
            val list = ArrayList<String>()
            if (str.isNullOrBlank()) return list
            val arr = str.split(" ")
            for (s in arr) {
                if (s.trim().isNotEmpty()) {
                    list.add(s)
                }
            }
            return list
        }
    }

    fun getList(list: List<String>?): String {
        val sb = StringBuilder()
        var first = true
        if (list != null) {
            for (s in list) {
                if (!first) {
                    sb.append(" ")
                }
                sb.append(s)
                if (first) {
                    first = false
                }
            }
        }
        return sb.toString()
    }
}
