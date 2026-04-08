package cc.dlabs.pesamind.core.network

object TokenManager {
    @Volatile
    private var token: String = ""

    fun getToken(): String = token

    fun setToken(value: String) {
        token = value
    }

    fun clearToken() {
        token = ""
    }
}
