package xdman.network.http

import xdman.util.NetUtils
import java.io.IOException
import java.io.InputStream
import java.util.*

class HeaderCollection {
    private val headers: MutableList<HttpHeader> = ArrayList()

    fun getValue(name: String): String? {
        for (i in headers.indices) {
            val header = headers[i]
            if (header.name.equals(name, ignoreCase = true)) {
                return header.value
            }
        }
        return null
    }

    fun containsHeader(name: String): Boolean {
        for (i in headers.indices) {
            val header = headers[i]
            if (header.name.equals(name, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    fun getHeaders(name: String): Iterator<HttpHeader> {
        val list: MutableList<HttpHeader> = ArrayList()
        for (i in headers.indices) {
            val header = headers[i]
            if (header.name.equals(name, ignoreCase = true)) {
                list.add(header)
            }
        }
        return list.iterator()
    }

    fun getAll(): Iterator<HttpHeader> {
        return headers.iterator()
    }

    fun addHeader(name: String, value: String) {
        this.addHeader(HttpHeader(name, value))
    }

    fun addHeader(header: HttpHeader?) {
        if (header == null) throw NullPointerException("Header is null")
        this.headers.add(header)
    }

    fun setValue(name: String, value: String) {
        var found = false
        for (i in headers.indices) {
            val header = headers[i]
            if (header.name.equals(name, ignoreCase = true)) {
                header.value = value
                found = true
            }
        }
        if (!found) {
            addHeader(name, value)
        }
    }

    fun add(text: String) {
        addHeader(HttpHeader.parse(text))
    }

    fun clear() {
        this.headers.clear()
    }

    fun appendToBuffer(buf: StringBuffer) {
        for (i in headers.indices) {
            val header = headers[i]
            buf.append("${header.name}: ${header.value}\r\n")
        }
    }

    @Throws(IOException::class)
    fun loadFromStream(inStream: InputStream) {
        while (true) {
            val ln = NetUtils.readLine(inStream)
            if (ln.length < 1) break
            val index = ln.indexOf(":")
            if (index > 0) {
                val key = ln.substring(0, index).trim { it <= ' ' }
                val value = ln.substring(index + 1).trim { it <= ' ' }
                val header = HttpHeader(key, value)
                headers.add(header)
            }
        }
    }
}
