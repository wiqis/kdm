package xdman.win32

import xdman.util.Logger
import xdman.util.XDMUtils
import java.io.File

class NativeMethods private constructor() {
    companion object {
        private var _me: NativeMethods? = null

        @JvmStatic
        fun getInstance(): NativeMethods {
            if (_me == null) {
                _me = NativeMethods()
            }
            return _me!!
        }
    }

    init {
        val dllPath = File(XDMUtils.getJarFile()!!.parentFile, "xdm_native.dll").absolutePath
        try {
            System.load(dllPath)
        } catch (e: Exception) {
            Logger.log(e)
        }
    }

    external fun keepAwakePing()
    external fun addToStartup(key: String, value: String)
    external fun presentInStartup(key: String, value: String): Boolean
    external fun removeFromStartup(key: String)
    external fun getDownloadsFolder(): String
    external fun stringTest(str: String): String
}
