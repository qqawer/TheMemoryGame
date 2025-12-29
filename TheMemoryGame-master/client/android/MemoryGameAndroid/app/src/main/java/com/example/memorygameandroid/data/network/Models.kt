package com.example.memorygameandroid.data.network

import com.google.gson.annotations.SerializedName

data class ApiResponse<T>(
    val code: Int,
    val message: String,
    val data: T?
)

data class LoginRequest(val username: String, val password: String)
data class LoginData(val token: String)

data class ScoreSubmitRequest(val completionTimeSeconds: Int)

// 广告（字段名按你后端 db.sql 的意思写）
data class AdDto(
    val id: Int? = null,
    @SerializedName("adImageUrl") val adImageUrl: String? = null,
    @SerializedName("adTitle") val adTitle: String? = null,
    @SerializedName("isActive") val isActive: Boolean? = null
)

// 排行榜条目（兼容常见命名）
data class LeaderboardItemDto(
    @SerializedName("username") val username: String? = null,
    @SerializedName("userName") val userName: String? = null,

    @SerializedName("completionTimeSeconds") val completionTimeSeconds: Int? = null,
    @SerializedName("timeSeconds") val timeSeconds: Int? = null
) {
    fun displayName(): String = (username ?: userName ?: "Unknown").trim()
    fun displaySeconds(): Int = completionTimeSeconds ?: timeSeconds ?: 0
}
