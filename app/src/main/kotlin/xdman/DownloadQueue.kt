package xdman

import xdman.util.Logger
import java.util.*

class DownloadQueue(id: String, name: String) {
    var isRunning = false
        private set
    var queueId: String = id
    var name: String = name
    val queuedItems: ArrayList<String> = ArrayList()
    private var currentItemId: String? = null
    var startTime: Long = -1
    var endTime: Long = -1
    var isPeriodic: Boolean = false
    var execDate: Date? = null
    var dayMask: Int = 0
    private var index = 0

    fun start() {
        if (isRunning) return
        index = 0
        isRunning = true
        next()
    }

    fun stop() {
        isRunning = false
        val app = XDMApp.getInstance()
        for (i in queuedItems.indices) {
            val id = queuedItems[i]
            val ent = app.getEntry(id)
                val state = ent?.state
                if (state == XDMConstants.FAILED || state == XDMConstants.FINISHED || state == XDMConstants.PAUSED) {
                    continue
                } else {
                    app.pauseDownload(id)
                }
        }
    }

    @Synchronized
    fun next() {
        Logger.log("$queueId attmpting to process next item")
        if (!isRunning) return
        val app = XDMApp.getInstance()
        if (queuedItems == null) return
        if (app.queueItemPending(queueId)) {
            Logger.log("$queueId not processing as has already pending download")
            return
        }
        if (currentItemId != null) {
            val ent = app.getEntry(currentItemId!!)
            if (ent != null) {
                val state = ent.state
                if (state != XDMConstants.FAILED && state != XDMConstants.PAUSED && state != XDMConstants.FINISHED) {
                    Logger.log("$queueId not processing as has already active download")
                    return
                }
            }
        }
        Logger.log("$queueId total queued ${queuedItems.size}")
        if (!(index < queuedItems.size)) {
            index = 0
        }
        var c = 0
        while (index < queuedItems.size) {
            val id = queuedItems[index]
            val ent = app.getEntry(id)
            if (ent != null) {
                val state = ent.state
                if (state == XDMConstants.FAILED || state == XDMConstants.PAUSED) {
                    Logger.log("index: $index c: $c")
                    currentItemId = id
                    index++
                    ent.isStartedByUser = false
                    XDMApp.getInstance().resumeDownload(id, false)
                    return
                }
            }
            index++
        }
    }

    fun removeFromQueue(id: String) {
        val app = XDMApp.getInstance()
        var c: Int
        for (i in queuedItems.indices) {
            if (queuedItems[i] == id) {
                c = i
                if (c <= index) {
                    index--
                }
                queuedItems.removeAt(i)
                if (id == currentItemId) {
                    currentItemId = null
                }
                val ent = app.getEntry(id)
                if (ent != null) {
                    ent.queueId = ""
                }
                QueueManager.getInstance().saveQueues()
                return
            }
        }
    }

    fun addToQueue(id: String) {
        if (!queuedItems.contains(id)) {
            Logger.log("$id added to $queueId")
            queuedItems.add(id)
            val ent = XDMApp.getInstance().getEntry(id)
            if (ent != null) {
                ent.queueId = queueId
            }
        }
        QueueManager.getInstance().saveQueues()
    }

    fun setQueuedItems(queuedItems: ArrayList<String>) {
        this.queuedItems.clear()
        this.queuedItems.addAll(queuedItems)
    }

    @Synchronized
    fun reorderItems(newOrder: ArrayList<String>) {
        val newList = ArrayList<String>()
        for (s in newOrder) {
            newList.add(s)
        }
        for (id in this.queuedItems) {
            if (!newList.contains(id)) {
                newList.add(id)
            }
        }
        this.queuedItems.clear()
        this.queuedItems.addAll(newList)
    }

    fun hasPendingItems(): Boolean {
        if (!isRunning) {
            return false
        }
        for (id in queuedItems) {
            val ent = XDMApp.getInstance().getEntry(id)
            if (ent != null) {
                if (ent.state != XDMConstants.FINISHED) {
                    return true
                }
            }
        }
        return false
    }

    override fun toString(): String {
        return name
    }
}
