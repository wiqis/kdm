package xdman.downloaders

import java.util.Comparator

class SegmentComparator : Comparator<Segment> {
    override fun compare(c1: Segment, c2: Segment): Int {
        return when {
            c1.startOffset > c2.startOffset -> 1
            c1.startOffset < c2.startOffset -> -1
            else -> 0
        }
    }
}
