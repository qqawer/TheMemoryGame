package com.example.memorygameandroid.data.network

import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitClient {

    // ✅ 模拟器访问电脑 localhost 必须用 10.0.2.2
    private const val BASE_URL = "http://10.0.2.2:5011/"

    fun create(): ApiService {
        val client = OkHttpClient.Builder().build()

        return Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}
