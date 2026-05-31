package xdman.downloaders.metadata

import xdman.XDMConstants
import xdman.util.Logger

class HdsMetadata : HttpMetadata {
    var bitRate: Int = 0

    constructor() : super()

    constructor(id: String) : super(id)

    override val type: Int
        get() = XDMConstants.HDS

    override fun derive(): HttpMetadata {
        Logger.log("derive hds metadata")
        val md = HdsMetadata()
        md.headers = headers
        md.url = url
        md.bitRate = bitRate
        return md
    }
}
