package xdman.network

import java.io.IOException
import java.net.Socket
import java.util.ArrayList

class KeepAliveConnectionCache private constructor() : Runnable {
    private val socketList: ArrayList<KeepAliveInfo> = ArrayList()
    private var stop = false
    private val MAX_KEEP_ALIVE_INT = 2000
    private var t: Thread? = null

    companion object {
        private var _this: KeepAliveConnectionCache? = null
        private val lock = Any()

        @JvmStatic
        fun getInstance(): KeepAliveConnectionCache {
            synchronized(lock) {
                if (_this == null) {
                    val inst = KeepAliveConnectionCache()
                    _this = inst
                    inst.start()
                }
                return _this!!
            }
        }
    }

    @Synchronized
    fun putSocket(socket: Socket?, host: String?, port: Int) {
        val info = KeepAliveInfo()
        info.lastUsed = System.currentTimeMillis()
        info.host = host
        info.port = port
        info.socket = socket
        socketList.add(info)
    }

    @Synchronized
    fun getReusableSocket(host: String?, port: Int): Socket? {
        val now = System.currentTimeMillis()
        var i = 0
        while (i < socketList.size) {
            val info = socketList[i]
            if (info.host == host && info.port == port) {
                if (now - info.lastUsed < MAX_KEEP_ALIVE_INT) {
                    socketList.removeAt(i)
                    return info.socket
                }
            }
            i++
        }
        return null
    }

    private fun scavengeCache() {
        val sockets2Close = ArrayList<Socket?>()
        synchronized(this) {
            var i = 0
            while (i < socketList.size) {
                val info = socketList[i]
                val now = System.currentTimeMillis()
                if (now - info.lastUsed >= MAX_KEEP_ALIVE_INT) {
                    socketList.removeAt(i)
                    sockets2Close.add(info.socket)
                } else {
                    i++
                }
            }
        }

        var i = 0
        while (i < socketList.size) {
            val info = socketList[i]
            val now = System.currentTimeMillis()
            if (now - info.lastUsed >= MAX_KEEP_ALIVE_INT) {
                socketList.removeAt(i)
                try {
                    info.socket?.close()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            } else {
                i++
            }
        }
    }

    override fun run() {
        while (!stop) {
            val lastrun = System.currentTimeMillis()
            scavengeCache()
            val now = System.currentTimeMillis()
            if (now - lastrun < MAX_KEEP_ALIVE_INT) {
                try {
                    Thread.sleep((MAX_KEEP_ALIVE_INT - (now - lastrun)).toLong())
                } catch (e: InterruptedException) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun start() {
        this.t = Thread(this)
        t?.start()
    }

    fun stop() {
        this.stop = true
        t?.interrupt()
    }
}
