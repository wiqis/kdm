package xdman.network.http.proxy

import java.net.URL

interface ProxyHandler {
    fun isSupported(paramInt: Int): Boolean

    fun isProxyCacheSupported(): Boolean

    @Throws(Exception::class)
    fun init(paramBrowserProxyInfo: BrowserProxyInfo?)

    @Throws(Exception::class)
    fun getProxyInfo(paramURL: URL?): Array<ProxyInfo>?
}
