package xdman

interface DownloadListener {
    fun downloadFinished(id: String)

    fun downloadFailed(id: String)

    fun downloadStopped(id: String)

    fun downloadConfirmed(id: String)

    fun downloadUpdated(id: String)

    fun getOutputFolder(id: String): String

    fun getOutputFile(id: String, update: Boolean): String
}
