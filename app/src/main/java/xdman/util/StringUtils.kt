package xdman.util

object StringUtils {
    @JvmStatic
    fun isNullOrEmpty(str: String?): Boolean {
        return str == null || str.isEmpty()
    }

    @JvmStatic
    fun isNullOrEmptyOrBlank(str: String?): Boolean {
        return str == null || str.trim().isEmpty()
    }

    @JvmStatic
    fun getBytes(sb: StringBuffer): ByteArray {
        return sb.toString().toByteArray()
    }

    @JvmStatic
    fun getBytes(s: String): ByteArray {
        return s.toByteArray()
    }
}
