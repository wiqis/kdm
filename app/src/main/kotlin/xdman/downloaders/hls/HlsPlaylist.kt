package xdman.downloaders.hls

class HlsPlaylist {
    var isMaster: Boolean = false
    var isEncrypted: Boolean = false
    var items: MutableList<HlsPlaylistItem>? = null
    var duration: Float = 0f

    override fun toString(): String {
        return items?.joinToString("\n") { it.toString() } ?: ""
    }
}
