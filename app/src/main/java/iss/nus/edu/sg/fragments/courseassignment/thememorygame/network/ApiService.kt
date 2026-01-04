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
        // ✅ 注意：末尾加 "/"，避免拼接时出现 apiScore 这种坑
        private const val BASE_URL = "http://10.0.2.2:5011/api/"  // For emulator (port 5011)
        private const val TAG = "ApiService"
        private const val TIMEOUT = 10000  // 10 seconds timeout
    }

    /**
     * token 规则：
     * - 传入 "Bearer xxx" => 原样使用
     * - 传入 "RAW xxx"    => 直接用 xxx 作为 Authorization 值（不加 Bearer）
     * - 其他情况          => 自动拼 "Bearer <token>"
     */
    private fun applyAuthHeader(connection: HttpURLConnection, token: String?) {
        val t = token?.trim().orEmpty()
        if (t.isEmpty()) return

        val value = when {
            t.startsWith("RAW ", ignoreCase = true) -> t.substring(4).trim()
            t.startsWith("Bearer ", ignoreCase = true) -> t
            else -> "Bearer $t"
        }

        connection.setRequestProperty("Authorization", value)

        val mode = when {
            t.startsWith("RAW ", true) -> "RAW"
            t.startsWith("Bearer ", true) -> "BEARER_AS_IS"
            else -> "BEARER_ADDED"
        }
        Log.d(TAG, "Auth header mode=$mode authLen=${value.length} tokenLen=${t.length}")
    }

    /**
     * ✅ 统一 endpoint 拼接：
     * - 允许传 "/Score/leaderboard?page=1&size=10"
     * - 也允许传 "Score/leaderboard?page=1&size=10"
     * 最终都会正确拼成 BASE_URL + endpoint
     */
    private fun buildUrl(endpoint: String): String {
        val ep = endpoint.trim().removePrefix("/")  // 去掉开头的 "/"
        return BASE_URL + ep
    }

    suspend fun post(
        endpoint: String,
        jsonBody: JSONObject,
        token: String? = null
    ): ApiResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            val urlStr = buildUrl(endpoint)
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "POST"
                doOutput = true
                doInput = true
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT

                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")

                applyAuthHeader(this, token)
            }

            OutputStreamWriter(connection.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "POST $endpoint -> $urlStr - Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK ||
                responseCode == HttpURLConnection.HTTP_CREATED
            ) {
                val response = BufferedReader(
                    InputStreamReader(connection.inputStream)
                ).use { it.readText() }

                Log.d(TAG, "Response: $response")
                ApiResponse.Success(JSONObject(response))
            } else {
                val errorResponse = connection.errorStream?.let {
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
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

    suspend fun get(
        endpoint: String,
        token: String? = null
    ): ApiResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            val urlStr = buildUrl(endpoint)
            val url = URL(urlStr)
            connection = url.openConnection() as HttpURLConnection

            connection.apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT

                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")

                applyAuthHeader(this, token)
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "GET $endpoint -> $urlStr - Response Code: $responseCode")

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = BufferedReader(
                    InputStreamReader(connection.inputStream)
                ).use { it.readText() }

                ApiResponse.Success(JSONObject(response))
            } else {
                val errorResponse = connection.errorStream?.let {
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
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

sealed class ApiResponse {
    data class Success(val data: JSONObject) : ApiResponse()
    data class Error(val code: Int, val message: String) : ApiResponse()
    data class Exception(val exception: Throwable) : ApiResponse()
}
