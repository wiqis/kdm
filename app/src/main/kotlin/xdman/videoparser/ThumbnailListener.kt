package xdman.videoparser

interface ThumbnailListener {
    fun thumbnailsLoaded(key: Long, url: String, file: String)
}
