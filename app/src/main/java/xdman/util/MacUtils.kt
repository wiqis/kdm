package xdman.util

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.util.ArrayList

class MacUtils {
    companion object {
        @JvmStatic
        @Throws(FileNotFoundException::class)
        fun open(f: File) {
            if (!f.exists()) {
                throw FileNotFoundException()
            }
            try {
                val pb = ProcessBuilder()
                pb.command("open", f.absolutePath)
                if (pb.start().waitFor() != 0) {
                    throw FileNotFoundException()
                }
            } catch (e: Exception) {
                Logger.log(e)
            }
        }

        @JvmStatic
        @Throws(FileNotFoundException::class)
        fun openFolder(folder: String?, file: String?) {
            if (file == null) {
                openFolder2(folder)
                return
            }
            val f = File(folder, file)
            if (!f.exists()) {
                throw FileNotFoundException()
            }
            try {
                val pb = ProcessBuilder()
                Logger.log("Opening folder: " + f.absolutePath)
                pb.command("open", "-R", f.absolutePath)
                if (pb.start().waitFor() != 0) {
                    throw FileNotFoundException()
                }
            } catch (e: Exception) {
                Logger.log(e)
            }
        }

        private fun openFolder2(folder: String?) {
            try {
                val builder = ProcessBuilder()
                val lst = ArrayList<String>()
                lst.add("open")
                folder?.let { lst.add(it) }
                builder.command(lst)
                builder.start()
            } catch (e: Exception) {
                Logger.log(e)
            }
        }

        @JvmStatic
        fun launchApp(app: String?, args: String?): Boolean {
            try {
                val pb = ProcessBuilder()
                pb.command("open", "-n", "-a", app, "--args", args)
                if (pb.start().waitFor() != 0) {
                    throw FileNotFoundException()
                }
                return true
            } catch (e: Exception) {
                Logger.log(e)
                return false
            }
        }

        @JvmStatic
        fun keepAwakePing() {
            try {
                Runtime.getRuntime().exec("caffeinate -i -t 3")
            } catch (e: Exception) {
                Logger.log(e)
            }
        }

        @JvmStatic
        fun addToStartup() {
            val dir = File(System.getProperty("user.home"), "Library/LaunchAgents")
            dir.mkdirs()
            val f = File(dir, "org.sdg.xdman.plist")
            var fs: FileOutputStream? = null
            try {
                fs = FileOutputStream(f)
                fs.write(getStartupPlist().toByteArray())
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
            val f = File(System.getProperty("user.home"), "Library/LaunchAgents/org.sdg.xdman.plist")
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
            val f = File(System.getProperty("user.home"), "Library/LaunchAgents/org.sdg.xdman.plist")
            f.delete()
        }

        @JvmStatic
        fun getStartupPlist(): String {
            val str = ("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\r\n"
                    + "<!DOCTYPE plist PUBLIC \"-//Apple Computer//DTD PLIST 1.0//EN\"\r\n"
                    + "\"http://www.apple.com/DTDs/PropertyList-1.0.dtd\" >\r\n" + "<plist version=\"1.0\">\r\n"
                    + "	<dict>\r\n" + "		<key>Label</key>\r\n" + "		<string>org.sdg.xdman</string>\r\n"
                    + "		<key>ProgramArguments</key>\r\n" + "		<array>\r\n"
                    + "			<string>%sbin/java</string>\r\n" + "			<string>-Xmx1024m</string>\r\n"
                    + "			<string>-Xdock:name=XDM</string>\r\n" + "			<string>-jar</string>\r\n"
                    + "			<!-- MODIFY THIS TO POINT TO YOUR EXECUTABLE JAR FILE -->\r\n"
                    + "			<string>%s</string>\r\n" + "			<string>-m</string>\r\n" + "		</array>\r\n"
                    + "		<key>OnDemand</key>\r\n" + "		<true />\r\n" + "		<key>RunAtLoad</key>\r\n"
                    + "		<true />\r\n" + "		<key>KeepAlive</key>\r\n" + "		<false />\r\n" + "	</dict>\r\n"
                    + "</plist>")
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
                pb.command("open", url)
                pb.start()
            } catch (e: Exception) {
                Logger.log(e)
            }
        }

        @JvmStatic
        fun initShutdown() {
            try {
                val builder = ProcessBuilder()
                val lst = ArrayList<String>()
                lst.add("osascript")
                lst.add("-e")
                lst.add("tell app \"System Events\" to shut down")
                builder.command(lst)
                builder.start()
            } catch (e: Exception) {
                Logger.log(e)
            }
        }
    }
}
