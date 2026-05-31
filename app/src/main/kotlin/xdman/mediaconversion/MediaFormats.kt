package xdman.mediaconversion

import xdman.util.StringUtils
import java.io.BufferedReader
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset

class MediaFormats {
    companion object {
        private var supportedFormats: Array<MediaFormat>? = null

        init {
            val list = ArrayList<MediaFormat>()
            list.add(MediaFormat())
            try {
                var inStream = MediaFormats::class.java.getResourceAsStream("/formats/list.txt")
                if (inStream == null) {
                    inStream = FileInputStream("formats/list.txt")
                }
                val r = InputStreamReader(inStream, Charset.forName("utf-8"))
                val br = BufferedReader(r, 1024)
                while (true) {
                    val ln = br.readLine() ?: break
                    if (ln.startsWith("#")) continue
                    val arr = ln.split("\\|".toRegex()).toTypedArray()
                    if (arr.size != 12) continue
                    val format = MediaFormat()
                    format.format = getString(arr[0])
                    format.resolution = getString(arr[1])
                    format.video_codec = getString(arr[2])
                    format.video_bitrate = getString(arr[3])
                    format.framerate = getString(arr[4])
                    format.video_param_extra = getString(arr[5])
                    format.audio_codec = getString(arr[6])
                    format.audio_bitrate = getString(arr[7])
                    format.samplerate = getString(arr[8])
                    format.audio_extra_param = getString(arr[9])
                    format.description = getString(arr[10])
                    format.audioOnly = "1" == getString(arr[11])

                    list.add(format)
                    supportedFormats = list.toTypedArray()
                }
            } catch (e: Exception) {
            }
        }

        @JvmStatic
        fun getString(str: String): String? {
            return if (!StringUtils.isNullOrEmptyOrBlank(str)) str else null
        }

        @JvmStatic
        fun getSupportedFormats(): Array<MediaFormat>? = supportedFormats

        @JvmStatic
        fun setSupportedFormats(supportedFmts: Array<MediaFormat>?) {
            supportedFormats = supportedFmts
        }
    }
}
