package xdman.network

import java.net.URL
import xdman.util.StringUtils

class ParsedURL private constructor() {
    private var _url: String? = null
    var port: Int = 0
        private set
    var host: String? = null
        private set
    var pathAndQuery: String? = null
        private set
    var protocol: String? = null
        private set

    companion object {
        @JvmStatic
        fun parse(urlString: String?): ParsedURL? {
            try {
                val url = URL(urlString)
                val parsedURL = ParsedURL()
                parsedURL._url = urlString
                parsedURL.host = url.host
                parsedURL.port = url.port
                if (parsedURL.port < 0) {
                    parsedURL.port = url.defaultPort
                }
                parsedURL.protocol = url.protocol
                parsedURL.pathAndQuery = url.path
                if (StringUtils.isNullOrEmptyOrBlank(parsedURL.pathAndQuery)) {
                    parsedURL.pathAndQuery = "/"
                }
                val query = url.query
                if (!StringUtils.isNullOrEmptyOrBlank(query)) {
                    parsedURL.pathAndQuery += "?$query"
                }
                return parsedURL
            } catch (e: Exception) {
                return null
            }
        }
    }

    override fun toString(): String {
        return _url ?: ""
    }
}
