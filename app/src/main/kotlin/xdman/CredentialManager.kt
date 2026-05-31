package xdman

import xdman.util.Base64
import xdman.util.Logger
import xdman.util.StringUtils
import java.io.*
import java.net.PasswordAuthentication

class CredentialManager private constructor() {
    private val savedCredentials: MutableMap<String, PasswordAuthentication> = HashMap()
    private val cachedCredentials: MutableMap<String, PasswordAuthentication> = HashMap()

    companion object {
        private var _this: CredentialManager? = null

        @JvmStatic
        fun getInstance(): CredentialManager {
            if (_this == null) {
                _this = CredentialManager()
            }
            return _this!!
        }
    }

    init {
        load()
    }

    fun getCredentials(): Set<Map.Entry<String, PasswordAuthentication>> {
        return savedCredentials.entries
    }

    fun getCredentialForHost(host: String): PasswordAuthentication? {
        println("Getting cred for $host")
        var pauth: PasswordAuthentication? = savedCredentials[host]
        if (pauth == null) {
            return cachedCredentials[host]
        }
        return pauth
    }

    fun getCredentialForProxy(): PasswordAuthentication? {
        return if (!StringUtils.isNullOrEmptyOrBlank(Config.getInstance().proxyUser)) {
            PasswordAuthentication(
                Config.getInstance().proxyUser,
                Config.getInstance().proxyPass?.toCharArray() ?: CharArray(0)
            )
        } else {
            null
        }
    }

    fun addCredentialForHost(host: String, pauth: PasswordAuthentication, save: Boolean) {
        if (save) {
            savedCredentials[host] = pauth
        } else {
            cachedCredentials[host] = pauth
        }
    }

    fun addCredentialForHost(host: String, user: String, pass: String, save: Boolean) {
        addCredentialForHost(host, PasswordAuthentication(user, pass.toCharArray()), save)
    }

    fun addCredentialForHost(host: String, user: String, pass: String) {
        addCredentialForHost(host, PasswordAuthentication(user, pass.toCharArray()), false)
    }

    fun addCredentialForHost(host: String, pauth: PasswordAuthentication) {
        addCredentialForHost(host, pauth, false)
    }

    private fun load() {
        var br: BufferedReader? = null
        try {
            val f = File(Config.getInstance().dataFolder, ".credentials")
            if (!f.exists()) {
                Logger.log("No saved credentials")
                return
            }
            br = BufferedReader(InputStreamReader(FileInputStream(f)))
            if (savedCredentials.isNotEmpty())
                savedCredentials.clear()
            while (true) {
                val ln = br.readLine() ?: break
                val str = String(Base64.decode(ln))
                val arr = str.split("\n".toRegex()).toTypedArray()
                if (arr.size < 2)
                    continue
                savedCredentials[arr[0]] = PasswordAuthentication(
                    arr[1],
                    if (arr.size == 3) arr[2].toCharArray() else CharArray(0)
                )
            }
        } catch (e: Exception) {
            Logger.log(e)
        } finally {
            try {
                br?.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    fun save() {
        val buf = StringBuilder()
        val savedKeyIterator = savedCredentials.keys.iterator()
        while (savedKeyIterator.hasNext()) {
            val key = savedKeyIterator.next()
            val pauth = savedCredentials[key]
            val str = "$key\n${pauth!!.userName}\n${String(pauth.password)}"
            val str64 = Base64.encode(str.toByteArray())
            buf.append("$str64\n")
        }
        var out: OutputStream? = null
        try {
            val f = File(Config.getInstance().dataFolder, ".credentials")
            out = FileOutputStream(f)
            out.write(buf.toString().toByteArray())
        } catch (e: Exception) {
            Logger.log(e)
        } finally {
            try {
                out?.close()
            } catch (_: Exception) {
            }
        }
    }

    fun removeSavedCredential(host: String) {
        savedCredentials.remove(host)
        save()
    }
}
