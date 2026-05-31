package xdman.network

import xdman.Config
import xdman.network.http.WebProxy

class ProxyResolver {
    companion object {
        @JvmStatic
        fun resolve(url: String?): WebProxy? {
            val config = Config.getInstance()
            val proxyMode = config.proxyMode
            if (proxyMode == 2) {
                val proxyHost = config.proxyHost
                if (proxyHost == null || proxyHost.length < 1) {
                    return null
                }
                if (config.proxyPort < 1) {
                    return null
                }
                return WebProxy(proxyHost, config.proxyPort)
            }
            if (proxyMode == 3) {
                val socksHost = config.socksHost
                if (socksHost == null || socksHost.length < 1) {
                    return null
                }
                if (config.socksPort < 1) {
                    return null
                }
                val wp = WebProxy(socksHost, config.socksPort)
                wp.isSocks = true
                return wp
            }
            return null
        }
    }
}
