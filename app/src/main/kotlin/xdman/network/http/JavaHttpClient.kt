package xdman.network.http

import xdman.network.ProxyResolver
import java.io.IOException
import java.io.InputStream
import java.net.*

class JavaHttpClient(private var _url: String) : HttpClient() {
    private var hc: HttpURLConnection? = null
    private var followRedirect = false
    private var realURL: URL? = null

    init {
        this.requestHeaders = HeaderCollection()
        this.responseHeaders = HeaderCollection()
    }

    fun setFollowRedirect(followRedirect: Boolean) {
        this.followRedirect = followRedirect
    }

    @Throws(IOException::class)
    override fun connect() {
        HttpContext.getInstance().init()
        val webproxy = ProxyResolver.resolve(_url)
        val url = URL(_url)
        this.realURL = url
        hc = if (webproxy != null) {
            val proxy = Proxy(
                if (webproxy.isSocks) Proxy.Type.SOCKS else Proxy.Type.HTTP,
                InetSocketAddress(webproxy.host, webproxy.port)
            )
            url.openConnection(proxy) as HttpURLConnection
        } else {
            url.openConnection() as HttpURLConnection
        }
        val headers = requestHeaders.getAll()
        while (headers.hasNext()) {
            val header = headers.next()
            hc!!.addRequestProperty(header.name, header.value)
        }
        hc!!.instanceFollowRedirects = this.followRedirect

        this.statusCode = hc!!.responseCode
        this.statusMessage = hc!!.responseMessage

        val responseHeaderMap = hc!!.headerFields

        val headerIterator = responseHeaderMap.keys.iterator()
        while (headerIterator.hasNext()) {
            val key = headerIterator.next()
            if (key == null) continue
            val headerValues = responseHeaderMap[key]
            val headerValueIterator = headerValues!!.iterator()
            while (headerValueIterator.hasNext()) {
                val value = headerValueIterator.next()
                val header = HttpHeader(key, value)
                this.responseHeaders.addHeader(header)
            }
        }
    }

    @get:Throws(IOException::class)
    override val contentLength: Long
        get() = hc!!.contentLengthLong

    override fun dispose() {
        hc!!.disconnect()
    }

    @get:Throws(IOException::class)
    override val inputStream: InputStream
        get() = hc!!.inputStream

    override val host: String
        get() = realURL!!.host + if (realURL!!.port > 0) ":" + realURL!!.port else ""
}
