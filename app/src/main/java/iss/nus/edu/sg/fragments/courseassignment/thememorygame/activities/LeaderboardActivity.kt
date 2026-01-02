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
        private const val TAG = "Leaderboard"

        private const val PREFS_NAME = "MemoryGamePrefs"
        private const val KEY_LAST_SCORE_SECONDS = "last_score_seconds"
        private const val KEY_LAST_USERNAME = "last_score_username"
        private const val KEY_LAST_RUN_PENDING = "last_run_pending"
        private const val KEY_BEST_PREFIX = "best_seconds_" // best_seconds_<username>

        // ✅ 必须是 public const，GameOverActivity 才能传
        const val EXTRA_LATEST_SCORE_SECONDS = "latest_score_seconds"
        const val EXTRA_LATEST_USERNAME = "latest_username"
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
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        btnBack.bringToFront()
        btnBack.translationZ = 100f

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        loadLeaderboard()
    }

    @Suppress("UNUSED_PARAMETER")
    fun onBackClick(view: View) {
        onBackPressedDispatcher.onBackPressed()
    }

    private fun loadLeaderboard() {
        pb.visibility = View.VISIBLE
        rv.visibility = View.GONE
        tvStatus.visibility = View.GONE

        lifecycleScope.launch {
            val auth = AuthManager.getInstance(this@LeaderboardActivity)

            // ✅ 只在 pending==true 时显示 header（一次性）
            val pending = prefs.getBoolean(KEY_LAST_RUN_PENDING, false)

            val intentScore = intent.getIntExtra(EXTRA_LATEST_SCORE_SECONDS, -1)
            val intentUser = intent.getStringExtra(EXTRA_LATEST_USERNAME)

            val savedScore = prefs.getInt(KEY_LAST_SCORE_SECONDS, -1)
            val savedUser = prefs.getString(KEY_LAST_USERNAME, null)

            val latestScore = if (intentScore > 0) intentScore else savedScore
            val latestUser = (intentUser ?: savedUser) ?: auth.getUsername() ?: "You"

            val shouldShowHeader = pending && latestScore > 0

            // ✅ 先显示 header（rank 先占位），就算榜单加载失败也能看到“这次/最好”
            if (shouldShowHeader) {
                showHeader(latestUser, latestScore, rankText = "…")
                // 不要立刻 consume：如果网络失败你还想再点进来看到一次
                // 这里等加载成功/失败都显示过后再 consume
            }

            try {
                val tokenRaw = auth.getToken() // leaderboard 通常允许匿名
                val api = ApiService()

                val endpoint = "/Scores/leaderboard?page=1&size=10"
                val chosen = withContext(Dispatchers.IO) { api.get(endpoint, token = tokenRaw) }

                pb.visibility = View.GONE

                when (chosen) {
                    is ApiResponse.Error -> {
                        appendStatus("\n\nLoad failed (HTTP ${chosen.code}).")
                        Log.e(TAG, "HTTP error ${chosen.code}: ${chosen.message}")
                        if (shouldShowHeader) consumeThisRunOnce()
                        return@launch
                    }

                    is ApiResponse.Exception -> {
                        appendStatus("\n\nNetwork error: ${chosen.exception.message}")
                        Log.e(TAG, "Network exception", chosen.exception)
                        if (shouldShowHeader) consumeThisRunOnce()
                        return@launch
                    }

                    is ApiResponse.Success -> {
                        val listFromServer = parseLeaderboardStrict(chosen.data)
                            .sortedBy { it.completeTimeSeconds }
                            .take(10)

                        if (listFromServer.isEmpty()) {
                            appendStatus("\n\nNo scores yet.")
                            if (shouldShowHeader) {
                                // 这里 rank 无法算，只显示 N/A
                                showHeader(latestUser, latestScore, rankText = "N/A")
                                consumeThisRunOnce()
                            }
                            return@launch
                        }

                        adapter.submit(listFromServer)
                        rv.visibility = View.VISIBLE

                        // ✅ 计算 “这次排名”
                        if (shouldShowHeader) {
                            val rankText = computeRankText(latestUser, latestScore, listFromServer)
                            showHeader(latestUser, latestScore, rankText)
                            consumeThisRunOnce()
                        }
                    }
                }
            } catch (e: Exception) {
                pb.visibility = View.GONE
                appendStatus("\n\nLoad failed: ${e.message}")
                Log.e(TAG, "Unexpected error", e)
                if (shouldShowHeader) consumeThisRunOnce()
            }
        }
    }

    private fun showHeader(username: String, latestScore: Int, rankText: String) {
        val runText = formatHMS(latestScore)

        val bestKey = KEY_BEST_PREFIX + username
        val best = prefs.getInt(bestKey, Int.MAX_VALUE)
        val bestText = if (best != Int.MAX_VALUE) formatHMS(best) else runText

        tvStatus.text =
            "This run: $username  $runText\nRank: $rankText\nYour best: $bestText"
        tvStatus.visibility = View.VISIBLE
    }

    private fun computeRankText(
        username: String,
        latestScore: Int,
        topList: List<LeaderboardRow>
    ): String {
        // 1) 如果提交成功且榜里能找到同名且时间相等的记录，直接用它的名次
        val exactIdx = topList.indexOfFirst {
            it.username == username && it.completeTimeSeconds == latestScore
        }
        if (exactIdx >= 0) return "#${exactIdx + 1} (on board)"

        // 2) 否则用“插入位置”估算（本地估计，不保证真的在服务器入榜）
        // 例：如果比 3 个人慢 -> rank = 4
        val betterCount = topList.count { it.completeTimeSeconds < latestScore }
        val estimatedRank = betterCount + 1

        return if (estimatedRank <= 10) "#$estimatedRank (estimated)" else ">10 (estimated)"
    }

    private fun appendStatus(extra: String) {
        val base = tvStatus.text?.toString().orEmpty()
        tvStatus.text = if (base.isBlank()) extra.trim() else base + extra
        tvStatus.visibility = View.VISIBLE
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
     * 严格按后端结构解析：
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
            val sec = o.optInt("completeTimeSeconds", 0)
            val at = o.optString("completeAt", "")
            if (sec > 0) out.add(LeaderboardRow(username, sec, at))
        }
        return out
    }
}
