package com.example.memorygameandroid.data.network

import com.google.gson.JsonElement
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface ApiService {

    @POST("/api/Auth/login")
    suspend fun login(@Body body: LoginRequest): ApiResponse<LoginData>

    // ✅ 广告：用 JsonElement 接住（后端 data 有时是对象/有时是数组）
    @GET("/api/Ad/active")
    suspend fun getActiveAd(@Header("Authorization") bearer: String): ApiResponse<JsonElement>

    @POST("/api/Scores/submit")
    suspend fun submitScore(
        @Header("Authorization") bearer: String,
        @Body body: ScoreSubmitRequest
    ): ApiResponse<Any>

    // ✅ 排行榜：同样用 JsonElement 接住
    @GET("/api/Scores/leaderboard")
    suspend fun getLeaderboard(@Header("Authorization") bearer: String): ApiResponse<JsonElement>
}
