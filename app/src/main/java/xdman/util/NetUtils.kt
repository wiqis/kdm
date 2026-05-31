package xdman.util

import java.io.*
import java.util.zip.GZIPInputStream
import xdman.network.http.*

class NetUtils {
    companion object {
        @JvmStatic
        fun getBytes(str: String): ByteArray {
            return str.toByteArray()
        }

        @JvmStatic
        @Throws(IOException::class)
        fun readLine(inStream: InputStream): String {
            val buf = StringBuilder()
            while (true) {
                val x = inStream.read()
                if (x == -1) {
                    throw IOException("Unexpected EOF while reading header line")
                }
                if (x == '\n'.code) {
                    return buf.toString()
                }
                if (x != '\r'.code) {
                    buf.append(x.toChar())
                }
            }
        }

        @JvmStatic
        fun getResponseCode(statusLine: String): Int {
            val arr = statusLine.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            if (arr.size < 2) return 400
            return arr[1].toInt()
        }

        @JvmStatic
        fun getContentLength(headers: HeaderCollection): Long {
            return try {
                val clen = headers.getValue("content-length")
                if (clen != null) {
                    clen.toLong()
                } else {
                    val crange = headers.getValue("content-range")
                    if (crange != null) {
                        val str = crange.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                        val splitStr = str.split("/".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[0]
                        val arr = splitStr.split("-".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                        arr[1].toLong() - arr[0].toLong() + 1
                    } else {
                        -1
                    }
                }
            } catch (e: Exception) {
                -1
            }
        }

        @JvmStatic
        @Throws(IOException::class)
        fun getInputStream(
            respHeaders: HeaderCollection,
            inStream: InputStream
        ): InputStream {
            var stream = inStream
            val transferEncoding = respHeaders.getValue("transfer-encoding")
            if (!StringUtils.isNullOrEmptyOrBlank(transferEncoding)) {
                stream = ChunkedInputStream(stream)
            }
            val contentEncoding = respHeaders.getValue("content-encoding")
            Logger.log("Content-Encoding: $contentEncoding")
            if (!StringUtils.isNullOrEmptyOrBlank(contentEncoding)) {
                if (contentEncoding.equals("gzip", ignoreCase = true)) {
                    stream = GZIPInputStream(stream)
                } else if (!(contentEncoding.equals("none", ignoreCase = true)
                            || contentEncoding.equals("identity", ignoreCase = true))) {
                    throw IOException("Content Encoding not supported: $contentEncoding")
                }
            }
            return stream
        }

        @JvmStatic
        @Throws(IOException::class)
        fun skipRemainingStream(
            respHeaders: HeaderCollection,
            inStream: InputStream
        ) {
            val stream = getInputStream(respHeaders, inStream)
            val length = getContentLength(respHeaders)
            skipRemainingStream(stream, length)
        }

        @JvmStatic
        @Throws(IOException::class)
        fun skipRemainingStream(inStream: InputStream, length: Long) {
            var len = length
            val buf = ByteArray(8192)
            if (len > 0) {
                while (len > 0) {
                    val r = if (len > buf.size) buf.size else len.toInt()
                    val x = inStream.read(buf, 0, r)
                    if (x == -1) break
                    len -= x.toLong()
                }
            } else {
                while (true) {
                    val x = inStream.read(buf)
                    if (x == -1) break
                }
            }
        }

        private fun getExtendedContentDisposition(header: String): String? {
            try {
                val arr = header.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                for (str in arr) {
                    if (str.contains("filename*")) {
                        val index = str.lastIndexOf("'")
                        if (index > 0) {
                            val st = str.substring(index + 1)
                            return XDMUtils.decodeFileName(st)
                        }
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return null
        }

        @JvmStatic
        fun getNameFromContentDisposition(header: String?): String? {
            try {
                if (header == null) return null
                val headerLow = header.lowercase()
                if (headerLow.startsWith("attachment")
                    || headerLow.startsWith("inline")
                ) {
                    val name = getExtendedContentDisposition(header)
                    if (name != null) return name
                    val arr = header.split(";".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                    for (i in arr.indices) {
                        val str = arr[i].trim { it <= ' ' }
                        if (str.lowercase().startsWith("filename")) {
                            val index = str.indexOf('=')
                            val file = str.substring(index + 1).replace("\"", "")
                                .trim { it <= ' ' }
                            return try {
                                XDMUtils.decodeFileName(file)
                            } catch (e: Exception) {
                                file
                            }
                        }
                    }
                }
            } catch (e: Exception) {
            }
            return null
        }

        @JvmStatic
        fun getCleanContentType(contentType: String?): String? {
            if (contentType == null || contentType.isEmpty()) return contentType
            val index = contentType.indexOf(";")
            if (index > 0) {
                return contentType.substring(0, index).trim { it <= ' ' }.lowercase()
            }
            return contentType
        }
    }
}
