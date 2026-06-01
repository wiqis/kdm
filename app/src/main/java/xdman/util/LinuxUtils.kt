package xdman.util

import java.io.BufferedReader
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.InputStreamReader

class LinuxUtils {
    companion object {
        private val shutdownCmds = arrayOf(
            "dbus-send --system --print-reply --dest=org.freedesktop.login1 /org/freedesktop/login1 \"org.freedesktop.login1.Manager.PowerOff\" boolean:true",
            "dbus-send --system --print-reply --dest=\"org.freedesktop.ConsoleKit\" /org/freedesktop/ConsoleKit/Manager org.freedesktop.ConsoleKit.Manager.Stop",
            "systemctl poweroff"
        )

        @JvmStatic
        fun initShutdown() {
            for (i in shutdownCmds.indices) {
                val cmd = shutdownCmds[0]
                try {
                    val proc = Runtime.getRuntime().exec(cmd)
                    val ret = proc.waitFor()
                    if (ret == 0) break
                } catch (e: Exception) {
                    Logger.log(e)
                }
            }
        }

        @JvmStatic
        @Throws(FileNotFoundException::class)
        fun open(f: File) {
            if (!f.exists()) {
                throw FileNotFoundException()
            }
            try {
                val pb = ProcessBuilder()
                pb.command("xdg-open", f.absolutePath)
                pb.start()
            } catch (e: Exception) {
                Logger.log(e)
            }
        }

        @JvmStatic
        fun keepAwakePing() {
            try {
                Runtime.getRuntime().exec(
                    "dbus-send --print-reply --type=method_call --dest=org.freedesktop.ScreenSaver /ScreenSaver org.freedesktop.ScreenSaver.SimulateUserActivity"
                )
            } catch (e: Exception) {
                Logger.log(e)
            }
        }

        @JvmStatic
        fun addToStartup() {
            val dir = File(System.getProperty("user.home"), ".config/autostart")
            dir.mkdirs()
            val f = File(dir, "kdm.desktop")
            var fs: FileOutputStream? = null
            try {
                fs = FileOutputStream(f)
                fs.write(getDesktopFileString().toByteArray())
            } catch (e: Exception) {
                Logger.log(e)
            } finally {
                try {
                    fs?.close()
                } catch (e2: Exception) {
                }
            }
            f.setExecutable(true)
        }

        @JvmStatic
        fun isAlreadyAutoStart(): Boolean {
            val f = File(System.getProperty("user.home"), ".config/autostart/xdman.desktop")
            if (!f.exists()) return false
            var `in`: FileInputStream? = null
            val buf = ByteArray(f.length().toInt())
            try {
                `in` = FileInputStream(f)
                if (`in`.read(buf) != f.length().toInt()) {
                    return false
                }
            } catch (e: Exception) {
                Logger.log(e)
            } finally {
                try {
                    `in`?.close()
                } catch (e2: Exception) {
                }
            }
            val str = String(buf)
            val s1 = getProperPath(System.getProperty("java.home"))
            val s2 = XDMUtils.getJarFile()?.absolutePath ?: ""
            return str.contains(s1) && str.contains(s2)
        }

        @JvmStatic
        fun removeFromStartup() {
            val f = File(System.getProperty("user.home"), ".config/autostart/kdm.desktop")
            f.delete()
        }

        private fun getDesktopFileString(): String {
            val str = ("[Desktop Entry]\r\n" + "Encoding=UTF-8\r\n" + "Version=1.0\r\n" + "Type=Application\r\n"
                    + "Terminal=false\r\n" + "Exec=\"%sbin/java\" -Xmx1024m -jar \"%s\" -m\r\n" + "Name=Kinetic Download Manager\r\n"
                    + "Comment=Kinetic Download Manager\r\n" + "Categories=Network;\r\n" + "Icon=/opt/kdm/icon.png")
            val s1 = getProperPath(System.getProperty("java.home"))
            val s2 = XDMUtils.getJarFile()?.absolutePath ?: ""
            return String.format(str, s1, s2)
        }

        private fun getProperPath(path: String): String {
            return if (path.endsWith("/")) path else "$path/"
        }

        @JvmStatic
        fun browseURL(url: String?) {
            try {
                val pb = ProcessBuilder()
                pb.command("xdg-open", url)
                pb.start()
            } catch (e: Exception) {
                Logger.log(e)
            }
        }

        @JvmStatic
        fun getXDGDownloaDir(): String? {
            var br: BufferedReader? = null
            try {
                br = BufferedReader(
                    InputStreamReader(
                        FileInputStream(
                            File(
                                System.getProperty("user.home"),
                                ".config/user-dirs.dirs"
                            )
                        )
                    )
                )
                while (true) {
                    val line = br.readLine() ?: break
                    if (line.startsWith("XDG_DOWNLOAD_DIR")) {
                        val index = line.indexOf("=")
                        if (index != -1) {
                            var path = line.substring(index + 1).trim { it <= ' ' }
                            path = path.replace("\$HOME", System.getProperty("user.home"))
                            // Also handle quotes around the XDG_DOWNLOAD_DIR value if they exist
                            if (path.startsWith("\"") && path.endsWith("\"") && path.length > 1) {
                                path = path.substring(1, path.length - 1)
                            }
                            val f = File(path)
                            if (f.exists()) {
                                return f.absolutePath
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Logger.log(e)
            } finally {
                if (br != null) {
                    try {
                        br.close()
                    } catch (e2: Exception) {
                    }
                }
            }
            return null
        }
    }
}
