package xdman.downloaders.metadata

import xdman.XDMConstants
import xdman.util.Logger

class HlsMetadata : HttpMetadata {
    constructor() : super()

    constructor(id: String) : super(id)

    override val type: Int
        get() = XDMConstants.HLS

    override fun derive(): HttpMetadata {
        Logger.log("derive hls metadata")
        val md = HlsMetadata()
        md.headers = headers
        md.url = url
        return md
    }
}
