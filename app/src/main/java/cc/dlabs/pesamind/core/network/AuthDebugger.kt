package cc.dlabs.pesamind.core.network

import android.util.Log
import cc.dlabs.pesamind.core.storage.TokenManager
import kotlinx.coroutines.runBlocking

/**
 * Debug utility to diagnose authentication and token issues
 */
object AuthDebugger {
    private const val TAG = "AuthDebugger"

    fun logCurrentAuthState() {
        try {
            val token = runBlocking { TokenManager.getToken() }
            val refresh = runBlocking { TokenManager.getRefreshToken() }
            
            Log.d(TAG, "=== CURRENT AUTH STATE ===")
            Log.d(TAG, "Access Token: ${if (token.isNullOrEmpty()) "NULL/EMPTY" else "Present (${token?.length} chars)"}")
            Log.d(TAG, "Refresh Token: ${if (refresh.isNullOrEmpty()) "NULL/EMPTY" else "Present (${refresh?.length} chars)"}")
            Log.d(TAG, "Is Logged In: ${!token.isNullOrEmpty()}")
            Log.d(TAG, "========================")
        } catch (e: Exception) {
            Log.e(TAG, "Error checking auth state: ${e.message}", e)
        }
    }

    fun logTokenRetrieval(label: String = "") {
        try {
            val token = runBlocking { TokenManager.getToken() }
            Log.d(TAG, "$label - Token Retrieval: ${token?.take(20)}..." + 
                    (if (token.isNullOrEmpty()) " [EMPTY]" else " [OK]"))
        } catch (e: Exception) {
            Log.e(TAG, "$label - Token Retrieval Failed: ${e.message}", e)
        }
    }
}

