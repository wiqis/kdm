package xdman.network

abstract class WebRequest : Runnable {
    protected abstract fun open(): Boolean

    override fun run() {
    }
}
