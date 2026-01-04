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
        private const val KEY_LAST_RUN_PENDING = "last_run_pending"
        private const val KEY_BEST_PREFIX = "best_seconds_" // best_seconds_<username>

        // ✅ GameOverActivity uses these
        const val EXTRA_LATEST_SCORE_SECONDS = "latest_score_seconds"
        const val EXTRA_LATEST_USERNAME = "latest_username"
        const val EXTRA_FROM_GAMEOVER = "from_gameover"
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

            val fromGameOver = intent.getBooleanExtra(EXTRA_FROM_GAMEOVER, false)
            val intentScore = intent.getIntExtra(EXTRA_LATEST_SCORE_SECONDS, -1)
            val intentUser = intent.getStringExtra(EXTRA_LATEST_USERNAME)

            val latestScore = intentScore
            val latestUser = intentUser ?: auth.getUsername() ?: "You"

            // ✅ 只要从 GameOver 来（或 intentScore>0），就显示 header
            val shouldShowThisRunHeader = (fromGameOver || intentScore > 0) && latestScore > 0

            Log.d(
                TAG,
                "fromGameOver=$fromGameOver intentScore=$intentScore user=$latestUser showHeader=$shouldShowThisRunHeader"
            )

            if (shouldShowThisRunHeader) {
                // 先显示占位（等榜单加载完再用服务器数据覆盖 rank/best）
                showThisRunRankBest(
                    username = latestUser,
                    latestScore = latestScore,
                    rankText = "Loading…",
                    bestSecondsOverride = null
                )
            }

            try {
                val tokenRaw = auth.getToken() // leaderboard 通常允许匿名
                val api = ApiService()

                // ✅ 路径：/api/Score/leaderboard（Score 单数）
                val endpoint = "/Score/leaderboard?page=1&size=10"

                val resp = withContext(Dispatchers.IO) { api.get(endpoint, token = tokenRaw) }

                pb.visibility = View.GONE

                when (resp) {
                    is ApiResponse.Error -> {
                        appendStatus("\n\nLoad failed (HTTP ${resp.code}).")
                        if (shouldShowThisRunHeader) consumeThisRunOnce()
                        return@launch
                    }

                    is ApiResponse.Exception -> {
                        appendStatus("\n\nNetwork error: ${resp.exception.message}")
                        if (shouldShowThisRunHeader) consumeThisRunOnce()
                        return@launch
                    }

                    is ApiResponse.Success -> {
                        val list = parseLeaderboardStrict(resp.data)
                            .sortedBy { it.completeTimeSeconds }
                            .take(10)

                        if (list.isEmpty()) {
                            appendStatus("\n\nNo scores yet.")
                            if (shouldShowThisRunHeader) {
                                showThisRunRankBest(
                                    username = latestUser,
                                    latestScore = latestScore,
                                    rankText = "N/A",
                                    bestSecondsOverride = null
                                )
                                consumeThisRunOnce()
                            }
                            return@launch
                        }

                        adapter.submit(list)
                        rv.visibility = View.VISIBLE

                        if (shouldShowThisRunHeader) {
                            // ✅ 真实 Rank：只以服务器返回的 Top10 为准，不再 estimated
                            val (rankText, bestOverride) = computeRankAndBestFromTop10(
                                username = latestUser,
                                latestScore = latestScore,
                                topList = list
                            )

                            showThisRunRankBest(
                                username = latestUser,
                                latestScore = latestScore,
                                rankText = rankText,
                                bestSecondsOverride = bestOverride
                            )
                            consumeThisRunOnce()
                        }
                    }
                }
            } catch (e: Exception) {
                pb.visibility = View.GONE
                appendStatus("\n\nLoad failed: ${e.message}")
                if (shouldShowThisRunHeader) consumeThisRunOnce()
            }
        }
    }

    /**
     * ✅ Header 展示：
     * - Your best 优先用服务器返回的 best（如果在 Top10 里找得到该用户）
     * - 找不到就退回用本地 prefs（你们现在就是这么存的）
     */
    private fun showThisRunRankBest(
        username: String,
        latestScore: Int,
        rankText: String,
        bestSecondsOverride: Int?
    ) {
        val runText = if (latestScore > 0) formatHMS(latestScore) else "N/A"

        val bestKey = KEY_BEST_PREFIX + username
        val localBest = prefs.getInt(bestKey, Int.MAX_VALUE)

        val bestSeconds = bestSecondsOverride
            ?: if (localBest != Int.MAX_VALUE) localBest else latestScore

        val bestText = if (bestSeconds > 0) formatHMS(bestSeconds) else runText

        val extraHint = if (bestSecondsOverride != null && latestScore > bestSeconds) {
            "\n(This run didn't beat your best)"
        } else ""

        tvStatus.text = "This run: $username  $runText\nRank: $rankText\nYour best: $bestText$extraHint"
        tvStatus.visibility = View.VISIBLE
    }

    /**
     * ✅ 真实规则：
     * - 排行榜只显示 Top10（服务器返回的就是 Top10）
     * - Rank 只在 Top10 内才有意义；不在则显示 Not in Top 10
     * - 同一个用户通常只出现一次（best），所以 27 不会“新增一行”覆盖 18
     */
    private fun computeRankAndBestFromTop10(
        username: String,
        latestScore: Int,
        topList: List<LeaderboardRow>
    ): Pair<String, Int?> {
        val idxByUser = topList.indexOfFirst {
            it.username.equals(username, ignoreCase = true)
        }

        if (idxByUser < 0) {
            // ✅ 你不在 Top10：不要估算全服排名
            return "Not in Top 10" to null
        }

        val serverBest = topList[idxByUser].completeTimeSeconds
        val rank = "#${idxByUser + 1}"

        // ✅ 如果本次不是 best，明确说明 best 是多少（避免误会“没提交”）
        val note = if (latestScore <= serverBest) {
            "(best)"
        } else {
            "(best ${formatHMS(serverBest)})"
        }

        return "$rank $note" to serverBest
    }

    private fun appendStatus(extra: String) {
        val base = tvStatus.text?.toString().orEmpty()
        tvStatus.text = if (base.isBlank()) extra.trim() else base + extra
        tvStatus.visibility = View.VISIBLE
    }

    private fun consumeThisRunOnce() {
        prefs.edit()
            .putBoolean(KEY_LAST_RUN_PENDING, false)
            .apply()
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
