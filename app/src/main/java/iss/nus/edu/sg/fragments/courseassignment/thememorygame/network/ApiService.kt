package iss.nus.edu.sg.fragments.courseassignment.thememorygame.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
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
        private const val BASE_URL = "http://10.0.2.2:5011/api/"
        private const val TAG = "ApiService"
        private const val TIMEOUT = 10000

        // Standardize endpoint constants to completely avoid typos like Score/Scores
        const val ENDPOINT_LEADERBOARD = "Score/leaderboard"
        const val ENDPOINT_SUBMIT_SCORE = "Score/submit"
        const val ENDPOINT_LOGIN = "Auth/login"
    }

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

    private fun buildUrl(endpoint: String): String {
        val ep = endpoint.trim().removePrefix("/")
        return BASE_URL + ep
    }

    private fun toSafeJson(raw: String): JSONObject {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return JSONObject()
        return try {
            JSONObject(trimmed)
        } catch (e: JSONException) {
            JSONObject().put("raw", trimmed)
        }
    }

    suspend fun post(
        endpoint: String,
        jsonBody: JSONObject,
        token: String? = null
    ): ApiResponse = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null

        try {
            val urlStr = buildUrl(endpoint)
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
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
            Log.d(TAG, "POST body: $jsonBody")

            if (responseCode == 200 || responseCode == 201 || responseCode == 204) {
                val response = if (responseCode == 204) "" else {
                    BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                }
                Log.d(TAG, "POST Response: $response")
                ApiResponse.Success(toSafeJson(response))
            } else {
                val errorResponse = connection.errorStream?.let {
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                } ?: "HTTP Error $responseCode"
                Log.e(TAG, "POST Error Response: $errorResponse")
                ApiResponse.Error(responseCode, errorResponse)
            }

        } catch (e: Exception) {
            Log.e(TAG, "POST Network error: ${e.message}", e)
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
            connection = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT
                readTimeout = TIMEOUT
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "Mozilla/5.0")
                applyAuthHeader(this, token)
            }

            val responseCode = connection.responseCode
            Log.d(TAG, "GET $endpoint -> $urlStr - Response Code: $responseCode")

            if (responseCode == 200) {
                val response = BufferedReader(InputStreamReader(connection.inputStream)).use { it.readText() }
                Log.d(TAG, "GET Response: $response")
                ApiResponse.Success(toSafeJson(response))
            } else {
                val errorResponse = connection.errorStream?.let {
                    BufferedReader(InputStreamReader(it)).use { reader -> reader.readText() }
                } ?: "HTTP Error $responseCode"
                Log.e(TAG, "GET Error Response: $errorResponse")
                ApiResponse.Error(responseCode, errorResponse)
            }

        } catch (e: Exception) {
            Log.e(TAG, "GET Network error: ${e.message}", e)
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
