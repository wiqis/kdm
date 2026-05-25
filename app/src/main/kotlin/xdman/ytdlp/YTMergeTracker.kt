package xdman.ytdlp

import xdman.*
import xdman.util.Logger
import java.io.File

object YTMergeTracker : ListChangeListener {
    private val pendingMerges = mutableMapOf<String, MergeJob>()
    private val mergingNow = mutableSetOf<String>()

    data class MergeJob(
        val baseName: String,
        val videoFile: String,
        val audioFile: String,
        val outputFile: String,
        val videoDownloadId: String,
        val audioDownloadId: String,
        val videoFormatId: String,
        val audioFormatId: String,
        val tempFolder: String
    )

    var onMergeStart: ((baseName: String) -> Unit)? = null
    var onMergeEvent: ((baseName: String, success: Boolean, mergedFilePath: String?) -> Unit)? = null

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
        val outputFile = "$outputFolder/$baseName.mp4"

        pendingMerges[baseName] = MergeJob(
            baseName = baseName,
            videoFile = videoFile,
            audioFile = audioFile,
            outputFile = outputFile,
            videoDownloadId = videoDownloadId,
            audioDownloadId = audioDownloadId,
            videoFormatId = videoExt,
            audioFormatId = audioExt,
            tempFolder = tempFolder
        )
        Logger.log("YTMergeTracker: registered merge for $baseName (video=$videoDownloadId, audio=$audioDownloadId)")
    }

    fun getMergeJob(videoId: String, audioId: String): MergeJob? {
        return pendingMerges.values.find { it.videoDownloadId == videoId && it.audioDownloadId == audioId }
    }

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
            onMergeStart?.invoke(baseName)

            Thread {
                runMerge(job)
                mergingNow.remove(baseName)
            }.start()
        }
    }

    private fun runMerge(job: MergeJob) {
        try {
            val ffmpeg = findFfmpeg()
            if (ffmpeg == null) {
                Logger.log("YTMergeTracker: ffmpeg not found")
                onMergeEvent?.invoke(job.baseName, false, null)
                return
            }

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
            val proc = ProcessBuilder(cmd).redirectErrorStream(true).start()
            val output = proc.inputStream.bufferedReader().readText()
            val exitCode = proc.waitFor()

            if (exitCode == 0) {
                Logger.log("YTMergeTracker: merge successful for ${job.baseName}")
                try { File(job.videoFile).delete() } catch (_: Exception) {}
                try { File(job.audioFile).delete() } catch (_: Exception) {}

                val videoEntry = XDMApp.getEntry(job.videoDownloadId)
                if (videoEntry != null) {
                    videoEntry.setFile("${job.baseName}.mp4")
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
        if (bundled.exists() && bundled.canExecute()) return bundled.absolutePath

        val candidates = listOf("ffmpeg", "ffmpeg.exe", "/usr/bin/ffmpeg", "/usr/local/bin/ffmpeg")
        for (cmd in candidates) {
            try {
                val proc = ProcessBuilder(cmd, "-version").redirectErrorStream(true).start()
                if (proc.waitFor() == 0) return cmd
            } catch (_: Exception) {}
        }
        return null
    }
}
