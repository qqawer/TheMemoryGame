package iss.nus.edu.sg.fragments.courseassignment.thememorygame.network

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import android.util.Log
import org.json.JSONObject

/**
 * Authentication Manager
 * Handles user login, token storage, and user information management
 */
class AuthManager(context: Context) {
    
    companion object {
        private const val TAG = "AuthManager"
        private const val PREFS_NAME = "MemoryGamePrefs"
        private const val KEY_TOKEN = "auth_token"
        private const val KEY_USERNAME = "username"
        private const val KEY_IS_PAID_USER = "is_paid_user"
        
        // Singleton instance
        @Volatile
        private var instance: AuthManager? = null
        
        fun getInstance(context: Context): AuthManager {
            return instance ?: synchronized(this) {
                instance ?: AuthManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
    
    private val prefs: SharedPreferences = 
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val apiService = ApiService()
    
    /**
     * User login
     * @param username Username
     * @param password Password
     * @return LoginResult
     */
    suspend fun login(username: String, password: String): LoginResult {
        // Validate input
        if (username.isBlank() || password.isBlank()) {
            return LoginResult.Error("Username and password cannot be empty")
        }
        
        // Build request body
        val requestBody = JSONObject().apply {
            put("username", username)
            put("password", password)
        }
        
        Log.d(TAG, "Attempting login for user: $username")
        
        // Send login request
        when (val response = apiService.post("/Auth/login", requestBody)) {
            is ApiResponse.Success -> {
                try {
                    val responseData = response.data
                    
                    // Parse API response structure: {code, message, data: {token}}
                    val code = responseData.optInt("code", 0)
                    val message = responseData.optString("message", "")
                    
                    if (code != 200) {
                        return LoginResult.Error(message.ifEmpty { "Login failed" })
                    }
                    
                    val data = responseData.optJSONObject("data")
                    if (data == null) {
                        return LoginResult.Error("Invalid server response format")
                    }
                    
                    val token = data.optString("token", "")
                    if (token.isEmpty()) {
                        return LoginResult.Error("Server returned empty token")
                    }
                    
                    // Parse JWT token to get isPaidUser info
                    val isPaidUser = parseIsPaidFromToken(token)
                    
                    // Save authentication info
                    saveAuthInfo(username, token, isPaidUser)
                    
                    Log.d(TAG, "Login successful for user: $username, isPaid: $isPaidUser")
                    return LoginResult.Success(
                        username = username,
                        token = token,
                        isPaidUser = isPaidUser
                    )
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing login response", e)
                    return LoginResult.Error("Failed to parse server response")
                }
            }
            
            is ApiResponse.Error -> {
                Log.e(TAG, "Login failed: ${response.message}")
                return when (response.code) {
                    401 -> LoginResult.Error("Invalid username or password")
                    404 -> LoginResult.Error("User not found")
                    500 -> LoginResult.Error("Server error, please try again later")
                    else -> LoginResult.Error("Login failed: ${response.message}")
                }
            }
            
            is ApiResponse.Exception -> {
                Log.e(TAG, "Network exception during login", response.exception)
                return LoginResult.Error("Network connection failed, please check your network")
            }
        }
    }
    
    /**
     * Parse JWT token to extract IsPaid information
     * JWT format: header.payload.signature
     */
    private fun parseIsPaidFromToken(token: String): Boolean {
        return try {
            // Split JWT token
            val parts = token.split(".")
            if (parts.size != 3) {
                Log.w(TAG, "Invalid JWT token format")
                return false
            }
            
            // Decode payload (second part)
            val payload = parts[1]
            val decodedBytes = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val decodedString = String(decodedBytes)
            
            Log.d(TAG, "JWT Payload: $decodedString")
            
            // Parse JSON
            val payloadJson = JSONObject(decodedString)
            
            // Get IsPaid field (it's a string "True" or "False" in the token)
            val isPaidString = payloadJson.optString("IsPaid", "False")
            isPaidString.equals("True", ignoreCase = true)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing JWT token", e)
            false  // Default to non-paid user if parsing fails
        }
    }
    
    /**
     * Save authentication info to SharedPreferences
     */
    private fun saveAuthInfo(username: String, token: String, isPaidUser: Boolean) {
        prefs.edit().apply {
            putString(KEY_TOKEN, token)
            putString(KEY_USERNAME, username)
            putBoolean(KEY_IS_PAID_USER, isPaidUser)
            apply()
        }
    }
    
    /**
     * Get currently saved token
     */
    fun getToken(): String? {
        return prefs.getString(KEY_TOKEN, null)
    }
    
    /**
     * Get currently logged in username
     */
    fun getUsername(): String? {
        return prefs.getString(KEY_USERNAME, null)
    }
    
    /**
     * Check if user is a paid user
     */
    fun isPaidUser(): Boolean {
        return prefs.getBoolean(KEY_IS_PAID_USER, false)
    }
    
    /**
     * Check if user is logged in (has valid token)
     */
    fun isLoggedIn(): Boolean {
        return !getToken().isNullOrEmpty()
    }
    
    /**
     * Logout
     */
    fun logout() {
        prefs.edit().clear().apply()
        Log.d(TAG, "User logged out")
    }
    
    /**
     * Submit game completion time to server
     * @param completionTimeSeconds Completion time in seconds
     */
    suspend fun submitGameScore(completionTimeSeconds: Int): Boolean {
        val token = getToken()
        
        if (token == null) {
            Log.e(TAG, "Cannot submit score: Not logged in")
            return false
        }
        
        // Updated request body to match API documentation
        val requestBody = JSONObject().apply {
            put("completionTimeSeconds", completionTimeSeconds)
        }
        
        return when (val response = apiService.post("/Score/submit", requestBody, token)) {
            is ApiResponse.Success -> {
                val responseData = response.data
                val code = responseData.optInt("code", 0)
                
                if (code == 200) {
                    Log.d(TAG, "Score submitted successfully")
                    true
                } else {
                    Log.e(TAG, "Failed to submit score: code $code")
                    false
                }
            }
            else -> {
                Log.e(TAG, "Failed to submit score")
                false
            }
        }
    }
    
    /**
     * Get leaderboard with pagination
     * @param page Page number (default: 1)
     * @param size Page size (default: 10)
     */
    suspend fun getLeaderboard(page: Int = 1, size: Int = 10): LeaderboardResult {
        val token = getToken() ?: return LeaderboardResult.Error("Not logged in")
        
        return when (val response = apiService.get("/Scores/leaderboard?page=$page&size=$size", token)) {
            is ApiResponse.Success -> {
                try {
                    val responseData = response.data
                    val code = responseData.optInt("code", 0)
                    
                    if (code != 200) {
                        return LeaderboardResult.Error("Failed to get leaderboard")
                    }
                    
                    val data = responseData.optJSONObject("data")
                    if (data == null) {
                        return LeaderboardResult.Error("Invalid response format")
                    }
                    
                    val scores = mutableListOf<ScoreEntry>()
                    val itemsArray = data.optJSONArray("items")
                    
                    itemsArray?.let {
                        for (i in 0 until it.length()) {
                            val scoreObj = it.getJSONObject(i)
                            scores.add(
                                ScoreEntry(
                                    username = scoreObj.getString("username"),
                                    completionTime = scoreObj.getInt("completeTimeSeconds"),
                                    completeAt = scoreObj.optString("completeAt", "")
                                )
                            )
                        }
                    }
                    
                    // Parse pagination info
                    val totalCount = data.optInt("totalCount", 0)
                    val pageNumber = data.optInt("pageNumber", 1)
                    val pageSize = data.optInt("pageSize", 10)
                    val totalPages = data.optInt("totalPages", 1)
                    
                    LeaderboardResult.Success(
                        scores = scores,
                        totalCount = totalCount,
                        currentPage = pageNumber,
                        pageSize = pageSize,
                        totalPages = totalPages
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Error parsing leaderboard", e)
                    LeaderboardResult.Error("Failed to parse leaderboard data")
                }
            }
            
            is ApiResponse.Error -> {
                LeaderboardResult.Error("Failed to get leaderboard: ${response.message}")
            }
            
            is ApiResponse.Exception -> {
                LeaderboardResult.Error("Network error")
            }
        }
    }
}

/**
 * Login result wrapper
 */
sealed class LoginResult {
    data class Success(
        val username: String,
        val token: String,
        val isPaidUser: Boolean
    ) : LoginResult()
    
    data class Error(val message: String) : LoginResult()
}

/**
 * Leaderboard result wrapper
 */
sealed class LeaderboardResult {
    data class Success(
        val scores: List<ScoreEntry>,
        val totalCount: Int,
        val currentPage: Int,
        val pageSize: Int,
        val totalPages: Int
    ) : LeaderboardResult()
    
    data class Error(val message: String) : LeaderboardResult()
}

/**
 * Score entry data class
 */
data class ScoreEntry(
    val username: String,
    val completionTime: Int,  // Completion time in seconds
    val completeAt: String    // Completion datetime
)
