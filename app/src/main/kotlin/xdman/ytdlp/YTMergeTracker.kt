package xdman.ytdlp

import xdman.*
import xdman.util.Logger
import java.io.File
import java.util.concurrent.TimeUnit

object YTMergeTracker : ListChangeListener {
    private val pendingMerges = mutableMapOf<String, MergeJob>()
    private val mergingNow = mutableSetOf<String>()
    private val ffmpegOutput = mutableMapOf<String, String>()

    data class MergeJob(
        val baseName: String,
        val videoFile: String,
        val audioFile: String,
        val outputFile: String,
        val videoDownloadId: String,
        val audioDownloadId: String,
        val videoFormatId: String,
        val audioFormatId: String,
        val tempFolder: String,
        val outputExt: String = "mp4"
    )

    var onMergeStart: ((baseName: String) -> Unit)? = null
    var onMergeEvent: ((baseName: String, success: Boolean, mergedFilePath: String?) -> Unit)? = null
    var onMergeOutput: ((baseName: String, line: String) -> Unit)? = null

    fun registerMerge(
        baseName: String,
        videoDownloadId: String,
        audioDownloadId: String,
        videoExt: String,
        audioExt: String,
        tempFolder: String,
        outputFolder: String
    ) {
        val videoFile = "$tempFolder/${baseName}_v.$videoExt"
        val audioFile = "$tempFolder/${baseName}_a.$audioExt"
        val outputExt = if (videoExt in listOf("mp4", "m4a", "mov")) "mp4" else videoExt
        val outputFile = "$outputFolder/$baseName.$outputExt"

        pendingMerges[baseName] = MergeJob(
            baseName = baseName,
            videoFile = videoFile,
            audioFile = audioFile,
            outputFile = outputFile,
            videoDownloadId = videoDownloadId,
            audioDownloadId = audioDownloadId,
            videoFormatId = videoExt,
            audioFormatId = audioExt,
            tempFolder = tempFolder,
            outputExt = outputExt
        )
        Logger.log("YTMergeTracker: registered merge for $baseName (video=$videoDownloadId, audio=$audioDownloadId)")
    }

    /**
     * Re-register a pending merge from a loaded CombinedYTDownload.
     * Call this on app restart to resume any stuck merges.
     */
    fun reRegister(combined: CombinedYTDownload) {
        val vidExt = combined.videoExt.ifEmpty { "mp4" }
        val audExt = (combined.audioExt ?: "m4a").ifEmpty { "m4a" }
        val baseName = combined.combinedId
        val videoFile = "${combined.tempFolder}/${baseName}_v.$vidExt"
        val audioFile = "${combined.tempFolder}/${baseName}_a.$audExt"
        val outputExt = if (vidExt in listOf("mp4", "m4a", "mov")) "mp4" else vidExt
        val outputFile = "${combined.outputFolder}/$baseName.$outputExt"

        pendingMerges[baseName] = MergeJob(
            baseName = baseName,
            videoFile = videoFile,
            audioFile = audioFile,
            outputFile = outputFile,
            videoDownloadId = combined.videoEntryId,
            audioDownloadId = combined.audioEntryId ?: "",
            videoFormatId = vidExt,
            audioFormatId = audExt,
            tempFolder = combined.tempFolder,
            outputExt = outputExt
        )
        Logger.log("YTMergeTracker: re-registered merge for $baseName")

        // Trigger check for both entries if they're already finished
        listItemUpdated(combined.videoEntryId)
        combined.audioEntryId?.let { listItemUpdated(it) }
    }

    fun getMergeJob(videoId: String, audioId: String): MergeJob? {
        return pendingMerges.values.find { it.videoDownloadId == videoId && it.audioDownloadId == audioId }
    }

    fun getFfmpegOutput(baseName: String): String = ffmpegOutput[baseName] ?: ""

    override fun listChanged() {}

    override fun listItemUpdated(id: String) {
        checkAndMerge(id)
    }

    private fun checkAndMerge(id: String) {
        val ent = XDMApp.getEntry(id) ?: return
        if (ent.state != XDMConstants.FINISHED) return

        val job = pendingMerges.values.find { it.videoDownloadId == id || it.audioDownloadId == id } ?: return
        val baseName = job.baseName
        if (baseName in mergingNow) return

        val videoEntry = XDMApp.getEntry(job.videoDownloadId)
        val audioEntry = XDMApp.getEntry(job.audioDownloadId)

        if (videoEntry?.state == XDMConstants.FINISHED && audioEntry?.state == XDMConstants.FINISHED) {
            Logger.log("YTMergeTracker: both downloads finished for $baseName, starting merge")
            pendingMerges.remove(baseName)
            mergingNow.add(baseName)
            ffmpegOutput[baseName] = ""
            onMergeStart?.invoke(baseName)

            Thread {
                runMerge(job)
                mergingNow.remove(baseName)
            }.start()
        }
    }

    private fun runMerge(job: MergeJob) {
        try {
            // Debug: check file sizes before merge
            val vFile = File(job.videoFile)
            val aFile = File(job.audioFile)
            Logger.log("YTMergeTracker: video file exists=${vFile.exists()} size=${vFile.length()} path=${job.videoFile}")
            Logger.log("YTMergeTracker: audio file exists=${aFile.exists()} size=${aFile.length()} path=${job.audioFile}")
            if (!vFile.exists() || !aFile.exists()) {
                Logger.log("YTMergeTracker: input files missing, cannot merge")
                onMergeEvent?.invoke(job.baseName, false, null)
                return
            }

            val ffmpeg = findFfmpeg()
            if (ffmpeg == null) {
                Logger.log("YTMergeTracker: ffmpeg not found")
                ffmpegOutput[job.baseName] = "ffmpeg not found"
                onMergeEvent?.invoke(job.baseName, false, null)
                return
            }

            Logger.log("YTMergeTracker: input files exist, ffmpeg=$ffmpeg")
            File(job.outputFile).parentFile.mkdirs()

            val cmd = listOf(
                ffmpeg,
                "-i", job.videoFile,
                "-i", job.audioFile,
                "-c", "copy",
                "-y",
                job.outputFile
            )

            Logger.log("YTMergeTracker: running: ${cmd.joinToString(" ")}")
            val proc = ProcessBuilder(cmd)
                .redirectErrorStream(true)
                .start()

            // Read output line-by-line on a separate thread so pipe never fills
            val outputReader = Thread {
                try {
                    proc.inputStream.bufferedReader().use { reader ->
                        reader.lines().forEach { line ->
                            Logger.log("YTMergeTracker output: $line")
                            synchronized(ffmpegOutput) {
                                ffmpegOutput[job.baseName] =
                                    (ffmpegOutput[job.baseName] ?: "") + line + "\n"
                            }
                            onMergeOutput?.invoke(job.baseName, line)
                        }
                    }
                } catch (_: Exception) {}
            }
            outputReader.isDaemon = true
            outputReader.start()

            // Wait with timeout (5 min for safety)
            val finished = proc.waitFor(5, TimeUnit.MINUTES)
            if (!finished) {
                proc.destroyForcibly()
                Logger.log("YTMergeTracker: merge timed out for ${job.baseName}")
                onMergeEvent?.invoke(job.baseName, false, null)
                return
            }

            val exitCode = proc.exitValue()
            // Make sure output reader has finished
            outputReader.join(2000)

            val output = getFfmpegOutput(job.baseName)

            if (exitCode == 0) {
                Logger.log("YTMergeTracker: merge successful for ${job.baseName}")
                try { File(job.videoFile).delete() } catch (_: Exception) {}
                try { File(job.audioFile).delete() } catch (_: Exception) {}
                ffmpegOutput.remove(job.baseName)

                val videoEntry = XDMApp.getEntry(job.videoDownloadId)
                if (videoEntry != null) {
                    videoEntry.setFile("${job.baseName}.${job.outputExt}")
                    videoEntry.setFolder(File(job.outputFile).parent)
                    videoEntry.setSize(File(job.outputFile).length())
                    XDMApp.fileNameChanged(job.videoDownloadId)
                }
                XDMApp.deleteDownloads(listOf(job.audioDownloadId), true)

                onMergeEvent?.invoke(job.baseName, true, job.outputFile)
            } else {
                Logger.log("YTMergeTracker: merge failed, exit code $exitCode\n$output")
                onMergeEvent?.invoke(job.baseName, false, null)
            }
        } catch (e: Exception) {
            Logger.log(e)
            onMergeEvent?.invoke(job.baseName, false, null)
        }
    }

    private fun findFfmpeg(): String? {
        val bundled = File(Config.getInstance().dataFolder, if (System.getProperty("os.name").lowercase().contains("win")) "ffmpeg.exe" else "ffmpeg")
        // Check bundled first; verify it actually works
        if (bundled.exists() && bundled.canExecute()) {
            try {
                val testProc = ProcessBuilder(bundled.absolutePath, "-version")
                    .redirectErrorStream(true).start()
                if (testProc.waitFor(3, TimeUnit.SECONDS) && testProc.exitValue() == 0) {
                    return bundled.absolutePath
                }
            } catch (_: Exception) {}
            Logger.log("YTMergeTracker: bundled ffmpeg at ${bundled.absolutePath} is invalid, falling back to PATH")
        }

        val candidates = listOf("ffmpeg", "ffmpeg.exe", "/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg")
        for (cmd in candidates) {
            try {
                val proc = ProcessBuilder(cmd, "-version").redirectErrorStream(true).start()
                if (proc.waitFor(3, TimeUnit.SECONDS) && proc.exitValue() == 0) return cmd
            } catch (_: Exception) {}
        }
        return null
    }
}
