package xdman.network.http.proxy

class ProxyInfo {
    var proxy: String? = null
        private set
    var port: Int = -1
        private set
    var socksProxy: String? = null
        private set
    var socksPort: Int = -1
        private set

    constructor(paramString: String?) : this(paramString, null)

    constructor(paramString1: String?, paramString2: String?) {
        var p1 = paramString1
        var p2 = paramString2
        if (p1 != null) {
            var i = p1.indexOf("//")
            if (i >= 0) {
                p1 = p1.substring(i + 2)
            }
            i = p1.lastIndexOf(':')
            if (i >= 0) {
                this.proxy = p1.substring(0, i)
                try {
                    this.port = p1.substring(i + 1).trim().toInt()
                } catch (localException1: Exception) {
                }
            } else if (p1 != "") {
                this.proxy = p1
            }
        }
        if (p2 != null) {
            val i = p2.lastIndexOf(':')
            if (i >= 0) {
                this.socksProxy = p2.substring(0, i)
                try {
                    this.socksPort = p2.substring(i + 1).trim().toInt()
                } catch (localException2: Exception) {
                }
            } else if (p2 != "") {
                this.socksProxy = p2
            }
        }
    }

    constructor(paramString: String?, paramInt: Int) : this(paramString, paramInt, null, -1)

    constructor(paramString1: String?, paramInt1: Int, paramString2: String?, paramInt2: Int) {
        this.proxy = paramString1
        this.port = paramInt1
        this.socksProxy = paramString2
        this.socksPort = paramInt2
    }

    fun isProxyUsed(): Boolean {
        return proxy != null || socksProxy != null
    }

    fun isSocksUsed(): Boolean {
        return socksProxy != null
    }

    override fun toString(): String {
        if (proxy != null) {
            return "$proxy:$port"
        }
        if (socksProxy != null) {
            return "$socksProxy:$socksPort"
        }
        return "DIRECT"
    }
}
