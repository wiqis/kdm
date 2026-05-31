package xdman.downloaders.hls

interface HlsEncryptedSouce {
    fun hasKey(keyUrl: String?): Boolean
    fun setKey(keyUrl: String, data: ByteArray)
    fun getIV(url: String): String?
    fun getKey(keyUrl: String): ByteArray?
}
