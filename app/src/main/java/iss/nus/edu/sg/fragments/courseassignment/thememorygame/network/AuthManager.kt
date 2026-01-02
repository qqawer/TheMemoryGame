package iss.nus.edu.sg.fragments.courseassignment.thememorygame.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import org.json.JSONObject

class AuthManager(context: Context) {

    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = "MemoryGamePrefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_IS_PAID_USER = "is_paid_user"

        @Volatile
        private var instance: AuthManager? = null

        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val apiService = ApiService()

    // =========================
    // Public getters
    // =========================
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)?.trim()
    fun getUsername(): String? = prefs.getString(KEY_USERNAME, null)
    fun isPaidUser(): Boolean = prefs.getBoolean(KEY_IS_PAID_USER, false)
    fun isLoggedIn(): Boolean = !getToken().isNullOrEmpty()

    fun logout() {
        prefs.edit().clear().apply()
        Log.d(TAG, "User logged out")
    }

    // =========================
    // Login
    // =========================
    suspend fun login(username: String, password: String): LoginResult {
        if (username.isBlank() || password.isBlank()) {
            return LoginResult.Error("Username and password cannot be empty")
        }

        val requestBody = JSONObject().apply {
            put("username", username)
            put("password", password)
        }

        Log.d(TAG, "Attempting login for user: $username")

        return when (val response = apiService.post("/Auth/login", requestBody)) {
            is ApiResponse.Success -> {
                try {
                    val responseData = response.data
                    val code = responseData.optInt("code", 0)
                    val message = responseData.optString("message", "")

                    if (code != 200) {
                        return LoginResult.Error(message.ifEmpty { "Login failed" })
                    }

                    val data = responseData.optJSONObject("data")
                        ?: return LoginResult.Error("Invalid server response format")

                    val token = data.optString("token", "").trim()
                    if (token.isEmpty()) return LoginResult.Error("Server returned empty token")

                    val isPaidUser = parseIsPaidFromToken(token)
                    saveAuthInfo(username, token, isPaidUser)

                    Log.d(
                        TAG,
                        "Login ok: $username isPaid=$isPaidUser tokenLen=${token.length} hasDot=${token.contains(".")}"
                    )

                    // ✅ 调试：打印 JWT payload 的关键字段（不打印完整 token）
                    debugJwtToken(token)

                    LoginResult.Success(username, token, isPaidUser)
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing login response", e)
                    LoginResult.Error("Failed to parse server response")
                }
            }

            is ApiResponse.Error -> {
                Log.e(TAG, "Login failed: ${response.code} ${response.message}")
                when (response.code) {
                    401 -> LoginResult.Error("Invalid username or password")
                    404 -> LoginResult.Error("User not found")
                    500 -> LoginResult.Error("Server error, please try again later")
                    else -> LoginResult.Error("Login failed: ${response.message}")
                }
            }

            is ApiResponse.Exception -> {
                Log.e(TAG, "Network exception during login", response.exception)
                LoginResult.Error("Network connection failed, please check your network")
            }
        }
    }

    // =========================
    // Submit Score
    // =========================
    /**
     * ✅ 提交成绩：后端是 ScoresController
     * POST /api/Scores/submit  (Authorize)
     * ApiService BASE_URL 已包含 /api，所以这里写 /Scores/submit
     */
    suspend fun submitGameScore(completionTimeSeconds: Int): Boolean {
        val token = getToken()
        if (token.isNullOrEmpty()) {
            Log.e(TAG, "submitGameScore blocked: no token (not logged in)")
            return false
        }

        // ✅ 调试：每次 submit 前也输出一次 claims（看是否过期/iss/aud等）
        debugJwtToken(token)

        val requestBody = JSONObject().apply {
            put("completionTimeSeconds", completionTimeSeconds)
        }

        // 1) 先按正常 Bearer 流程提交（ApiService 会自动加 Bearer）
        val resp1 = apiService.post("/Scores/submit", requestBody, token)

        // 2) 如果 401，自动再试一次 “RAW token”（不带 Bearer）
        val finalResp = if (resp1 is ApiResponse.Error && resp1.code == 401) {
            val pure = token.removePrefix("Bearer").trim()
            Log.e(TAG, "submitGameScore got 401. Retrying with RAW token... tokenLen=${pure.length}")
            apiService.post("/Scores/submit", requestBody, "RAW $pure")
        } else resp1

        return when (finalResp) {
            is ApiResponse.Success -> {
                val root = finalResp.data

                // ✅ 有些后端不返回 code 字段；有些返回 201
                val hasCode = root.has("code")
                val code = if (hasCode) root.optInt("code", 0) else 200
                val msg = root.optString("message", "")

                val ok = (code == 200 || code == 201)

                Log.d(TAG, "submitGameScore success: serverCode=$code ok=$ok msg=$msg raw=$root")
                ok
            }

            is ApiResponse.Error -> {
                val human = when (finalResp.code) {
                    401 -> "Unauthorized (token invalid/expired OR backend auth config mismatch)"
                    403 -> "Forbidden (no permission)"
                    404 -> "Not Found (endpoint wrong)"
                    500 -> "Server error"
                    else -> "HTTP ${finalResp.code}"
                }
                Log.e(TAG, "submitGameScore error: $human body=${finalResp.message}")
                false
            }

            is ApiResponse.Exception -> {
                Log.e(TAG, "submitGameScore exception", finalResp.exception)
                false
            }
        }
    }

    // =========================
    // Token helpers
    // =========================
    private fun saveAuthInfo(username: String, token: String, isPaidUser: Boolean) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)   // 存 token 原样（可能是纯JWT，也可能是 "Bearer xxx"）
            putString(KEY_USERNAME, username)
            putBoolean(KEY_IS_PAID_USER, isPaidUser)
            apply()
        }
    }

    private fun parseIsPaidFromToken(token: String): Boolean {
        return try {
            // 兼容 token 前面带 "Bearer "
            val pure = token.removePrefix("Bearer").trim()

            val parts = pure.split(".")
            if (parts.size < 2) return false

            val payload = parts[1]
            val decodedBytes =
                Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val decodedString = String(decodedBytes)

            val payloadJson = JSONObject(decodedString)
            val isPaidString = payloadJson.optString("IsPaid", "False")
            isPaidString.equals("True", ignoreCase = true) ||
                    isPaidString.equals("true", ignoreCase = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JWT token", e)
            false
        }
    }

    /**
     * ✅ Debug JWT payload (不打印完整 token，只打印 payload 里的关键字段)
     * 用来判断：exp 是否过期、iss/aud 是否为空或不匹配、有没有 userId/name 等。
     */
    private fun debugJwtToken(token: String) {
        try {
            val pure = token.removePrefix("Bearer").trim()
            val parts = pure.split(".")
            if (parts.size < 2) {
                Log.e(TAG, "Token is not JWT (parts<2). tokenLen=${pure.length}")
                return
            }

            val payload = parts[1]
            val decodedBytes = Base64.decode(
                payload,
                Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
            )
            val decodedString = String(decodedBytes)
            val json = JSONObject(decodedString)

            val exp = json.optLong("exp", 0L)
            val iss = json.optString("iss", "")
            val aud = json.optString("aud", "")
            val sub = json.optString("sub", "")

            // 常见的用户标识字段（不同后端会不一样）
            val nameId = when {
                json.has("nameid") -> json.optString("nameid", "")
                json.has("nameidentifier") -> json.optString("nameidentifier", "")
                json.has("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier") ->
                    json.optString("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/nameidentifier", "")
                else -> ""
            }
            val username = when {
                json.has("unique_name") -> json.optString("unique_name", "")
                json.has("name") -> json.optString("name", "")
                json.has("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name") ->
                    json.optString("http://schemas.xmlsoap.org/ws/2005/05/identity/claims/name", "")
                else -> ""
            }

            Log.d(TAG, "JWT claims: iss=$iss aud=$aud sub=$sub nameId=$nameId name=$username exp=$exp payload=$json")
        } catch (e: Exception) {
            Log.e(TAG, "debugJwtToken failed", e)
        }
    }
}

sealed class LoginResult {
    data class Success(
        val username: String,
        val token: String,
        val isPaidUser: Boolean
    ) : LoginResult()

    data class Error(val message: String) : LoginResult()
}
