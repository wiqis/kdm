package xdman.videoparser

import xdman.Config
import xdman.network.ProxyResolver
import xdman.util.Logger
import xdman.util.StringUtils
import xdman.util.XDMUtils
import java.io.File
import java.io.FileInputStream
import java.util.*

class YoutubeDLHandler(private val url: String, private val user: String, private val pass: String) {
    private var proc: Process? = null
    private var exitCode: Int = 0
    private var ydlLocation: String
    private var stop = false
    private val videos: ArrayList<YdlResponse.YdlVideo> = ArrayList()

    init {
        val ydlFile = File(
            Config.getInstance().dataFolder,
            if (System.getProperty("os.name").lowercase().contains("windows")) "youtube-dl.exe" else "youtube-dl"
        )
        ydlLocation = if (ydlFile.exists()) {
            ydlFile.absolutePath
        } else {
            File(
                XDMUtils.getJarFile()!!.parentFile,
                if (System.getProperty("os.name").lowercase().contains("windows")) "youtube-dl.exe" else "youtube-dl"
            ).absolutePath
        }
    }

    fun start() {
        val tmpError = File(Config.getInstance().temporaryFolder, UUID.randomUUID().toString())
        val tmpOutput = File(Config.getInstance().temporaryFolder, UUID.randomUUID().toString())
        var `in`: FileInputStream? = null
        try {
            val args = ArrayList<String>()
            args.add(ydlLocation)
            args.add("--no-warnings")
            args.add("-q")
            args.add("-i")
            args.add("-J")
            if (!(StringUtils.isNullOrEmptyOrBlank(user) || StringUtils.isNullOrEmptyOrBlank(pass))) {
                args.add("-u")
                args.add(user)
                args.add("-p")
                args.add(pass)
            }

            val webproxy = ProxyResolver.resolve(url)
            if (webproxy != null) {
                val sb = StringBuilder()
                val proxyUser = Config.getInstance().proxyUser
                val proxyPass = Config.getInstance().proxyPass
                if (!(StringUtils.isNullOrEmptyOrBlank(proxyUser) || StringUtils.isNullOrEmptyOrBlank(proxyPass))) {
                    sb.append("$proxyUser:$proxyPass")
                }
                val proxy = "http://" + webproxy.host
                val port = webproxy.port
                if (port > 0 && port != 80) {
                    sb.append(":$port")
                }
                if (sb.isNotEmpty()) {
                    sb.append("@")
                }
                sb.append(proxy)
                args.add("--proxy")
                args.add(sb.toString())
            }

            args.add(url)

            val pb = ProcessBuilder(args)
            for (i in args.indices) {
                Logger.log(args[i])
            }

            Logger.log("Writing JSON to: $tmpOutput")

            pb.redirectError(tmpError)
            pb.redirectOutput(tmpOutput)
            proc = pb.start()

            exitCode = proc!!.waitFor()
            if (!stop) {
                `in` = FileInputStream(tmpOutput)
                videos.addAll(YdlResponse.parse(`in`))
                Logger.log("video found: " + videos.size)
            }
        } catch (e: Exception) {
            Logger.log(e)
        } finally {
            try {
                `in`?.close()
            } catch (_: Exception) {
            }
            tmpError.delete()
            tmpOutput.delete()
        }
    }

    fun getExitCode(): Int = exitCode

    fun setExitCode(exitCode: Int) {
        this.exitCode = exitCode
    }

    fun getVideos(): ArrayList<YdlResponse.YdlVideo> = videos

    fun stop() {
        try {
            proc?.destroy()
        } catch (e: Exception) {
            Logger.log(e)
        }
    }
}
