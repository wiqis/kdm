package xdman.mediaconversion

class FormatGroup {
    var name: String? = null
    var desc: String? = null
    var formats: MutableList<Format> = ArrayList()

    override fun toString(): String = desc ?: ""
}
