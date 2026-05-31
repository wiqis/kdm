package xdman.network

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocket
import xdman.Config
import xdman.network.http.HttpContext
import xdman.util.Logger

class SocketFactory {
    companion object {
        @JvmStatic
        @Throws(NetworkException::class)
        fun wrapSSL(socket: Socket?, host: String?, port: Int): SSLSocket {
            try {
                val sock2 = HttpContext.getInstance().sslContext.socketFactory
                    .createSocket(socket, host, port, true) as SSLSocket
                sock2.startHandshake()
                return sock2
            } catch (e: IOException) {
                throw NetworkException("Https connection failed: $host:$port")
            }
        }

        @JvmStatic
        @Throws(HostUnreachableException::class)
        fun createSocket(host: String?, port: Int): Socket {
            val config = Config.getInstance()
            try {
                val sock = Socket()
                sock.soTimeout = Config.getInstance().networkTimeout * 1000
                sock.tcpNoDelay = true
                if (config.tcpWindowSize > 0) {
                    try {
                        sock.receiveBufferSize = config.tcpWindowSize * 1024
                    } catch (e: Exception) {
                        Logger.log(e)
                    }
                }
                Logger.log("Tcp RWin: ${sock.receiveBufferSize}")
                sock.setSoLinger(false, 0)
                sock.connect(InetSocketAddress(host, port))
                return sock
            } catch (e: IOException) {
                throw HostUnreachableException("Unable to connect to: $host:$port")
            }
        }
    }
}
