package xdman.mediaconversion

import xdman.Config
import xdman.util.Logger
import xdman.util.XDMUtils
import java.io.File
import java.nio.charset.Charset
import java.nio.file.Files
import java.util.UUID
import java.util.regex.Pattern
import javax.swing.ImageIcon

class MediaInfoExtractor {
    private val pattern1: Pattern = Pattern.compile("Duration:\\s+([0-9]+:[0-9]+:[0-9]+)")
    private val pattern2: Pattern = Pattern.compile("Stream .*, ([0-9]+x[0-9]+)")
    @Volatile
    private var stop: Boolean = false
    private var proc: Process? = null

    fun stop() {
        stop = true
        if (proc != null) {
            try {
                proc?.destroy()
            } catch (_: Exception) {
            }
        }
    }

    fun getInfo(file: String): MediaFormatInfo? {
        val f = File(file)
        val tmpOutput = File(Config.getInstance().temporaryFolder, UUID.randomUUID().toString())
        val tmpImgFile = File(Config.getInstance().temporaryFolder, UUID.randomUUID().toString() + ".jpg")
        if (!f.exists()) return null

        var ffFile = File(
            Config.getInstance().dataFolder,
            if (System.getProperty("os.name").lowercase().contains("windows")) "ffmpeg.exe" else "ffmpeg"
        )
        if (!ffFile.exists()) {
            ffFile = File(
                XDMUtils.getJarFile()!!.parentFile,
                if (System.getProperty("os.name").lowercase().contains("windows")) "ffmpeg.exe" else "ffmpeg"
            )
            if (!ffFile.exists()) return null
        }
        if (stop) return null

        try {
            val args = ArrayList<String>()
            args.add(ffFile.absolutePath)
            args.add("-i")
            args.add(f.absolutePath)
            args.add("-vf")
            args.add("scale=64:-1")
            args.add("-vframes")
            args.add("1")
            args.add("-f")
            args.add("image2")
            args.add(tmpImgFile.absolutePath)
            args.add("-y")

            val str2 = args.joinToString(" ") { " $it" }
            println(str2)

            val pb = ProcessBuilder(args)
            pb.redirectError(tmpOutput)
            proc = pb.start()

            val ret = proc!!.waitFor()
            println("ret: $ret")
            if (stop) return null

            val info = MediaFormatInfo()
            info.thumbnail = ImageIcon(tmpImgFile.absolutePath)
            val array = Files.readAllBytes(tmpOutput.toPath())
            val str = String(array, Charset.forName("utf-8"))
            println(str)

            val matcher1 = pattern1.matcher(str)
            val matcher2 = pattern2.matcher(str)
            if (matcher1.find()) {
                info.duration = matcher1.group(1)
                println("Match: ${info.duration}")
            } else {
                println("no match")
            }
            if (matcher2.find()) {
                info.resolution = matcher2.group(1)
                println("Match: ${info.resolution}")
            }
            if (stop) return null
            return info
        } catch (e: Exception) {
            Logger.log(e)
        } finally {
            tmpOutput.delete()
            tmpImgFile.delete()
        }
        return null
    }
}
