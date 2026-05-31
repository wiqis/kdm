package xdman.videoparser

import xdman.Config
import xdman.network.http.JavaHttpClient
import xdman.util.Logger
import xdman.util.XDMUtils
import java.io.File
import java.io.FileOutputStream
import java.util.*

class ThumbnailDownloader(
    list: ArrayList<String>,
    private var listener: ThumbnailListener?,
    private val instanceKey: Long
) : Runnable {
    private var thumbnails: Array<String> = Array(list.size) { list[it] }
    private var t: Thread? = null
    @Volatile
    private var stop = false

    fun download() {
        t = Thread(this)
        t!!.start()
    }

    fun stop() {
        stop = true
        listener = null
    }

    fun removeThumbnailListener() {
        listener = null
    }

    override fun run() {
        val list = ArrayList<String>()
        try {
            if (thumbnails.isEmpty()) return
            for (i in thumbnails.indices) {
                if (stop) return
                val url = thumbnails[i]
                val file = downloadReal(url)
                if (stop) return
                if (file != null) {
                    listener?.thumbnailsLoaded(instanceKey, url, file)
                    list.add(file)
                }
            }
        } catch (e: Exception) {
            Logger.log(e)
        } finally {
            if (stop) {
                for (file in list) {
                    File(file).delete()
                }
            }
        }
    }

    private fun downloadReal(url: String): String? {
        val tmpFile = File(Config.getInstance().temporaryFolder, UUID.randomUUID().toString())
        var client: JavaHttpClient? = null
        var out: FileOutputStream? = null
        try {
            client = JavaHttpClient(url)
            client.setFollowRedirect(true)
            client.connect()
            val resp = client.statusCode
            if (stop) return null
            Logger.log("manifest download response: $resp")
            if (resp == 200 || resp == 206) {
                val input = client.inputStream
                val len = client.contentLength
                out = FileOutputStream(tmpFile)
                XDMUtils.copyStream(input, out, len)
                Logger.log("thumbnail download successfull")
                return tmpFile.absolutePath
            }
        } catch (e: Exception) {
            Logger.log(e)
        } finally {
            try {
                client?.dispose()
            } catch (_: Exception) {
            }
            try {
                out?.close()
            } catch (_: Exception) {
            }
            if (stop) {
                tmpFile.delete()
            }
        }
        return null
    }
}
