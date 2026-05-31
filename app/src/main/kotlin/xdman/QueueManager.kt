package xdman

import xdman.ui.res.StringResource
import xdman.util.Logger
import xdman.util.XDMUtils
import java.io.*
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.*

class QueueManager private constructor() {
    private val queueList: ArrayList<DownloadQueue> = ArrayList()

    companion object {
        private var _this: QueueManager? = null

        @JvmStatic
        fun getInstance(): QueueManager {
            if (_this == null) {
                _this = QueueManager()
            }
            return _this!!
        }
    }

    init {
        loadQueues()
    }

    fun getQueueById(queueId: String?): DownloadQueue? {
        if (queueId == null)
            return null
        if (queueId.length < 1) {
            return queueList[0]
        }
        for (i in queueList.indices) {
            val q = queueList[i]
            if (q.queueId == queueId) {
                return q
            }
        }
        return null
    }

    fun getQueueList(): ArrayList<DownloadQueue> {
        return queueList
    }

    fun getDefaultQueue(): DownloadQueue {
        return queueList[0]
    }

    private fun loadQueues() {
        val file = File(Config.getInstance().dataFolder, "queues.txt")
        val defaultQ = DownloadQueue("", StringResource.get("DEF_QUEUE") ?: "Default")
        queueList.add(defaultQ)
        if (!file.exists()) {
            return
        }

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd")

        try {
            BufferedReader(
                InputStreamReader(FileInputStream(file), Charset.forName("UTF-8"))
            ).use { reader ->
                var str = reader.readLine()
                val count = (str ?: "0").trim().toInt()
                for (i in 0 until count) {
                    var strLn = reader.readLine()
                        ?: throw IOException("Unexpected EOF")
                    val id = strLn.trim()
                    strLn = reader.readLine()
                        ?: throw IOException("Unexpected EOF")
                    val name = strLn.trim()
                    val queue = if ("" == id) {
                        defaultQ
                    } else {
                        DownloadQueue(id, name)
                    }
                    val c = XDMUtils.readLineSafe(reader).trim().toInt()
                    for (j in 0 until c) {
                        queue.queuedItems.add(XDMUtils.readLineSafe(reader).trim())
                    }
                    val hasStartTime = reader.readLine().toInt() == 1
                    if (hasStartTime) {
                        queue.startTime = reader.readLine().toLong()
                        val hasEndTime = reader.readLine().toInt() == 1
                        if (hasEndTime) {
                            queue.endTime = reader.readLine().toLong()
                        }
                        val isPeriodic = reader.readLine().toInt() == 1
                        queue.isPeriodic = isPeriodic
                        if (isPeriodic) {
                            queue.dayMask = reader.readLine().toInt()
                        } else {
                            if (reader.readLine().toInt() == 1) {
                                val ln = reader.readLine()
                                if (ln != null)
                                    queue.execDate = dateFormatter.parse(ln)
                            }
                        }
                    }
                    if (queue.queueId.length > 0) {
                        queueList.add(queue)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    fun saveQueues() {
        val count = queueList.size
        val file = File(Config.getInstance().dataFolder, "queues.txt")
        val newLine = System.getProperty("line.separator")
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd")
        var writer: BufferedWriter? = null
        try {
            writer = BufferedWriter(OutputStreamWriter(FileOutputStream(file), Charset.forName("UTF-8")))
            writer.write("$count$newLine")
            for (i in 0 until count) {
                val queue = queueList[i]
                writer.write("${queue.queueId}$newLine")
                writer.write("${queue.name}$newLine")
                val queuedItems = queue.queuedItems
                writer.write("${queuedItems.size}$newLine")
                for (j in queuedItems.indices) {
                    writer.write("${queuedItems[j]}$newLine")
                }
                if (queue.startTime != -1L) {
                    writer.write("1$newLine")
                    writer.write("${queue.startTime}$newLine")
                    if (queue.endTime != -1L) {
                        writer.write("1$newLine")
                        writer.write("${queue.endTime}$newLine")
                    } else {
                        writer.write("0$newLine")
                    }
                    writer.write("${if (queue.isPeriodic) 1 else 0}$newLine")
                    if (queue.isPeriodic) {
                        writer.write("${queue.dayMask}$newLine")
                    } else {
                        if (queue.execDate != null) {
                            writer.write("1$newLine")
                            writer.write("${dateFormatter.format(queue.execDate)}$newLine")
                        } else {
                            writer.write("0$newLine")
                        }
                    }
                } else {
                    writer.write("0$newLine")
                }
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
        if (writer != null) {
            try {
                writer.close()
            } catch (e: IOException) {
                Logger.log(e)
            }
        }
    }

    fun removeQueue(queueId: String) {
        val q = getQueueById(queueId) ?: return
        if (q.isRunning) {
            q.stop()
        }
        for (i in q.queuedItems.indices) {
            val id = q.queuedItems[i]
            val ent = XDMApp.getInstance().getEntry(id)
            if (ent != null) {
                ent.queueId = ""
            }
        }
        queueList.remove(q)
    }

    fun createNewQueue(): DownloadQueue {
        var counter = 1
        val name: String
        val qw = StringResource.get("Q_WORD")
        while (true) {
            var found = false
            counter++
            for (qi in queueList) {
                if ("" == qi.queueId)
                    continue
                if ("$qw $counter" == qi.name) {
                    found = true
                    break
                }
            }
            if (!found) {
                name = "$qw $counter"
                break
            }
        }
        val q = DownloadQueue(UUID.randomUUID().toString(), name)
        queueList.add(q)
        saveQueues()
        return q
    }

    fun fixCorruptEntries(ids: MutableIterator<String>, app: XDMApp) {
        val dfq = getDefaultQueue()
        while (ids.hasNext()) {
            val id = ids.next()
            val ent = app.getEntry(id)
            val qId = ent?.queueId
            if (qId == null || getQueueById(qId) == null) {
                dfq.queuedItems.add(id)
                ent?.queueId = ""
            }
        }
        for (i in queueList.indices) {
            val q = queueList[i]
            val corruptIds = ArrayList<String>()
            for (k in q.queuedItems.indices) {
                val id = q.queuedItems[k]
                if (app.getEntry(id) == null) {
                    corruptIds.add(id)
                }
            }
            q.queuedItems.removeAll(corruptIds)
        }
    }
}
