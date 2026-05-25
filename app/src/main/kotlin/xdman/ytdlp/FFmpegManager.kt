package xdman.ytdlp

import xdman.Config
import xdman.util.Logger
import java.io.File
import java.io.FileOutputStream
import java.net.URL

object FFmpegManager {
    // Single binary ffmpeg static builds from ffmpeg-static project
    private val downloadUrls = mapOf(
        "linux" to "https://github.com/eugeneware/ffmpeg-static/releases/download/6.0/linux-x64",
        "win" to "https://github.com/eugeneware/ffmpeg-static/releases/download/6.0/win32-x64",
        "mac" to "https://github.com/eugeneware/ffmpeg-static/releases/download/6.0/darwin-x64"
    )

    fun getBinaryFile(): File? {
        val os = osName()
        if (os == null) return null
        val name = if (os == "win") "ffmpeg.exe" else "ffmpeg"
        return File(Config.getInstance().dataFolder, name)
    }

    fun isAvailable(): Boolean {
        getBinaryFile()?.let { f -> if (f.exists() && f.canExecute()) return true }

        try {
            val proc = ProcessBuilder("ffmpeg", "-version").redirectErrorStream(true).start()
            if (proc.waitFor() == 0) return true
        } catch (_: Exception) {}

        return false
    }

    fun getVersion(): String? {
        try {
            val path = getBinaryFile()?.absolutePath ?: "ffmpeg"
            val proc = ProcessBuilder(path, "-version").redirectErrorStream(true).start()
            val firstLine = proc.inputStream.bufferedReader().readLine() ?: return null
            return firstLine.substringAfter("ffmpeg version ").substringBefore(" ").takeIf { it.isNotBlank() }
                ?: firstLine.take(40)
        } catch (e: Exception) {
            return null
        }
    }

    @Throws(Exception::class)
    fun downloadBinary(progressCallback: ((downloaded: Long, total: Long) -> Unit)? = null) {
        val os = osName() ?: throw UnsupportedOperationException("Unsupported OS")
        val urlStr = downloadUrls[os] ?: throw UnsupportedOperationException("No binary for $os")
        val target = getBinaryFile() ?: throw UnsupportedOperationException("Cannot determine binary path")
        target.parentFile.mkdirs()

        val url = URL(urlStr)
        val conn = url.openConnection()
        val totalSize = conn.contentLengthLong
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

    private fun osName(): String? {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("win") -> "win"
            os.contains("mac") -> "mac"
            os.contains("linux") -> "linux"
            else -> null
        }
    }
}
