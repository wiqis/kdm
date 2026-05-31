package xdman.util

import xdman.win32.NativeMethods
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException

object WinUtils {
    @Throws(FileNotFoundException::class)
    fun open(f: File) {
        if (!f.exists()) {
            throw FileNotFoundException()
        }
        try {
            val builder = ProcessBuilder()
            val lst = ArrayList<String>()
            lst.add("rundll32")
            lst.add("url.dll,FileProtocolHandler")
            lst.add(f.absolutePath)
            builder.command(lst)
            builder.start()
        } catch (e: IOException) {
            Logger.log(e)
        }
    }

    @Throws(FileNotFoundException::class)
    fun openFolder(folder: String, file: String?) {
        if (file == null) {
            openFolder2(folder)
            return
        }
        try {
            val f = File(folder, file)
            if (!f.exists()) {
                throw FileNotFoundException()
            }
            val builder = ProcessBuilder()
            val lst = ArrayList<String>()
            lst.add("explorer")
            lst.add("/select,")
            lst.add(f.absolutePath)
            builder.command(lst)
            builder.start()
        } catch (e: IOException) {
            Logger.log(e)
        }
    }

    private fun openFolder2(folder: String) {
        try {
            val builder = ProcessBuilder()
            val lst = ArrayList<String>()
            lst.add("explorer")
            lst.add(folder)
            builder.command(lst)
            builder.start()
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    fun keepAwakePing() {
        NativeMethods.getInstance().keepAwakePing()
    }

    fun addToStartup() {
        val launchCmd = "\"" + System.getProperty("java.home") + "\\bin\\javaw.exe\" -Xmx1024m -jar \"" +
                XDMUtils.getJarFile()!!.absolutePath + "\" -m"
        Logger.log("Launch CMD: $launchCmd")
        NativeMethods.getInstance().addToStartup("XDM", launchCmd)
    }

    fun isAlreadyAutoStart(): Boolean {
        val launchCmd = "\"" + System.getProperty("java.home") + "\\bin\\javaw.exe\" -Xmx1024m -jar \"" +
                XDMUtils.getJarFile()!!.absolutePath + "\" -m"
        Logger.log("Launch CMD: $launchCmd")
        return NativeMethods.getInstance().presentInStartup("XDM", launchCmd)
    }

    fun removeFromStartup() {
        NativeMethods.getInstance().removeFromStartup("XDM")
    }

    fun browseURL(url: String) {
        try {
            val builder = ProcessBuilder()
            val lst = ArrayList<String>()
            lst.add("rundll32")
            lst.add("url.dll,FileProtocolHandler")
            lst.add(url)
            builder.command(lst)
            builder.start()
        } catch (e: IOException) {
            Logger.log(e)
        }
    }

    fun initShutdown() {
        try {
            val builder = ProcessBuilder()
            val lst = ArrayList<String>()
            lst.add("shutdown")
            lst.add("-t")
            lst.add("30")
            lst.add("-s")
            builder.command(lst)
            builder.start()
        } catch (e: Exception) {
            Logger.log(e)
        }
    }
}
