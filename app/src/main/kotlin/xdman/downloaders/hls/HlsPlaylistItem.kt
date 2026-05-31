package xdman.downloaders.hls

class HlsPlaylistItem {
    var url: String? = null
    var keyUrl: String? = null
    var IV: String? = null
    var resolution: String? = null
    var bandwidth: String? = null
    var duration: String? = null

    constructor()

    constructor(url: String?, keyUrl: String?, iV: String?, resolution: String?, bandwidth: String?, duration: String?) {
        this.url = url
        this.keyUrl = keyUrl
        this.IV = iV
        this.resolution = resolution
        this.bandwidth = bandwidth
        this.duration = duration
    }

    override fun toString(): String {
        return "url: $url\nduration: $duration\nbandwidth: $bandwidth\nresolution: $resolution\nkeyUrl: $keyUrl\nIV: $IV"
    }
}
