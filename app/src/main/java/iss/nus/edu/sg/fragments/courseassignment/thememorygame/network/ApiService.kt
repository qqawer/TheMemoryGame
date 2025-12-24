package iss.nus.edu.sg.fragments.courseassignment.thememorygame.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Network communication base class
 * Handles all HTTP communication with the .NET backend
 */
class ApiService {
    
    companion object {
        // Backend server address - UPDATED to match API documentation
        private const val BASE_URL = "http://10.0.2.2:5011/api"  // For emulator (port 5011)
        // private const val BASE_URL = "http://192.168.x.x:5011/api"  // For real device
        
        private const val TAG = "ApiService"
        private const val TIMEOUT = 10000  // 10 seconds timeout
    }
    
    /**
     * Execute POST request
     * @param endpoint API endpoint (e.g. "/Auth/login")
     * @param jsonBody JSON request body
     * @param token Optional Bearer token
     * @return ApiResponse with result, null if failed
     */
    suspend fun post(
        endpoint: String, 
        jsonBody: JSONObject,
        token: String? = null
    ): ApiResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        
        try {
            val url = URL("$BASE_URL$endpoint")
            connection = url.openConnection() as HttpURLConnection
            
            // Set request properties
            connection.apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                
                // Set headers
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                
                // Add Authorization header if token exists
                token?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
            }
            
            // Write request body
            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }
            
            // Read response
            val responseCode = connection.responseCode
            Log.d(TAG, "POST $endpoint - Response Code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK || 
                responseCode == HttpURLConnection.HTTP_CREATED) {
                
                val response = BufferedReader(
                    InputStreamReader(connection.inputStream)
                ).use { it.readText() }
                
                Log.d(TAG, "Response: $response")
                ApiResponse.Success(JSONObject(response))
                
            } else {
                // Read error response
                val errorResponse = connection.errorStream?.let {
                    BufferedReader(InputStreamReader(it)).use { reader ->
                        reader.readText()
                    }
                } ?: "HTTP Error $responseCode"
                
                Log.e(TAG, "Error Response: $errorResponse")
                ApiResponse.Error(responseCode, errorResponse)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}", e)
            ApiResponse.Exception(e)
            
        } finally {
            connection?.disconnect()
        }
    }
    
    /**
     * Execute GET request
     * @param endpoint API endpoint
     * @param token Optional Bearer token
     * @return ApiResponse with result, null if failed
     */
    suspend fun get(
        endpoint: String,
        token: String? = null
    ): ApiResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        
        try {
            val url = URL("$BASE_URL$endpoint")
            connection = url.openConnection() as HttpURLConnection
            
            connection.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                
                token?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
            }
            
            val responseCode = connection.responseCode
            Log.d(TAG, "GET $endpoint - Response Code: $responseCode")
            
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(
                    InputStreamReader(connection.inputStream)
                ).use { it.readText() }
                
                ApiResponse.Success(JSONObject(response))
            } else {
                val errorResponse = connection.errorStream?.let {
                    BufferedReader(InputStreamReader(it)).use { reader ->
                        reader.readText()
                    }
                } ?: "HTTP Error $responseCode"
                
                ApiResponse.Error(responseCode, errorResponse)
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Network error: ${e.message}", e)
            ApiResponse.Exception(e)
            
        } finally {
            connection?.disconnect()
        }
    }
}

/**
 * API response wrapper class
 */
sealed class ApiResponse {
    data class Success(val data: JSONObject) : ApiResponse()
    data class Error(val code: Int, val message: String) : ApiResponse()
    data class Exception(val exception: Throwable) : ApiResponse()
}
