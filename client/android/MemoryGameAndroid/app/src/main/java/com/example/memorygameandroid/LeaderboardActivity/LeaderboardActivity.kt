package com.example.memorygameandroid

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.memorygameandroid.data.network.LeaderboardItemDto
import com.example.memorygameandroid.data.network.RetrofitClient
import com.example.memorygameandroid.data.session.SessionManager
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var pb: ProgressBar
    private lateinit var tvStatus: TextView
    private val adapter = LeaderboardAdapter()
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        rv = findViewById(R.id.rvLeaderboard)
        pb = findViewById(R.id.pbLoading)
        tvStatus = findViewById(R.id.tvStatus)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // ✅ 顶部返回按钮：一定触发
        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { goBack() }

        // ✅ 系统返回（手势/物理）：同样走 goBack()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        val token = SessionManager(this).getToken()
        if (token.isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        loadLeaderboard("Bearer $token")
    }

    private fun goBack() {
        // 有上一页就 finish()，否则回主界面
        if (!isTaskRoot) {
            finish()
        } else {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }

    private fun loadLeaderboard(bearer: String) {
        val api = RetrofitClient.create()

        pb.visibility = View.VISIBLE
        tvStatus.visibility = View.GONE
        rv.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val resp = api.getLeaderboard(bearer)
                val list = extractLeaderboardList(resp.data)

                pb.visibility = View.GONE

                if (list.isEmpty()) {
                    tvStatus.text = "No scores yet."
                    tvStatus.visibility = View.VISIBLE
                    return@launch
                }

                adapter.submit(list.sortedBy { it.displaySeconds() })
                rv.visibility = View.VISIBLE

            } catch (e: Exception) {
                pb.visibility = View.GONE
                tvStatus.text = "Load failed: ${e.message}"
                tvStatus.visibility = View.VISIBLE
            }
        }
    }

    private fun extractLeaderboardList(data: JsonElement?): List<LeaderboardItemDto> {
        if (data == null || data.isJsonNull) return emptyList()

        val listType = object : TypeToken<List<LeaderboardItemDto>>() {}.type
        return try {
            when {
                data.isJsonArray -> gson.fromJson(data, listType)
                data.isJsonObject -> {
                    val obj = data.asJsonObject
                    val arr = obj.get("items") ?: obj.get("leaderboard") ?: obj.get("data")
                    if (arr != null && arr.isJsonArray) gson.fromJson(arr, listType) else emptyList()
                }
                else -> emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
