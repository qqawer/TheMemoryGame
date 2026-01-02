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

        // ✅ 这两个必须对外公开，否则 GameOverActivity 访问会报 "it is private"
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

        // 返回（保险1：代码）
        btnBack.setOnClickListener { onBackPressedDispatcher.onBackPressed() }
        // 防止被遮挡点不到
        btnBack.bringToFront()
        btnBack.translationZ = 100f

        // 返回（保险2：系统 back）
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = finish()
        })

        loadLeaderboard()
    }

    // 返回（保险3：XML android:onClick）
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

            // 只在 pending==true 时显示 “This run / Your best”（一次性）
            val pending = prefs.getBoolean(KEY_LAST_RUN_PENDING, false)

            val intentScore = intent.getIntExtra(EXTRA_LATEST_SCORE_SECONDS, -1)
            val intentUser = intent.getStringExtra(EXTRA_LATEST_USERNAME)

            val savedScore = prefs.getInt(KEY_LAST_SCORE_SECONDS, -1)
            val savedUser = prefs.getString(KEY_LAST_USERNAME, null)

            val latestScore = if (intentScore > 0) intentScore else savedScore
            val latestUser = (intentUser ?: savedUser) ?: auth.getUsername() ?: "You"

            val shouldShowHeader = pending && latestScore > 0

            // 先把 header 显示出来（不管榜单能不能加载）
            if (shouldShowHeader) {
                showHeaderThisRunAndBest(latestUser, latestScore)
                consumeThisRunOnce()
            }

            try {
                val tokenRaw = auth.getToken() // 可能为空：AllowAnonymous 也能看榜

                val api = ApiService()

                // 后端你贴的 Controller 是 ScoresController => /api/Scores/leaderboard
                val candidates = listOf(
                    "/Scores/leaderboard?page=1&size=10",
                    "/scores/leaderboard?page=1&size=10",
                    "/Scores/leaderboard",
                    "/scores/leaderboard",

                    // 兜底（防队友改过命名）
                    "/Score/leaderboard?page=1&size=10",
                    "/score/leaderboard?page=1&size=10",
                    "/Score/leaderboard",
                    "/score/leaderboard"
                )

                var chosen: ApiResponse? = null
                for (ep in candidates) {
                    val r = withContext(Dispatchers.IO) { api.get(ep, token = tokenRaw) }
                    when (r) {
                        is ApiResponse.Success -> { chosen = r; break }
                        is ApiResponse.Error -> {
                            if (r.code == 404) continue
                            chosen = r; break
                        }
                        is ApiResponse.Exception -> { chosen = r; break }
                    }
                }

                pb.visibility = View.GONE

                when (chosen) {
                    null -> {
                        // header 可能已显示；这里补一句错误说明
                        appendStatus("\n\nLeaderboard endpoint not found (404).")
                        return@launch
                    }
                    is ApiResponse.Error -> {
                        appendStatus("\n\nLoad failed (HTTP ${chosen.code}).")
                        Log.e(TAG, "HTTP error ${chosen.code}: ${chosen.message}")
                        return@launch
                    }
                    is ApiResponse.Exception -> {
                        appendStatus("\n\nNetwork error: ${chosen.exception.message}")
                        Log.e(TAG, "Network exception", chosen.exception)
                        return@launch
                    }
                    is ApiResponse.Success -> {
                        val listFromServer = parseLeaderboardStrict(chosen.data)
                            .sortedBy { it.completeTimeSeconds }
                            .take(10) // ✅ 只要 Top10

                        if (listFromServer.isEmpty()) {
                            appendStatus("\n\nNo scores yet.")
                            return@launch
                        }

                        adapter.submit(listFromServer)
                        rv.visibility = View.VISIBLE
                    }
                }

            } catch (e: Exception) {
                pb.visibility = View.GONE
                appendStatus("\n\nLoad failed: ${e.message}")
                Log.e(TAG, "Unexpected error", e)
            }
        }
    }

    private fun showHeaderThisRunAndBest(username: String, latestScore: Int) {
        val runText = if (latestScore > 0) formatHMS(latestScore) else "N/A"

        val bestKey = KEY_BEST_PREFIX + username
        val best = prefs.getInt(bestKey, Int.MAX_VALUE)
        val bestText = if (best != Int.MAX_VALUE) formatHMS(best) else runText

        tvStatus.text = "This run: $username  $runText\nYour best: $bestText"
        tvStatus.visibility = View.VISIBLE
    }

    private fun appendStatus(extra: String) {
        // tvStatus 可能已经是 header，追加信息即可
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
     * 严格按你后端的返回结构解析：
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
