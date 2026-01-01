package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.R
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.leaderboard.LeaderboardAdapter
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.leaderboard.LeaderboardRow
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.ApiResponse
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.ApiService
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.AuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LeaderboardActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "MemoryGamePrefs"
        private const val KEY_LAST_SCORE_SECONDS = "last_score_seconds"
        private const val KEY_LAST_USERNAME = "last_score_username"
        private const val KEY_LAST_RUN_PENDING = "last_run_pending"
    }

    private lateinit var rv: RecyclerView
    private lateinit var pb: ProgressBar
    private lateinit var tvStatus: TextView
    private val adapter = LeaderboardAdapter()

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        rv = findViewById(R.id.rvLeaderboard)
        pb = findViewById(R.id.pbLoading)
        tvStatus = findViewById(R.id.tvStatus)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val btnBack = findViewById<ImageButton>(R.id.btnBack)

        // ✅ 代码绑定返回
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }

        // ✅ 防止被覆盖导致点不到
        btnBack.bringToFront()
        btnBack.translationZ = 100f

        // ✅ 系统返回
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        loadLeaderboard()
    }

    /**
     * ✅ XML android:onClick 用（第三保险）
     */
    @Suppress("UNUSED_PARAMETER")
    fun onBackClick(view: View) {
        onBackPressedDispatcher.onBackPressed()
    }

    private fun loadLeaderboard() {
        pb.visibility = View.VISIBLE
        tvStatus.visibility = View.GONE
        rv.visibility = View.GONE

        lifecycleScope.launch {
            val auth = AuthManager.getInstance(this@LeaderboardActivity)

            // ✅ 只有 pending==true 才允许显示 This run（一次性）
            val pending = prefs.getBoolean(KEY_LAST_RUN_PENDING, false)

            // Intent 优先，否则 prefs
            val intentScore = intent.getIntExtra("latest_score_seconds", -1)
            val intentUser = intent.getStringExtra("latest_username")

            val savedScore = prefs.getInt(KEY_LAST_SCORE_SECONDS, -1)
            val savedUser = prefs.getString(KEY_LAST_USERNAME, null)

            val latestScore = if (intentScore > 0) intentScore else savedScore
            val latestUser = (intentUser ?: savedUser) ?: auth.getUsername() ?: "You"

            val shouldShowThisRun = pending && latestScore > 0

            val localList = ArrayList<LeaderboardRow>()
            if (shouldShowThisRun) {
                // ✅ 注意：LeaderboardRow 的参数名是 completeTimeSeconds
                localList.add(
                    LeaderboardRow(
                        username = latestUser,
                        completeTimeSeconds = latestScore,
                        completeAt = "(this run)"
                    )
                )
            }

            try {
                val tokenRaw = auth.getToken()

                // 未登录：只显示本局一次 或提示登录
                if (tokenRaw.isNullOrEmpty()) {
                    pb.visibility = View.GONE
                    if (localList.isNotEmpty()) {
                        adapter.submit(localList)
                        rv.visibility = View.VISIBLE
                        showRunHeaderAndConsumeOnce(latestUser, latestScore, rank = null)
                    } else {
                        tvStatus.text = "Please login first to view leaderboard."
                        tvStatus.visibility = View.VISIBLE
                    }
                    return@launch
                }

                val api = ApiService()

                // ✅ 容错候选（防大小写/参数名差异）
                val candidates = listOf(
                    "/Score/leaderboard?page=1&size=50",
                    "/score/leaderboard?page=1&size=50",
                    "/Score/leaderboard?pageNumber=1&pageSize=50",
                    "/score/leaderboard?pageNumber=1&pageSize=50",
                    "/Score/leaderboard",
                    "/score/leaderboard",
                    "/scores/leaderboard?page=1&size=50",
                    "/scores/leaderboard"
                )

                var chosen: ApiResponse? = null
                var triedAll404 = true

                for (ep in candidates) {
                    val r = withContext(Dispatchers.IO) { api.get(ep, token = tokenRaw) }
                    when (r) {
                        is ApiResponse.Success -> { chosen = r; triedAll404 = false; break }
                        is ApiResponse.Error -> {
                            if (r.code == 404) continue
                            chosen = r; triedAll404 = false; break
                        }
                        is ApiResponse.Exception -> { chosen = r; triedAll404 = false; break }
                    }
                }

                if (chosen == null) {
                    pb.visibility = View.GONE
                    tvStatus.text = "Load failed: Unknown error"
                    tvStatus.visibility = View.VISIBLE
                    if (shouldShowThisRun) consumeThisRunOnce()
                    return@launch
                }

                val listFromServer = when (chosen) {
                    is ApiResponse.Success -> parseLeaderboardStrict(chosen.data)
                    is ApiResponse.Error -> {
                        pb.visibility = View.GONE
                        tvStatus.text = if (triedAll404) {
                            "Load failed (HTTP 404): leaderboard endpoint not found."
                        } else {
                            "Load failed (HTTP ${chosen.code}): ${chosen.message}"
                        }
                        tvStatus.visibility = View.VISIBLE

                        // 服务端失败也别一直 pending
                        if (localList.isNotEmpty()) {
                            adapter.submit(localList)
                            rv.visibility = View.VISIBLE
                            showRunHeaderAndConsumeOnce(latestUser, latestScore, rank = null)
                        } else if (pending) {
                            consumeThisRunOnce()
                        }
                        return@launch
                    }
                    is ApiResponse.Exception -> {
                        pb.visibility = View.GONE
                        tvStatus.text = "Network error: ${chosen.exception.message}"
                        tvStatus.visibility = View.VISIBLE
                        Log.e("Leaderboard", "Network exception", chosen.exception)

                        if (localList.isNotEmpty()) {
                            adapter.submit(localList)
                            rv.visibility = View.VISIBLE
                            showRunHeaderAndConsumeOnce(latestUser, latestScore, rank = null)
                        } else if (pending) {
                            consumeThisRunOnce()
                        }
                        return@launch
                    }
                }

                val merged = ArrayList<LeaderboardRow>().apply {
                    addAll(localList)
                    addAll(listFromServer)
                }

                // ✅ 注意：排序字段也是 completeTimeSeconds
                val finalList = merged
                    .distinctBy { "${it.username}_${it.completeTimeSeconds}_${it.completeAt}" }
                    .sortedBy { it.completeTimeSeconds }

                val rank = if (shouldShowThisRun) {
                    val idx = finalList.indexOfFirst {
                        it.username == latestUser && it.completeTimeSeconds == latestScore
                    }
                    if (idx >= 0) idx + 1 else null
                } else null

                pb.visibility = View.GONE
                if (finalList.isEmpty()) {
                    tvStatus.text = "No scores yet."
                    tvStatus.visibility = View.VISIBLE
                    if (shouldShowThisRun) consumeThisRunOnce()
                } else {
                    adapter.submit(finalList)
                    rv.visibility = View.VISIBLE
                    if (shouldShowThisRun) showRunHeaderAndConsumeOnce(latestUser, latestScore, rank)
                }

            } catch (e: Exception) {
                pb.visibility = View.GONE
                tvStatus.text = "Load failed: ${e.message}"
                tvStatus.visibility = View.VISIBLE
                Log.e("Leaderboard", "Unexpected error", e)
                if (shouldShowThisRun) consumeThisRunOnce()
            }
        }
    }

    // ✅ 显示一次后立即清理（保证“从此再也不显示”）
    private fun showRunHeaderAndConsumeOnce(username: String, seconds: Int, rank: Int?) {
        val timeText = formatHMS(seconds)
        tvStatus.text = if (rank != null) {
            "This run: $username  $timeText   (Rank #$rank)"
        } else {
            "This run: $username  $timeText"
        }
        tvStatus.visibility = View.VISIBLE
        consumeThisRunOnce()
    }

    private fun consumeThisRunOnce() {
        prefs.edit()
            .putBoolean(KEY_LAST_RUN_PENDING, false)
            .remove(KEY_LAST_SCORE_SECONDS)
            .remove(KEY_LAST_USERNAME)
            .apply()
    }

    private fun formatHMS(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

    /**
     * ✅ 严格按 API 文档解析：
     * { code, message, data: { items:[{username, completeTimeSeconds, completeAt}] } }
     */
    private fun parseLeaderboardStrict(root: JSONObject): List<LeaderboardRow> {
        val code = root.optInt("code", 0)
        if (code != 200) return emptyList()

        val data = root.optJSONObject("data") ?: return emptyList()
        val items: JSONArray = data.optJSONArray("items") ?: return emptyList()

        val out = ArrayList<LeaderboardRow>(items.length())
        for (i in 0 until items.length()) {
            val o = items.optJSONObject(i) ?: continue
            val username = o.optString("username", "unknown")

            // ✅ 后端可能用 completeTimeSeconds 或 completionTimeSeconds，兼容两种
            val sec = o.optInt("completeTimeSeconds", o.optInt("completionTimeSeconds", 0))

            val at = o.optString("completeAt", "")
            if (sec > 0) out.add(LeaderboardRow(username = username, completeTimeSeconds = sec, completeAt = at))
        }
        return out
    }
}
