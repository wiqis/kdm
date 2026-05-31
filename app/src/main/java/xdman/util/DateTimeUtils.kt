package xdman.util

import java.util.Calendar
import java.util.Date

class DateTimeUtils {
    companion object {
        @JvmStatic
        fun getDefaultStart(): Date {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.time
        }

        @JvmStatic
        fun getDefaultEnd(): Date {
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 23)
            cal.set(Calendar.MINUTE, 59)
            cal.set(Calendar.SECOND, 59)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.time
        }

        @JvmStatic
        fun getBeginDate(): Date {
            return getDefaultStart()
        }

        @JvmStatic
        fun getEndDate(): Date {
            val cal = Calendar.getInstance()
            cal.add(Calendar.YEAR, 100)
            return cal.time
        }

        @JvmStatic
        fun getTimePart(date: Date): Long {
            val cal = Calendar.getInstance()
            cal.time = date
            return (cal.get(Calendar.HOUR_OF_DAY) * 3600 + cal.get(Calendar.MINUTE)
                    * 60 + cal.get(Calendar.SECOND)).toLong()
        }

        @JvmStatic
        fun addTimePart(sec: Long): Date? {
            if (sec < 0) {
                return null
            }
            val cal = Calendar.getInstance()
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            cal.add(Calendar.SECOND, sec.toInt())
            return cal.time
        }

        @JvmStatic
        fun getDatePart(cal: Calendar): Date {
            cal.set(Calendar.HOUR_OF_DAY, 0)
            cal.set(Calendar.MINUTE, 0)
            cal.set(Calendar.SECOND, 0)
            cal.set(Calendar.MILLISECOND, 0)
            return cal.time
        }
    }
}
