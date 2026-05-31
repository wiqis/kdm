package xdman.util

import java.net.InetAddress
import java.net.Socket
import javax.swing.JOptionPane

class ParamUtils {
    companion object {
        @JvmStatic
        fun sendParam(params: Map<String, String>) {
            val sb = StringBuilder()
            val paramIter = params.keys.iterator()
            while (paramIter.hasNext()) {
                val key = paramIter.next()
                val value = params[key]
                sb.append(key).append(":").append(value).append("\n")
            }

            val addr = InetAddress.getLoopbackAddress()

            val reqBuf = StringBuilder()
            reqBuf.append("GET /cmd HTTP/1.1\r\n")
            reqBuf.append("Content-Length: ").append(sb.length).append("\r\n")
            reqBuf.append("Host: ").append(addr.hostName).append("\r\n")
            reqBuf.append("Connection: close\r\n\r\n")
            reqBuf.append(sb)
            var resp: String? = null
            var sock: Socket? = null
            try {
                sock = Socket(InetAddress.getLoopbackAddress(), 9614)
                val `in` = sock.getInputStream()
                val out = sock.getOutputStream()
                out.write(reqBuf.toString().toByteArray())
                val line = NetUtils.readLine(`in`)
                if (line != null) {
                    resp = line.split(" ".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()[1]
                }
            } catch (e: Exception) {
            } finally {
                if (sock != null) {
                    try {
                        sock.close()
                    } catch (e2: Exception) {
                    }
                }
            }

            if ("200" != resp) {
                JOptionPane.showMessageDialog(null, "An older version of XDM is already running.")
            }
        }
    }
}
