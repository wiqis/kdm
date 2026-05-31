package xdman.util

import xdman.DownloadEntry
import xdman.DownloadQueue
import xdman.QueueManager
import xdman.XDMConstants
import xdman.ui.res.StringResource
import java.text.SimpleDateFormat
import java.util.*

object FormatUtilities {
    private var _format: SimpleDateFormat? = null
    private const val MB = 1024 * 1024
    private const val KB = 1024

    @Synchronized
    @JvmStatic
    fun formatDate(date: Long): String {
        if (_format == null) {
            _format = SimpleDateFormat("yyyy-MM-dd")
        }
        val dt = Date(date)
        return _format!!.format(dt)
    }

    @JvmStatic
    fun formatSize(length: Double): String {
        if (length < 0) return "---"
        return if (length > MB) {
            String.format("%.1f MB", (length / MB).toFloat())
        } else if (length > KB) {
            String.format("%.1f KB", (length / KB).toFloat())
        } else {
            String.format("%d B", length.toInt())
        }
    }

    @JvmStatic
    fun getFormattedStatus(ent: DownloadEntry): String {
        var statStr = ""
        if (ent.queueId != null) {
            val q = QueueManager.getInstance().getQueueById(ent.queueId)
            var qname = ""
            if (q != null && q.queueId != null) {
                qname = if (q.queueId.length > 0) "[ " + q.name + " ] " else ""
            }
            statStr += qname
        }

        statStr += when (ent.state) {
            XDMConstants.FINISHED -> StringResource.get("STAT_FINISHED")
            XDMConstants.PAUSED, XDMConstants.FAILED -> StringResource.get("STAT_PAUSED")
            XDMConstants.ASSEMBLING -> StringResource.get("STAT_ASSEMBLING")
            else -> StringResource.get("STAT_DOWNLOADING")
        }
        val sizeStr = formatSize(ent.size.toDouble())
        return if (ent.state == XDMConstants.FINISHED) {
            "$statStr $sizeStr"
        } else {
            if (ent.size > 0) {
                val downloadedStr = formatSize(ent.downloaded.toDouble())
                val progressStr = ent.progress.toString() + "%"
                "$statStr $progressStr [ $downloadedStr / $sizeStr ]"
            } else {
                statStr + (if (ent.progress > 0) " " + ent.progress + "%" else "") +
                        (if (ent.downloaded > 0) " " + formatSize(ent.downloaded.toDouble())
                        else if (ent.state == XDMConstants.PAUSED || ent.state == XDMConstants.FAILED) "" else " ...")
            }
        }
    }

    @JvmStatic
    fun getETA(length: Double, rate: Float): String {
        if (length == 0.0) return "00:00:00"
        return if (length < 1 || rate <= 0) "---"
        else {
            val sec = (length / rate).toInt()
            hms(sec)
        }
    }

    @JvmStatic
    fun hms(sec: Int): String {
        val hrs = sec / 3600
        val min = sec % 3600 / 60
        val s = sec % 60
        return String.format("%02d:%02d:%02d", hrs, min, s)
    }

    @JvmStatic
    fun getResolution(res: String?): String? {
        var res2 = res ?: return null
        res2 = res2.lowercase().trim()
        val index = res2.indexOf("x")
        if (index > 0) {
            res2 = res2.substring(index + 1).trim()
            try {
                Integer.parseInt(res2)
                return "${res2}p"
            } catch (_: Exception) {
            }
        }
        return res2
    }

    @JvmStatic
    fun getFriendlyCodec(name: String?): String? {
        var name2 = name ?: return name
        if (!StringUtils.isNullOrEmptyOrBlank(name2)) {
            name2 = name2.lowercase().trim()
            when {
                name2.startsWith("avc") -> return "h264"
                name2.startsWith("mp4a") -> return "aac"
                name2.startsWith("mp4v") -> return "mpeg4"
                name2.startsWith("samr") -> return "amr"
            }
        }
        return name2
    }
}
