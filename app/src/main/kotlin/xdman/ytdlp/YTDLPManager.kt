package xdman.ytdlp

import xdman.Config
import xdman.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object YTDLPManager {
    private const val WIN_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe"
    private const val LINUX_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp"
    private const val MAC_URL = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_macos"

    fun getBinaryFile(): File {
        val os = System.getProperty("os.name").lowercase()
        val name = when {
            os.contains("win") -> "yt-dlp.exe"
            os.contains("mac") -> "yt-dlp_macos"
            else -> "yt-dlp"
        }
        return File(Config.getInstance().dataFolder, name)
    }

    fun isAvailable(): Boolean {
        val f = getBinaryFile()
        return f.exists() && f.canExecute()
    }

    fun getPath(): String = getBinaryFile().absolutePath

    private fun getDownloadUrl(): String {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> WIN_URL
            os.contains("mac") -> MAC_URL
            else -> LINUX_URL
        }
    }

    @Throws(Exception::class)
    fun downloadBinary(progressCallback: ((downloaded: Long, total: Long) -> Unit)? = null) {
        val url = URL(getDownloadUrl())
        val conn = url.openConnection()
        val totalSize = conn.contentLengthLong
        val target = getBinaryFile()
        target.parentFile.mkdirs()

        val buffer = ByteArray(8192)
        url.openStream().use { input ->
            FileOutputStream(target).use { output ->
                var read: Int
                var totalRead = 0L
                while (input.read(buffer).also { read = it } != -1) {
                    output.write(buffer, 0, read)
                    totalRead += read
                    progressCallback?.invoke(totalRead, totalSize)
                }
            }
        }

        target.setExecutable(true, false)
    }

    fun getVersion(): String? {
        return try {
            val proc = ProcessBuilder(getPath(), "--version")
                .redirectErrorStream(true)
                .start()
            proc.inputStream.bufferedReader().readLine()
        } catch (e: Exception) {
            null
        }
    }
}
