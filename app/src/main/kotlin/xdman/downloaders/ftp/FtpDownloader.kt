package xdman.downloaders.ftp

import xdman.XDMConstants
import xdman.downloaders.AbstractChannel
import xdman.downloaders.Segment
import xdman.downloaders.SegmentDownloader
import xdman.downloaders.metadata.HttpMetadata

class FtpDownloader(id: String, folder: String, private var _metadata: HttpMetadata) : SegmentDownloader(id, folder) {
    override fun createChannel(segment: Segment): AbstractChannel {
        return FtpChannel(segment, _metadata.url)
    }

    override val type: Int
        get() = XDMConstants.FTP

    override val isFileNameChanged: Boolean
        get() = false

    override val newFile: String
        get() = ""

    override val metadata: HttpMetadata?
        get() = _metadata

    override fun chunkConfirmed(c: Segment) {
    }
}
