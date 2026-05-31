package xdman

interface ListChangeListener {
    fun listChanged()

    fun listItemUpdated(id: String)
}
