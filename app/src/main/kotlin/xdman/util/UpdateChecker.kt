package xdman.util

import xdman.Config
import xdman.XDMApp
import xdman.network.http.JavaHttpClient
import java.io.File
import java.io.FilenameFilter
import java.io.InputStream
import java.util.regex.Pattern

object UpdateChecker {
    const val APP_UPDATE_AVAILABLE = 10
    const val COMP_UPDATE_AVAILABLE = 20
    const val COMP_NOT_INSTALLED = 30
    const val NO_UPDATE_AVAILABLE = 40

    private val PATTERN_TAG = Pattern.compile("\\\"tag_name\\\"\\s*:\\s*\\\"(\\d+\\.\\d+\\.\\d+)\\\"")

    @JvmStatic
    fun getUpdateStat(): Int {
        println("checking for app update")
        return if (isAppUpdateAvailable()) APP_UPDATE_AVAILABLE else NO_UPDATE_AVAILABLE
    }

    private fun isAppUpdateAvailable(): Boolean {
        return isUpdateAvailable(XDMApp.APP_VERSION)
    }

    private fun isComponentUpdateAvailable(): Int {
        val componentVersion = getComponentVersion()
        println("current component version: $componentVersion")
        if (componentVersion == null) return -1
        return if (isUpdateAvailable(componentVersion)) 0 else 1
    }

    @JvmStatic
    fun getComponentVersion(): String? {
        val f = File(Config.getInstance().dataFolder)
        val files = f.list { _: File, name: String -> name.endsWith(".version") }
        if (files == null || files.size < 1) {
            Logger.log("Component not installed")
            Logger.log("Checking fallback components")
            return getFallbackComponentVersion()
        }
        return files[0].split("\\.").toTypedArray()[0]
    }

    @JvmStatic
    fun getFallbackComponentVersion(): String? {
        val f = XDMUtils.getJarFile()!!.parentFile
        val files = f.list { _: File, name: String -> name.endsWith(".version") }
        if (files == null || files.size < 1) {
            Logger.log("Component not installed")
            return null
        }
        return files[0].split("\\.").toTypedArray()[0]
    }

    private fun isUpdateAvailable(version: String): Boolean {
        var client: JavaHttpClient? = null
        try {
            client = JavaHttpClient(XDMApp.APP_UPDAT_URL + "?ver=$version")
            client.setFollowRedirect(true)
            client.connect()
            val resp = client.statusCode
            Logger.log("manifest download response: $resp")
            if (resp == 200) {
                val `in`: InputStream = client.inputStream
                val sb = StringBuilder()
                while (true) {
                    val x = `in`.read()
                    if (x == -1) break
                    sb.append(x.toChar())
                }
                return isNewerVersion(sb, XDMApp.APP_VERSION)
            }
        } catch (e: Exception) {
            Logger.log(e)
        } finally {
            try {
                client!!.dispose()
            } catch (_: Exception) {
            }
        }
        return false
    }

    private fun isNewerVersion(text: StringBuilder, v2: String): Boolean {
        try {
            val matcher = PATTERN_TAG.matcher(text)
            if (matcher.find()) {
                val v1 = matcher.group(1)
                println("$v1 $v2")
                if (v1.indexOf(".") > 0 && v2.indexOf(".") > 0) {
                    val arr1 = v1.split("\\.").toTypedArray()
                    val arr2 = v2.split("\\.").toTypedArray()
                    for (i in 0 until Math.min(arr1.size, arr2.size)) {
                        val ia = Integer.parseInt(arr1[i])
                        val ib = Integer.parseInt(arr2[i])
                        if (ia > ib) {
                            return true
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return false
    }
}
