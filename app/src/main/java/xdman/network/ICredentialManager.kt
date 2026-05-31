package xdman.network

interface ICredentialManager {
    fun requestProxyCredential(): Boolean
    fun getProxyUser(): String?
    fun getProxyPass(): String?
    fun requestCredential(): Boolean
    fun getUser(): String?
    fun getPass(): String?
}
