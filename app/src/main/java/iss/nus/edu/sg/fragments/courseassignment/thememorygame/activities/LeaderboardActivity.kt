package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
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
        private const val KEY_LAST_RUN_PENDING = "last_run_pending"
        private const val KEY_BEST_PREFIX = "best_seconds_" // best_seconds_<username>

        const val EXTRA_LATEST_SCORE_SECONDS = "latest_score_seconds"
        const val EXTRA_LATEST_USERNAME = "latest_username"
        const val EXTRA_FROM_GAMEOVER = "from_gameover"
    }

    private lateinit var rv: RecyclerView
    private lateinit var pb: ProgressBar

    // HUD status bar (error / empty / load failed)
    private lateinit var tvStatus: TextView

    // Bottom result card (this run / your best)
    private lateinit var resultCard: LinearLayout
    private lateinit var tvThisRunValue: TextView
    private lateinit var tvYourBestValue: TextView

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

        // ✅ New bottom card views (from your updated XML)
        resultCard = findViewById(R.id.resultCard)
        tvThisRunValue = findViewById(R.id.tvThisRunValue)
        tvYourBestValue = findViewById(R.id.tvYourBestValue)

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

        // reset HUD status + bottom card
        tvStatus.visibility = View.GONE
        tvStatus.text = ""
        resultCard.visibility = View.GONE

        lifecycleScope.launch {
            val auth = AuthManager.getInstance(this@LeaderboardActivity)

            val fromGameOver = intent.getBooleanExtra(EXTRA_FROM_GAMEOVER, false)
            val intentScore = intent.getIntExtra(EXTRA_LATEST_SCORE_SECONDS, -1)
            val intentUser = intent.getStringExtra(EXTRA_LATEST_USERNAME)

            val latestScore = intentScore
            val latestUser = (intentUser ?: auth.getUsername() ?: "You").trim()

            val shouldShowHeader = (fromGameOver || intentScore > 0) && latestScore > 0

            Log.d(
                TAG,
                "fromGameOver=$fromGameOver latestScore=$latestScore user=$latestUser showHeader=$shouldShowHeader"
            )

            if (shouldShowHeader) {
                // 先用本地 best 占位（等拉到榜单再用服务器 best 校准）
                val bestLocal = getLocalBestSeconds(latestUser, latestScore)
                showBottomResultCard(latestScore, bestLocal, bestRankWithin10 = null)
            }

            try {
                val tokenRaw = auth.getToken()
                val api = ApiService()

                val endpoint = "/Score/leaderboard?page=1&size=10"
                val resp = withContext(Dispatchers.IO) { api.get(endpoint, token = tokenRaw) }

                pb.visibility = View.GONE

                when (resp) {
                    is ApiResponse.Error -> {
                        showStatus("Load failed (HTTP ${resp.code}).")
                        if (shouldShowHeader) consumeThisRunOnce()
                        return@launch
                    }

                    is ApiResponse.Exception -> {
                        showStatus("Network error: ${resp.exception.message}")
                        if (shouldShowHeader) consumeThisRunOnce()
                        return@launch
                    }

                    is ApiResponse.Success -> {
                        val list = parseLeaderboardStrict(resp.data)
                            .sortedBy { it.completeTimeSeconds }
                            .take(10)

                        if (list.isEmpty()) {
                            showStatus("No scores yet.")
                            if (shouldShowHeader) {
                                val bestLocal = getLocalBestSeconds(latestUser, latestScore)
                                showBottomResultCard(latestScore, bestLocal, bestRankWithin10 = null)
                                consumeThisRunOnce()
                            }
                            return@launch
                        }

                        adapter.submit(list)
                        rv.visibility = View.VISIBLE

                        if (shouldShowHeader) {
                            val bestLocal = getLocalBestSeconds(latestUser, latestScore)

                            // ✅ 服务器 top10 里该用户最好成绩（有就拿来校准本地 best）
                            val bestServer = findBestForUserFromTop10(latestUser, list)

                            val unifiedBest = minOf(
                                bestLocal,
                                bestServer ?: Int.MAX_VALUE
                            )

                            // ✅ 写回本地 best（避免“榜单 15s 但 best 还是 21s”）
                            if (unifiedBest != Int.MAX_VALUE) {
                                saveBestSeconds(latestUser, unifiedBest)
                            }

                            // ✅ 名次只跟 best 走：best 在 top10 且精确命中才显示 (#rank)
                            val bestRank = findExactRankWithin10(latestUser, unifiedBest, list)

                            showBottomResultCard(latestScore, unifiedBest, bestRankWithin10 = bestRank)
                            consumeThisRunOnce()
                        }
                    }
                }
            } catch (e: Exception) {
                pb.visibility = View.GONE
                showStatus("Load failed: ${e.message}")
                if (shouldShowHeader) consumeThisRunOnce()
            }
        }
    }

    /**
     * ✅ 底部卡片展示（契合你新的 UI 设计）
     *
     * This run: 永远不显示名次
     * Your best: best 在 Top10 才显示名次 (#rank)
     */
    private fun showBottomResultCard(latestScore: Int, bestSeconds: Int, bestRankWithin10: Int?) {
        val runText = if (latestScore > 0) formatHMS(latestScore) else "N/A"
        val bestText =
            if (bestSeconds > 0 && bestSeconds != Int.MAX_VALUE) formatHMS(bestSeconds) else runText

        val rankSuffix =
            if (bestRankWithin10 != null && bestRankWithin10 in 1..10) " (#$bestRankWithin10)" else ""

        tvThisRunValue.text = runText
        tvYourBestValue.text = bestText + rankSuffix

        resultCard.visibility = View.VISIBLE
    }

    private fun showStatus(message: String) {
        tvStatus.text = message
        tvStatus.visibility = View.VISIBLE
    }

    private fun getLocalBestSeconds(username: String, latestScore: Int): Int {
        val u = username.trim()
        if (u.isEmpty()) return if (latestScore > 0) latestScore else Int.MAX_VALUE

        val bestKey = KEY_BEST_PREFIX + u
        val best = prefs.getInt(bestKey, Int.MAX_VALUE)
        return minOf(best, if (latestScore > 0) latestScore else Int.MAX_VALUE)
    }

    private fun saveBestSeconds(username: String, bestSeconds: Int) {
        val u = username.trim()
        if (u.isEmpty() || bestSeconds <= 0 || bestSeconds == Int.MAX_VALUE) return

        val bestKey = KEY_BEST_PREFIX + u
        val old = prefs.getInt(bestKey, Int.MAX_VALUE)

        if (bestSeconds < old) {
            prefs.edit().putInt(bestKey, bestSeconds).apply()
            Log.d(TAG, "Best updated locally: user=$u best=$bestSeconds (was=$old)")
        }
    }

    /**
     * ✅ 从服务器 top10 里找该用户最好成绩（忽略大小写）
     */
    private fun findBestForUserFromTop10(username: String, topList: List<LeaderboardRow>): Int? {
        val u = username.trim()
        if (u.isEmpty()) return null

        return topList
            .filter { it.username.trim().equals(u, ignoreCase = true) }
            .minOfOrNull { it.completeTimeSeconds }
    }

    /**
     * ✅ 只返回“能在当前 top10 列表里精确命中 best”的排名，否则 null
     */
    private fun findExactRankWithin10(
        username: String,
        bestSeconds: Int,
        topList: List<LeaderboardRow>
    ): Int? {
        val u = username.trim()
        if (u.isEmpty()) return null
        if (bestSeconds <= 0 || bestSeconds == Int.MAX_VALUE) return null

        val idx = topList.indexOfFirst {
            it.username.trim().equals(u, ignoreCase = true) &&
                    it.completeTimeSeconds == bestSeconds
        }
        return if (idx in 0..9) idx + 1 else null
    }

    private fun consumeThisRunOnce() {
        prefs.edit().putBoolean(KEY_LAST_RUN_PENDING, false).apply()
    }

    private fun formatHMS(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return String.format("%02d:%02d:%02d", h, m, s)
    }

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
