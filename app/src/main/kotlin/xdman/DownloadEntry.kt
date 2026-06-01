package xdman

import xdman.util.*

class DownloadEntry {
    var id: String = ""
    var file: String? = null
    var folder: String = ""
    var state: Int = 0
    var category: Int = 0
    var size: Long = 0
    var downloaded: Long = 0
    var date: Long = 0
        set(value) {
            field = value
            this.dateStr = FormatUtilities.formatDate(value)
        }
    var progress: Int = 0
    var dateStr: String = ""
    var queueId: String? = ""
    var isStartedByUser: Boolean = false
    var outputFormatIndex: Int = 0 // 0 original
    var scheduledTime: Long = 0 // 0 = no schedule, otherwise epoch millis to start
    private var _tempFolder: String? = null
    var tempFolder: String
        get() {
            if (StringUtils.isNullOrEmptyOrBlank(_tempFolder)) {
                _tempFolder = Config.getInstance().temporaryFolder
            }
            return _tempFolder!!
        }
        set(value) { _tempFolder = value }

    constructor()
}
