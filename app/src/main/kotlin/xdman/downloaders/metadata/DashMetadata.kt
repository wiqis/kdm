package xdman.downloaders.metadata

import xdman.XDMConstants
import xdman.network.http.HeaderCollection
import xdman.util.Logger

class DashMetadata : HttpMetadata {
    var url2: String? = null
    var len1: Long = 0
    var len2: Long = 0
    var headers2: HeaderCollection? = null

    constructor() : super() {
        headers2 = HeaderCollection()
    }

    constructor(id: String) : super(id) {
        headers2 = HeaderCollection()
    }

    override fun derive(): HttpMetadata {
        Logger.log("derive dash metadata")
        val md = DashMetadata()
        md.headers = headers
        md.headers2 = headers2
        md.url = url
        md.url2 = url2
        md.len1 = len1
        md.len2 = len2
        return md
    }

    override val type: Int
        get() = XDMConstants.DASH
}
