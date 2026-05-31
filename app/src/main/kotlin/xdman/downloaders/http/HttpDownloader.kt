package xdman.downloaders.http

import xdman.XDMConstants
import xdman.downloaders.AbstractChannel
import xdman.downloaders.Segment
import xdman.downloaders.SegmentDownloader
import xdman.downloaders.metadata.HttpMetadata
import xdman.util.*

class HttpDownloader(id: String, folder: String, private val _metadata: HttpMetadata) : SegmentDownloader(id, folder) {
    private var newFileName: String? = null
    private var isJavaClientRequiredLocal: Boolean = false

    override fun createChannel(segment: Segment): AbstractChannel {
        val buf = StringBuffer()
        _metadata.headers.appendToBuffer(buf)
        println("Headers all: $buf")
        return HttpChannel(segment, _metadata.url, _metadata.headers, length, isJavaClientRequiredLocal)
    }

    override val type: Int
        get() = XDMConstants.HTTP

    override val isFileNameChanged: Boolean
        get() {
            Logger.log("Checking for filename change ${newFileName != null}")
            return newFileName != null
        }

    override val newFile: String
        get() = newFileName ?: ""

    override fun chunkConfirmed(c: Segment) {
        val oldFileName = getOutputFileName(false)
        val hc = c.channel as HttpChannel
        this.isJavaClientRequiredLocal = hc.isJavaClientRequired()
        super.getLastModifiedDate(c)
        if (hc.isRedirected()) {
            _metadata.url = hc.redirectUrl ?: _metadata.url
            _metadata.save()
        }

        if ((hc.getHeader("content-type") ?: "").contains("text/html")) {
            if (hc.getHeader("content-disposition") == null) {
                newFileName = XDMUtils.getFileNameWithoutExtension(oldFileName) + ".html"
                outputFormat = 0
            }
        }

        var nameSet = false
        val contentDispositionHeader = hc.getHeader("content-disposition")
        if (contentDispositionHeader != null) {
            if (outputFormat == 0) {
                println("checking content disposition")
                val name = NetUtils.getNameFromContentDisposition(contentDispositionHeader)
                if (name != null) {
                    this.newFileName = name
                    nameSet = true
                    Logger.log("set new filename: $newFileName")
                }
            }
        }

        if (!nameSet) {
            val ext = XDMUtils.getExtension(oldFileName)
            if (StringUtils.isNullOrEmptyOrBlank(ext)) {
                val newExt = MimeUtil.getFileExt(hc.getHeader("content-type"))
                if (newExt != null) {
                    newFileName = "$oldFileName.$newExt"
                }
            }
            Logger.log("new filename: $newFileName")
        }
    }

    override val metadata: HttpMetadata
        get() = this._metadata
}
