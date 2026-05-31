package xdman.network.http

import xdman.CredentialManager
import xdman.network.ICredentialManager
import xdman.util.Logger
import java.net.Authenticator
import java.net.PasswordAuthentication
import java.net.Socket
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.TrustManager
import javax.net.ssl.X509ExtendedTrustManager

class HttpContext private constructor() {
    lateinit var sslContext: SSLContext
        private set

    fun registerCredentialManager(mgr: ICredentialManager) {
    }

    fun init() {
        if (!init) {
            Logger.log("Context initialized")
            System.setProperty("http.auth.preference", "ntlm")
            try {
                try {
                    sslContext = SSLContext.getInstance("TLS")
                } catch (e: Exception) {
                    e.printStackTrace()
                    sslContext = SSLContext.getInstance("SSL")
                }

                val trustAllCerts = arrayOf<TrustManager>(object : X509ExtendedTrustManager() {
                    override fun checkClientTrusted(chain: Array<X509Certificate?>, authType: String) {
                    }

                    override fun checkServerTrusted(chain: Array<X509Certificate?>, authType: String) {
                    }

                    override fun getAcceptedIssuers(): Array<X509Certificate>? {
                        return null
                    }

                    override fun checkClientTrusted(chain: Array<X509Certificate?>, authType: String, socket: Socket) {
                    }

                    override fun checkClientTrusted(chain: Array<X509Certificate?>, authType: String, engine: SSLEngine) {
                    }

                    override fun checkServerTrusted(chain: Array<X509Certificate?>, authType: String, socket: Socket) {
                    }

                    override fun checkServerTrusted(chain: Array<X509Certificate?>, authType: String, engine: SSLEngine) {
                    }
                })

                sslContext.init(null, trustAllCerts, SecureRandom())
                HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.socketFactory)
            } catch (e: Exception) {
                Logger.log(e)
            }

            Authenticator.setDefault(object : Authenticator() {
                override fun getPasswordAuthentication(): PasswordAuthentication {
                    println("Called on $requestorType scheme: $requestingScheme host: $requestingHost url: $requestingURL prompt: $requestingPrompt")
                    return if (requestorType == RequestorType.SERVER) {
                        CredentialManager.getInstance().getCredentialForHost(requestingHost) ?: PasswordAuthentication("", CharArray(0))
                    } else {
                        CredentialManager.getInstance().getCredentialForProxy() ?: PasswordAuthentication("", CharArray(0))
                    }
                }
            })
            init = true
        }
    }

    companion object {
        private var _this: HttpContext? = null
        private var init = false

        @JvmStatic
        fun getInstance(): HttpContext {
            if (_this == null) {
                _this = HttpContext()
                _this!!.init()
            }
            return _this!!
        }
    }
}
