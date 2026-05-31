package xdman.network.http

class WebProxy {
    var host: String = ""
    var port: Int = 0
    var isSocks: Boolean = false

    constructor()
    constructor(host: String, port: Int) {
        this.host = host
        this.port = port
    }
}
