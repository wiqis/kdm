package xdman

import xdman.util.DateTimeUtils
import xdman.util.Logger
import xdman.util.UpdateChecker
import xdman.util.XDMUtils
import java.util.*

class QueueScheduler private constructor() : Runnable {
    private var lastKeepAwakePing = 0L

    companion object {
        private var _this: QueueScheduler? = null

        @JvmStatic
        fun getInstance(): QueueScheduler {
            if (_this == null) {
                _this = QueueScheduler()
            }
            return _this!!
        }
    }

    fun start() {
        lastKeepAwakePing = System.currentTimeMillis()
        Thread(this).start()
    }

    override fun run() {
        var lastUpdateChecked = 0L
        try {
            val cal = Calendar.getInstance()
            while (true) {
                try {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastKeepAwakePing > 3000) {
                        if (!XDMApp.getInstance().isAllFinished()) {
                            XDMUtils.keepAwakePing()
                            lastKeepAwakePing = currentTime
                        }
                    }

                    val queues = QueueManager.getInstance().getQueueList()
                    for (i in queues.indices) {
                        val queue = queues[i]
                        if (queue.isRunning || queue.startTime == -1L) {
                            continue
                        }
                        val now = Date()
                        cal.time = now
                        val onlyDate = DateTimeUtils.getDatePart(cal)
                        val seconds = DateTimeUtils.getTimePart(now)

                        if (seconds > queue.startTime) {
                            if (queue.endTime > 0) {
                                if (queue.endTime < seconds) {
                                    continue
                                }
                            }
                        } else {
                            continue
                        }

                        if (queue.isPeriodic) {
                            val day = cal[Calendar.DAY_OF_WEEK]
                            val mask = 0x01 shl day - 1
                            if (queue.dayMask and mask != mask) {
                                continue
                            }
                        } else {
                            val execDate = queue.execDate
                            if (execDate == null) {
                                continue
                            }
                            cal.time = execDate
                            val onlyDate2 = DateTimeUtils.getDatePart(cal)
                            if (onlyDate.compareTo(onlyDate2) < 0) {
                                continue
                            }
                        }
                        queue.start()
                    }

                    for (i in queues.indices) {
                        val queue = queues[i]
                        if (!queue.isRunning) {
                            continue
                        }
                        if (queue.endTime < 1) {
                            continue
                        }
                        val now = Date()
                        val seconds = DateTimeUtils.getTimePart(now)
                        if (queue.endTime < seconds) {
                            queue.stop()
                        }
                    }
                    Thread.sleep(1000)
                } catch (e2: Exception) {
                    Logger.log("error in scheduler: $e2")
                    Logger.log(e2)
                }

                val now = System.currentTimeMillis()
                if (now - lastUpdateChecked > 3600 * 1000) {
                    val stat = UpdateChecker.getUpdateStat()
                    when (stat) {
                        UpdateChecker.NO_UPDATE_AVAILABLE -> {}
                        UpdateChecker.APP_UPDATE_AVAILABLE -> XDMApp.getInstance().notifyAppUpdate()
                        UpdateChecker.COMP_NOT_INSTALLED -> XDMApp.getInstance().notifyComponentInstall()
                        UpdateChecker.COMP_UPDATE_AVAILABLE -> XDMApp.getInstance().notifyComponentUpdate()
                    }
                }
                lastUpdateChecked = now
            }
        } catch (e: Exception) {
            Logger.log("error in scheduler: $e")
            Logger.log(e)
        }
    }
}
