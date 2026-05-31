package xdman

import xdman.downloaders.metadata.HttpMetadata
import xdman.util.Logger
import xdman.util.StringUtils
import xdman.util.XDMUtils
import java.net.URL

class ClipboardMonitor : Runnable {
    private var lastContent: String? = null
    private var t: Thread? = null

    companion object {
        private var _this: ClipboardMonitor? = null

        @JvmStatic
        fun getInstance(): ClipboardMonitor {
            if (_this == null) {
                _this = ClipboardMonitor()
            }
            return _this!!
        }
    }

    fun startMonitoring() {
        try {
            if (t == null) {
                t = Thread(this)
                t!!.start()
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    fun stopMonitoring() {
        try {
            if (t != null && t!!.isAlive) {
                t!!.interrupt()
                t = null
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    override fun run() {
        try {
            while (true) {
                val txt = XDMUtils.getClipBoardText()
                if (StringUtils.isNullOrEmptyOrBlank(txt)) {
                    return
                }
                if (txt != lastContent) {
                    Logger.log("New content: $txt")
                    lastContent = txt
                    try {
                        URL(txt)
                        val md = HttpMetadata()
                        md.url = txt
                        val file = XDMUtils.getFileName(txt)
                        val ext = XDMUtils.getExtension(file)
                        val extUpper = if (!StringUtils.isNullOrEmptyOrBlank(ext)) ext!!.uppercase().replace(".", "") else ""

                        val arr = Config.getInstance().fileExts
                        var found = false
                        if (arr != null) {
                            for (s in arr) {
                                if (s.contains(extUpper)) {
                                    found = true
                                    break
                                }
                            }
                        }
                        if (found) {
                            XDMApp.getInstance().addDownload(md, file)
                        }
                    } catch (_: Exception) {
                    }
                }
                Thread.sleep(1000)
            }
        } catch (e: Exception) {
            Logger.log(e)
        }
    }
}
