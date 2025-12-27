package com.example.memorygameandroid.data.session

import android.content.Context

class SessionManager(context: Context) {
    private val sp = context.getSharedPreferences("auth", Context.MODE_PRIVATE)

    fun saveToken(token: String) {
        sp.edit().putString("jwt", token).apply()
    }

    fun getToken(): String? = sp.getString("jwt", null)

    fun clear() {
        sp.edit().clear().apply()
    }
}
