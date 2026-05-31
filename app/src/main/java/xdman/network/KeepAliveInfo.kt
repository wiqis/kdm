package xdman.network

import java.net.Socket

class KeepAliveInfo {
    var socket: Socket? = null
    var host: String? = null
    var port: Int = 0
    var lastUsed: Long = 0
}
