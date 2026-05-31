package xdman.downloaders

import java.util.ArrayList

class SegmentDetails {
    val segInfoList = ArrayList<SegmentInfo>()

    @get:Synchronized
    @set:Synchronized
    var chunkCount: Long = 0

    val chunkUpdates: ArrayList<SegmentInfo>
        get() = segInfoList

    @Synchronized
    fun extend(len: Int) {
        for (i in 0 until len) {
            segInfoList.add(SegmentInfo())
        }
    }

    val capacity: Int
        get() = segInfoList.size
}
