package xdman.util

import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.DataFlavor
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.MouseEvent
import java.io.BufferedReader
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.URISyntaxException
import java.net.URL
import java.net.URLDecoder
import java.util.ArrayList
import java.util.Locale

import xdman.Config
import xdman.XDMConstants
import xdman.downloaders.metadata.HttpMetadata

object XDMUtils {
    private val invalid_chars = charArrayOf('/', '\\', '"', '?', '*', '<', '>', ':', '|')

    @JvmStatic
    fun decodeFileName(encoded: String): String {
        var str: String
        try {
            str = URLDecoder.decode(encoded.replace("+", "%2B"), "UTF-8")
        } catch (e: Exception) {
            val builder = StringBuilder()
            val ch = encoded.toCharArray()
            var i = 0
            while (i < ch.size) {
                if (ch[i] == '%') {
                    if (i + 2 < ch.size) {
                        try {
                            val c = (ch[i + 1].toString() + ch[i + 2]).toInt(16)
                            builder.append(c.toChar())
                            i += 2
                            i++
                            continue
                        } catch (ex: Exception) {
                        }
                    }
                }
                builder.append(ch[i])
                i++
            }
            str = builder.toString()
        }
        val builder = StringBuilder()
        for (c in str.toCharArray()) {
            if (c == '/' || c == '\\' || c == '"' || c == '?' || c == '*' || c == '<' || c == '>' || c == ':') continue
            builder.append(c)
        }
        return builder.toString()
    }

    @JvmStatic
    fun getFileName(uri: String?): String {
        try {
            if (uri == null) return "FILE"
            if (uri == "/" || uri.isEmpty()) {
                return "FILE"
            }
            val x = uri.lastIndexOf("/")
            var path = uri
            if (x > -1) {
                path = uri.substring(x)
            }
            val qindex = path.indexOf("?")
            if (qindex > -1) {
                path = path.substring(0, qindex)
            }
            path = decodeFileName(path)
            if (path.isEmpty()) return "FILE"
            if (path == "/") return "FILE"
            return createSafeFileName(path)
        } catch (e: Exception) {
            Logger.log(e)
            return "FILE"
        }
    }

    @JvmStatic
    fun createSafeFileName(str: String): String {
        var safe_name = str
        for (i in invalid_chars.indices) {
            if (safe_name.indexOf(invalid_chars[i]) != -1) {
                safe_name = safe_name.replace(invalid_chars[i], '_')
            }
        }
        return safe_name
    }

    @JvmStatic
    fun validateURL(urlStr: String?): Boolean {
        var url = urlStr ?: return false
        try {
            url = url.lowercase(Locale.getDefault())
            if (url.startsWith("http://") || url.startsWith("https://") || url.startsWith("ftp://")) {
                URL(url)
                return true
            }
            return false
        } catch (e: Exception) {
            return false
        }
    }

    private val doc = arrayOf(".doc", ".docx", ".txt", ".pdf", ".rtf", ".xml", ".c", ".cpp", ".java", ".cs", ".vb", ".html", ".htm", ".chm", ".xls", ".xlsx", ".ppt", ".pptx", ".js", ".css")
    private val cmp = arrayOf(".7z", ".zip", ".rar", ".gz", ".tgz", ".tbz2", ".bz2", ".lzh", ".sit", ".z")
    private val music = arrayOf(".mp3", ".wma", ".ogg", ".aiff", ".au", ".mid", ".midi", ".mp2", ".mpa", ".wav", ".aac", ".oga", ".ogx", ".ogm", ".spx", ".opus")
    private val vid = arrayOf(".mpg", ".mpeg", ".avi", ".flv", ".asf", ".mov", ".mpe", ".wmv", ".mkv", ".mp4", ".3gp", ".divx", ".vob", ".webm", ".ts")
    private val prog = arrayOf(".exe", ".msi", ".bin", ".sh", ".deb", ".cab", ".cpio", ".dll", ".jar", "rpm", ".run", ".py")

    @JvmStatic
    fun findCategory(filename: String?): Int {
        val file = filename?.lowercase(Locale.getDefault()) ?: return XDMConstants.OTHER
        for (i in doc.indices) {
            if (file.endsWith(doc[i])) {
                return XDMConstants.DOCUMENTS
            }
        }
        for (i in cmp.indices) {
            if (file.endsWith(cmp[i])) {
                return XDMConstants.COMPRESSED
            }
        }
        for (i in music.indices) {
            if (file.endsWith(music[i])) {
                return XDMConstants.MUSIC
            }
        }
        for (i in prog.indices) {
            if (file.endsWith(prog[i])) {
                return XDMConstants.PROGRAMS
            }
        }
        for (i in vid.indices) {
            if (file.endsWith(vid[i])) {
                return XDMConstants.VIDEO
            }
        }
        return XDMConstants.OTHER
    }

    @JvmStatic
    fun appendArray2Str(arr: Array<String>): String {
        var first = true
        val buf = StringBuilder()
        for (s in arr) {
            if (!first) {
                buf.append(",")
            }
            buf.append(s)
            first = false
        }
        return buf.toString()
    }

    @JvmStatic
    fun appendStr2Array(str: String): Array<String> {
        val arr = str.split(",").toTypedArray()
        val arrList = ArrayList<String>()
        for (s in arr) {
            val txt = s.trim()
            if (txt.isNotEmpty()) {
                arrList.add(txt)
            }
        }
        return arrList.toTypedArray()
    }

    @JvmStatic
    fun getExtension(file: String?): String? {
        if (file == null) return null
        val index = file.lastIndexOf(".")
        return if (index > 0) {
            file.substring(index)
        } else {
            null
        }
    }

    @JvmStatic
    fun getFileNameWithoutExtension(fileName: String?): String {
        if (fileName == null) return ""
        val index = fileName.lastIndexOf(".")
        return if (index > 0) {
            fileName.substring(0, index)
        } else {
            fileName
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun copyStream(instream: InputStream, outstream: OutputStream, size: Long) {
        val b = ByteArray(8192)
        var rem = size
        while (true) {
            val bs = if (size > 0) (if (rem > b.size) b.size.toLong() else rem).toInt() else b.size
            val x = instream.read(b, 0, bs)
            if (x == -1) {
                if (size > 0) {
                    throw EOFException("Unexpected EOF")
                } else {
                    break
                }
            }
            outstream.write(b, 0, x)
            rem -= x.toLong()
            if (size > 0) {
                if (rem <= 0) break
            }
        }
    }

    const val WINDOWS = 10
    const val MAC = 20
    const val LINUX = 30

    @JvmStatic
    fun detectOS(): Int {
        val os = System.getProperty("os.name").lowercase(Locale.ENGLISH)
        return when {
            os.contains("mac") || os.contains("darwin") || os.contains("os x") -> MAC
            os.contains("linux") -> LINUX
            os.contains("windows") -> WINDOWS
            else -> -1
        }
    }

    @JvmStatic
    fun getOsArch(): Int {
        return if (System.getProperty("os.arch").contains("64")) 64 else 32
    }

    @JvmStatic
    @Throws(Exception::class)
    fun openFile(file: String?, folder: String) {
        val os = detectOS()
        val f = File(folder, file)
        when (os) {
            WINDOWS -> WinUtils.open(f)
            LINUX -> LinuxUtils.open(f)
            MAC -> MacUtils.open(f)
            else -> Desktop.getDesktop().open(f)
        }
    }

    @JvmStatic
    @Throws(Exception::class)
    fun openFolder(file: String?, folder: String) {
        val os = detectOS()
        when (os) {
            WINDOWS -> WinUtils.openFolder(folder, file)
            LINUX -> {
                val f = File(folder)
                LinuxUtils.open(f)
            }
            MAC -> MacUtils.openFolder(folder, file)
            else -> {
                val ff = File(folder)
                Desktop.getDesktop().open(ff)
            }
        }
    }

    @JvmStatic
    fun copyURL(url: String) {
        try {
            Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(url), null)
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    @JvmStatic
    fun exec(args: String?): Boolean {
        if (args == null) return false
        try {
            Logger.log("Launching: $args")
            Runtime.getRuntime().exec(args)
        } catch (e: IOException) {
            Logger.log(e)
            return false
        }
        return true
    }

    @JvmStatic
    fun getFreeSpace(folder: String?): Long {
        return if (folder == null) File(Config.getInstance().temporaryFolder).freeSpace else File(folder).freeSpace
    }

    @JvmStatic
    fun keepAwakePing() {
        try {
            val os = detectOS()
            when (os) {
                LINUX -> LinuxUtils.keepAwakePing()
                WINDOWS -> WinUtils.keepAwakePing()
                MAC -> MacUtils.keepAwakePing()
            }
        } catch (e: Throwable) {
        }
    }

    @JvmStatic
    fun isAlreadyAutoStart(): Boolean {
        try {
            val os = detectOS()
            return when (os) {
                LINUX -> LinuxUtils.isAlreadyAutoStart()
                WINDOWS -> WinUtils.isAlreadyAutoStart()
                MAC -> MacUtils.isAlreadyAutoStart()
                else -> false
            }
        } catch (e: Throwable) {
            Logger.log(e)
        }
        return false
    }

    @JvmStatic
    fun addToStartup() {
        try {
            val os = detectOS()
            when (os) {
                LINUX -> LinuxUtils.addToStartup()
                WINDOWS -> WinUtils.addToStartup()
                MAC -> MacUtils.addToStartup()
            }
        } catch (e: Throwable) {
            Logger.log(e)
        }
    }

    @JvmStatic
    fun removeFromStartup() {
        try {
            val os = detectOS()
            when (os) {
                LINUX -> LinuxUtils.removeFromStartup()
                WINDOWS -> WinUtils.removeFromStartup()
                MAC -> MacUtils.removeFromStartup()
            }
        } catch (e: Throwable) {
            Logger.log(e)
        }
    }

    @JvmStatic
    fun getJarFile(): File? {
        try {
            return File(XDMUtils::class.java.protectionDomain.codeSource.location.toURI().path)
        } catch (e: URISyntaxException) {
            e.printStackTrace()
        }
        return null
    }

    @JvmStatic
    fun checkComponentsInstalled(): Boolean {
        var ffFile = File(Config.getInstance().dataFolder, if (detectOS() == WINDOWS) "ffmpeg.exe" else "ffmpeg")
        var ytFile = File(Config.getInstance().dataFolder, if (detectOS() == WINDOWS) "youtube-dl.exe" else "youtube-dl")
        if (ffFile.exists() && ytFile.exists()) {
            return true
        } else {
            val jarFile = getJarFile()
            if (jarFile != null) {
                ffFile = File(jarFile.parentFile, if (detectOS() == WINDOWS) "ffmpeg.exe" else "ffmpeg")
                ytFile = File(jarFile.parentFile, if (detectOS() == WINDOWS) "youtube-dl.exe" else "youtube-dl")
                return ffFile.exists() && ytFile.exists()
            }
        }
        return false
    }

    @JvmStatic
    fun getClipBoardText(): String {
        try {
            return Toolkit.getDefaultToolkit().systemClipboard.getData(DataFlavor.stringFlavor) as String
        } catch (e: Exception) {
            Logger.log(e)
        }
        return ""
    }

    @JvmStatic
    fun browseURL(url: String) {
        val os = detectOS()
        when (os) {
            WINDOWS -> WinUtils.browseURL(url)
            LINUX -> LinuxUtils.browseURL(url)
            MAC -> MacUtils.browseURL(url)
        }
    }

    @JvmStatic
    fun below7(): Boolean {
        try {
            val version = System.getProperty("os.version").split(".")[0].toInt()
            return version < 6
        } catch (e: Exception) {
        }
        return false
    }

    @JvmStatic
    fun getDownloadsFolder(): String {
        if (detectOS() == LINUX) {
            val path = LinuxUtils.getXDGDownloaDir()
            if (path != null) {
                return path
            }
        }
        return File(System.getProperty("user.home"), "Downloads").absolutePath
    }

    @JvmStatic
    fun isFFmpegInstalled(): Boolean {
        val f1 = File(Config.getInstance().dataFolder, "ffmpeg" + (if (detectOS() == WINDOWS) ".exe" else ""))
        if (f1.exists()) {
            return true
        }
        val jarFile = getJarFile()
        return if (jarFile != null) {
            File(jarFile.parentFile, "ffmpeg" + (if (detectOS() == WINDOWS) ".exe" else "")).exists()
        } else false
    }

    @JvmStatic
    fun isMacPopupTrigger(e: MouseEvent): Boolean {
        if (detectOS() == MAC) {
            return (e.modifiersEx and InputEvent.BUTTON1_DOWN_MASK) != 0 && (e.modifiersEx and InputEvent.CTRL_DOWN_MASK) != 0
        }
        return false
    }

    @JvmStatic
    fun mkdirs(folder: String) {
        val outFolder = File(folder)
        if (!outFolder.exists()) {
            outFolder.mkdirs()
        }
    }

    @JvmStatic
    fun forceScreenType(type: Int) {
        screenType = type
    }

    @JvmStatic
    fun getScaleFactor(): Float {
        return when (screenType) {
            XDMConstants.XHDPI -> 2.0f
            XDMConstants.HDPI -> 1.5f
            else -> 1.0f
        }
    }

    @JvmStatic
    fun toMetadata(urls: List<String>): List<HttpMetadata> {
        val list = ArrayList<HttpMetadata>()
        for (url in urls) {
            val md = HttpMetadata()
            md.url = url
            list.add(md)
        }
        return list
    }

    private var screenType = -1

    @JvmStatic
    fun getScaledInt(value: Int): Int {
        return value
    }

    @JvmStatic
    @Throws(IOException::class)
    fun readLineSafe(r: BufferedReader): String {
        return r.readLine() ?: throw IOException("Unexpected EOF")
    }
}
