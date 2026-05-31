package xdman.util

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class HttpDateParser {
    companion object {
        private var fmt: SimpleDateFormat? = null

        @JvmStatic
        @Synchronized
        fun parseHttpDate(lastModified: String?): Date? {
            if (StringUtils.isNullOrEmptyOrBlank(lastModified)) {
                return null
            }
            if (fmt == null) {
                fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("GMT")
                }
            }
            try {
                return fmt!!.parse(lastModified)
            } catch (e: ParseException) {
                Logger.log(e)
            }
            return null
        }
    }
}
