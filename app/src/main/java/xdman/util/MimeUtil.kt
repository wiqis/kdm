package xdman.util

import java.util.HashMap

class MimeUtil private constructor() {
    companion object {
        private var mime: HashMap<String, String>? = null

        @JvmStatic
        fun getFileExt(target: String?): String? {
            if (mime == null) {
                init()
            }
            return mime?.get(target)
        }

        @Synchronized
        private fun init() {
            if (mime != null) return
            val m = HashMap<String, String>()
            m["audio/x-aiff"] = "aiff"
            m["audio/basic"] = "au"
            m["video/x-msvideo"] = "avi"
            m["application/x-bcpio"] = "bcpio"
            m["image/bmp"] = "bmp"
            m["application/x-cpio"] = "cpio"
            m["text/css"] = "css"
            m["application/x-msdownload"] = "dll"
            m["application/msword"] = "doc"
            m["image/gif"] = "gif"
            m["application/x-gtar"] = "gtar"
            m["application/x-gzip"] = "gz"
            m["text/html"] = "html"
            m["image/x-icon"] = "ico"
            m["image/jpeg"] = "jpeg"
            m["application/x-javascript"] = "js"
            m["audio/mid"] = "mid"
            m["video/quicktime"] = "mov"
            m["audio/mpeg"] = "mp3"
            m["video/mpeg"] = "mpeg"
            m["application/pdf"] = "pdf"
            m["application/vnd.ms-powerpoint"] = "ppt"
            m["application/postscript"] = "ps"
            m["video/quicktime"] = "qt"
            m["application/rtf"] = "rtf"
            m["application/x-stuffit"] = "sit"
            m["image/svg+xml"] = "svg"
            m["application/x-shockwave-flash"] = "swf"
            m["application/x-tar"] = "tar"
            m["application/x-compressed"] = "tgz"
            m["image/tiff"] = "tiff"
            m["text/plain"] = "txt"
            m["audio/x-wav"] = "wav"
            m["application/vnd.ms-excel"] = "xls"
            m["application/x-compress"] = "z"
            m["application/zip"] = "zip"
            m["video/x-flv"] = "flv"
            m["video/flv"] = "flv"
            m["video/webm"] = "webm"
            m["video/3gpp"] = "3gp"
            m["video/mp4"] = "mp4"
            m["video/x-ms-wmv"] = "wmv"
            mime = m
        }
    }
}
