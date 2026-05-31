package xdman.mediaconversion

import xdman.Config
import xdman.util.Logger
import xdman.util.StringUtils
import xdman.util.XDMUtils
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.ArrayList

class FFmpeg(
    private val inputFiles: List<String>,
    private val outputFile: String,
    private val listener: MediaConversionListener?,
    private val outformat: MediaFormat?,
    private val copy: Boolean
) {
    var hls: Boolean = false
    var volume: String? = null
    var useHwAccel: Boolean = false

    private var totalDuration: Long = 0
    private var proc: Process? = null
    var ffExitCode: Int = 0
        private set

    fun convert(): Int {
        try {
            Logger.log("Outformat: " + outformat)

            var ffFile = File(
                Config.getInstance().dataFolder,
                if (System.getProperty("os.name").lowercase().contains("windows")) "ffmpeg.exe" else "ffmpeg"
            )
            if (!ffFile.exists()) {
                ffFile = File(
                    XDMUtils.getJarFile()!!.parentFile,
                    if (System.getProperty("os.name").lowercase().contains("windows")) "ffmpeg.exe" else "ffmpeg"
                )
                if (!ffFile.exists()) {
                    return FF_NOT_FOUND
                }
            }

            val args = ArrayList<String>()
            args.add(ffFile.absolutePath)

            if (useHwAccel) {
                args.add("-hwaccel")
                args.add("auto")
            }

            if (hls) {
                args.add("-f")
                args.add("concat")
                args.add("-safe")
                args.add("0")
            }

            for (i in inputFiles.indices) {
                args.add("-i")
                args.add(inputFiles[i])
            }

            if (copy) {
                args.add("-acodec")
                args.add("copy")
                args.add("-vcodec")
                args.add("copy")
            } else {
                outformat?.let { fmt ->
                    if (fmt.resolution != null) {
                        args.add("-s")
                        args.add(fmt.resolution!!)
                    }
                    if (fmt.video_codec != null) {
                        args.add("-vcodec")
                        args.add(fmt.video_codec!!)
                    }
                    if (fmt.video_bitrate != null) {
                        args.add("-b:v")
                        args.add(fmt.video_bitrate!!)
                    }
                    if (fmt.framerate != null) {
                        args.add("-r")
                        args.add(fmt.framerate!!)
                    }
                    if (fmt.aspectRatio != null) {
                        args.add("-aspect")
                        args.add(fmt.aspectRatio!!)
                    }
                    if (fmt.video_param_extra != null) {
                        val arr = fmt.video_param_extra!!.split(" ").toTypedArray()
                        if (arr.isNotEmpty()) {
                            args.addAll(listOf(*arr))
                        }
                    } else {
                        if ("libx264" == fmt.video_codec) {
                            args.add("-profile:v")
                            args.add("baseline")
                        }
                    }

                    if (fmt.audio_codec != null) {
                        args.add("-acodec")
                        args.add(fmt.audio_codec!!)
                    }
                    if (fmt.audio_bitrate != null) {
                        args.add("-b:a")
                        args.add(fmt.audio_bitrate!!)
                    }
                    if (isNumeric(fmt.samplerate)) {
                        args.add("-ar")
                        args.add(fmt.samplerate!!)
                    }
                    if (isNumeric(fmt.audio_channel)) {
                        args.add("-ac")
                        args.add(fmt.audio_channel!!)
                    }
                    if (fmt.audio_extra_param != null) {
                        val arr = fmt.audio_extra_param!!.split(" ").toTypedArray()
                        if (arr.isNotEmpty()) {
                            args.addAll(listOf(*arr))
                        }
                    }
                }
                if (volume != null) {
                    args.add("-filter:a")
                    args.add("volume=$volume")
                }
            }

            args.add(outputFile)
            args.add("-y")

            for (s in args) {
                Logger.log("@ffmpeg_args: $s")
            }

            val pb = ProcessBuilder(args)
            pb.redirectErrorStream(true)
            proc = pb.start()

            val br = BufferedReader(InputStreamReader(proc!!.inputStream), 1024)
            while (true) {
                val ln = br.readLine() ?: break
                try {
                    val text = ln.trim()
                    processOutput(text)
                } catch (e: Exception) {
                    Logger.log(e)
                }
            }

            ffExitCode = proc!!.waitFor()
            return if (ffExitCode == 0) FF_SUCCESS else FF_CONVERSION_FAILED
        } catch (e: Exception) {
            return FF_LAUNCH_ERROR
        }
    }

    fun setHLSDuration(totalDuration: Float) {
        this.totalDuration = totalDuration.toLong()
    }

    private fun parseDuration(dur: String): Long {
        var duration: Long = 0
        val arr = dur.split(":")
        var s = arr[0].trim()
        if (!StringUtils.isNullOrEmpty(s)) {
            duration = s.toInt(10) * 3600L
        }
        s = arr[1].trim()
        if (!StringUtils.isNullOrEmpty(s)) {
            duration += arr[1].trim().toInt(10) * 60L
        }
        s = arr[2].split("\\.").toTypedArray()[0].trim()
        if (!StringUtils.isNullOrEmpty(s)) {
            duration += s.toInt(10)
        }
        return duration
    }

    private fun processOutput(text: String) {
        if (StringUtils.isNullOrEmpty(text)) return
        if (totalDuration > 0) {
            if (text.startsWith("frame=") && text.contains("time=")) {
                var index1 = text.indexOf("time")
                index1 = text.indexOf('=', index1)
                val index2 = text.indexOf("bitrate=")
                val dur = text.substring(index1 + 1, index2).trim()
                Logger.log("Parsing duration: $dur")
                val t = parseDuration(dur)
                Logger.log("Duration: $t Total duration: $totalDuration")
                val prg = ((t * 100) / totalDuration).toInt()
                Logger.log("ffmpeg prg: $prg")
                listener?.progress(prg)
            }
        }

        if (totalDuration == 0L) {
            if (text.startsWith("Duration:")) {
                try {
                    var index1 = text.indexOf("Duration")
                    index1 = text.indexOf(':', index1)
                    val index2 = text.indexOf(",", index1)
                    val dur = text.substring(index1 + 1, index2).trim()
                    Logger.log("Parsing duration: $dur")
                    totalDuration = parseDuration(dur)
                    Logger.log("Total duration: $totalDuration")
                } catch (e: Exception) {
                    Logger.log(e)
                    totalDuration = -1
                }
            }
        }
    }

    fun stop() {
        try {
            if (proc?.isAlive == true) {
                proc?.destroy()
            }
        } catch (_: Exception) {
        }
    }

    private fun isNumeric(s: String?): Boolean {
        return try {
            s?.let { java.lang.Double.parseDouble(it) }
            true
        } catch (e: Exception) {
            false
        }
    }

    companion object {
        const val FF_NOT_FOUND = 10
        const val FF_LAUNCH_ERROR = 20
        const val FF_CONVERSION_FAILED = 30
        const val FF_SUCCESS = 0
    }
}
