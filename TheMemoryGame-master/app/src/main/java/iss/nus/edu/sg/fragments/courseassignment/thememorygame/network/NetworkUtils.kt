package iss.nus.edu.sg.fragments.courseassignment.thememorygame.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log

/**
 * Network Utilities
 * Provides network status checking, connection testing, and other functions
 */
class NetworkUtils {
    
    companion object {
        private const val TAG = "NetworkUtils"
        
        /**
         * Check if network is available
         * @param context Context
         * @return true if network is available
         */
        fun isNetworkAvailable(context: Context): Boolean {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as ConnectivityManager
            
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) 
                ?: return false
            
            return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                   capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
        }
        
        /**
         * Get network type
         */
        fun getNetworkType(context: Context): NetworkType {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) 
                as ConnectivityManager
            
            val network = connectivityManager.activeNetwork ?: return NetworkType.NONE
            val capabilities = connectivityManager.getNetworkCapabilities(network) 
                ?: return NetworkType.NONE
            
            return when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 
                    NetworkType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 
                    NetworkType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 
                    NetworkType.ETHERNET
                else -> NetworkType.OTHER
            }
        }
        
        /**
         * Test server connection
         * @param context Context
         * @return ConnectionTestResult
         */
        @Suppress("unused")
        suspend fun testServerConnection(context: Context): ConnectionTestResult {
            // First check if network is available
            if (!isNetworkAvailable(context)) {
                return ConnectionTestResult.NoNetwork
            }
            
            // Try to ping server
            val apiService = ApiService()
            
            return when (val response = apiService.get("/health")) {
                is ApiResponse.Success -> {
                    Log.d(TAG, "Server connection test: SUCCESS")
                    ConnectionTestResult.Success
                }
                
                is ApiResponse.Error -> {
                    Log.e(TAG, "Server connection test failed: ${response.message}")
                    ConnectionTestResult.ServerError(response.code, response.message)
                }
                
                is ApiResponse.Exception -> {
                    Log.e(TAG, "Server connection test exception", response.exception)
                    ConnectionTestResult.NetworkError(response.exception.message ?: "Unknown error")
                }
            }
        }
        
        /**
         * Format network error message to user-friendly text
         */
        @Suppress("unused")
        fun getErrorMessage(error: Throwable): String {
            return when {
                error.message?.contains("timeout", ignoreCase = true) == true ->
                    "Connection timeout, please check network and try again"
                    
                error.message?.contains("failed to connect", ignoreCase = true) == true ->
                    "Unable to connect to server, please check server address"
                    
                error.message?.contains("unable to resolve host", ignoreCase = true) == true ->
                    "Unable to resolve server address, please check network settings"
                    
                error is java.net.SocketTimeoutException ->
                    "Request timeout, please try again"
                    
                error is java.net.UnknownHostException ->
                    "Unable to find server"
                    
                error is java.io.IOException ->
                    "Network connection failed"
                    
                else ->
                    "Network error: ${error.message}"
            }
        }
    }
}

/**
 * Network type enumeration
 */
enum class NetworkType {
    WIFI,
    CELLULAR,
    ETHERNET,
    OTHER,
    NONE
}

/**
 * Connection test result
 */
sealed class ConnectionTestResult {
    object Success : ConnectionTestResult()
    object NoNetwork : ConnectionTestResult()
    data class ServerError(val code: Int, val message: String) : ConnectionTestResult()
    data class NetworkError(val message: String) : ConnectionTestResult()
}

/**
 * Network debugging helper
 * For development debugging
 */
@Suppress("unused")
object NetworkDebugHelper {
    
    private const val TAG = "NetworkDebug"
    private var isDebugMode = true  // Should be false in production
    
    /**
     * Log request information
     */
    @Suppress("unused")
    fun logRequest(endpoint: String, method: String, body: String? = null) {
        if (!isDebugMode) return
        
        Log.d(TAG, """
            ===== Network Request =====
            Method: $method
            Endpoint: $endpoint
            Body: ${body ?: "None"}
            ===========================
        """.trimIndent())
    }
    
    /**
     * Log response information
     */
    @Suppress("unused")
    fun logResponse(endpoint: String, code: Int, response: String?) {
        if (!isDebugMode) return
        
        Log.d(TAG, """
            ===== Network Response =====
            Endpoint: $endpoint
            Status Code: $code
            Response: ${response ?: "Empty"}
            ============================
        """.trimIndent())
    }
    
    /**
     * Log error information
     */
    @Suppress("unused")
    fun logError(endpoint: String, error: Throwable) {
        if (!isDebugMode) return
        
        Log.e(TAG, """
            ===== Network Error =====
            Endpoint: $endpoint
            Error: ${error.message}
            Stack: ${error.stackTraceToString()}
            =========================
        """.trimIndent())
    }
    
    /**
     * Simulate network delay (for testing)
     */
    @Suppress("unused")
    suspend fun simulateNetworkDelay(delayMs: Long = 1000) {
        if (!isDebugMode) return
        kotlinx.coroutines.delay(delayMs)
    }
    
    /**
     * Get current network configuration info
     */
    @Suppress("unused")
    fun getNetworkConfig(context: Context): String {
        val networkType = NetworkUtils.getNetworkType(context)
        val isConnected = NetworkUtils.isNetworkAvailable(context)
        
        return """
            Network Status:
            - Type: $networkType
            - Connected: $isConnected
            - Debug Mode: $isDebugMode
        """.trimIndent()
    }
}
