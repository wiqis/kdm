package xdman.ui.res

import xdman.Config
import xdman.util.Logger
import java.io.FileInputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.*

object StringResource {
    private var strings: Properties? = null

    @JvmStatic
    @Synchronized
    fun get(id: String): String? {
        if (strings == null) {
            strings = Properties()
            try {
                val lang = Config.getInstance().language
                println(lang)
                if (!loadLang(lang, strings!!)) {
                    Logger.log("Unable to load language: $lang")
                    strings!!.clear()
                    loadLang("en", strings!!)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return strings!!.getProperty(id)
    }

    @JvmStatic
    private fun loadLang(code: String, prop: Properties): Boolean {
        Logger.log("Loading language $code")
        try {
            var inStream = StringResource::class.java.getResourceAsStream("/lang/en.txt")
            if (inStream == null) {
                inStream = FileInputStream("lang/$code.txt")
            }
            val r = InputStreamReader(inStream, Charset.forName("utf-8"))
            prop.load(r)
        } catch (e: Exception) {
            Logger.log(e)
            return false
        }
        if ("en" == code) {
            return true
        }
        try {
            var inStream = StringResource::class.java.getResourceAsStream("/lang/$code.txt")
            if (inStream == null) {
                inStream = FileInputStream("lang/$code.txt")
            }
            val r = InputStreamReader(inStream, Charset.forName("utf-8"))
            prop.load(r)
            return true
        } catch (e: Exception) {
            Logger.log(e)
            return false
        }
    }
}
