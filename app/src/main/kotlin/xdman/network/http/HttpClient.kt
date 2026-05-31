package xdman.network.http

import xdman.network.ICredentialManager
import java.io.IOException
import java.io.InputStream

abstract class HttpClient {
    @JvmField
    var requestHeaders: HeaderCollection = HeaderCollection()
    @JvmField
    var responseHeaders: HeaderCollection = HeaderCollection()
    @JvmField
    var credentialMgr: ICredentialManager? = null
    @JvmField
    var statusCode: Int = 0
    @JvmField
    var statusMessage: String? = null

    fun addHeader(name: String, value: String) {
        this.requestHeaders.addHeader(name, value)
    }

    fun setHeader(name: String, value: String) {
        this.requestHeaders.setValue(name, value)
    }

    fun getResponseHeader(name: String): String? {
        return this.responseHeaders.getValue(name)
    }

    fun setCredentialMgr(mgr: ICredentialManager?) {
        this.credentialMgr = mgr
    }

    fun getStatusCode(): Int = statusCode

    fun getStatusMessage(): String? = statusMessage

    @Throws(IOException::class)
    abstract fun connect()

    abstract fun dispose()

    @get:Throws(IOException::class)
    abstract val inputStream: InputStream

    @get:Throws(IOException::class)
    abstract val contentLength: Long

    abstract val host: String
}
