package xdman.network.http

class HttpHeader {
    var name: String = ""
    var value: String = ""

    constructor()
    constructor(name: String, value: String) {
        this.name = name
        this.value = value
    }

    companion object {
        @JvmStatic
        fun parse(str: String): HttpHeader? {
            val index = str.indexOf(":")
            if (index < 0) return null
            val key = str.substring(0, index)
            val `val` = str.substring(index + 1)
            return HttpHeader(key, `val`)
        }
    }
}
