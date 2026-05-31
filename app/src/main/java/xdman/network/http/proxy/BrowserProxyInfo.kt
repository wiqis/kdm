package xdman.network.http.proxy

import java.util.ArrayList

class BrowserProxyInfo {
    var type: Int = 0
    var httpHost: String? = null
    var httpPort: Int = -1
    var httpsHost: String? = null
    var httpsPort: Int = -1
    var ftpHost: String? = null
    var ftpPort: Int = -1
    var gopherHost: String? = null
    var gopherPort: Int = -1
    var socksHost: String? = null
    var socksPort: Int = -1
    var overrides: Array<String>? = null
    var autoConfigURL: String? = null
    var isHintOnly: Boolean = false
    var isAutoProxyDetectionEnabled: Boolean = false

    fun getOverridesString(): String {
        var str = ""
        val ovs = overrides
        if (ovs != null && ovs.isNotEmpty()) {
            for (i in ovs.indices) {
                if (i != ovs.size - 1) {
                    str = str.plus(ovs[i] + "|")
                } else {
                    str = str.plus(ovs[i])
                }
            }
        }
        return str
    }

    fun setOverrides(paramList: List<*>?) {
        if (paramList != null) {
            val localArrayList = ArrayList(paramList)
            val arr = arrayOfNulls<String>(localArrayList.size)
            this.overrides = localArrayList.toArray(arr) as Array<String>
        }
    }
}
