package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.PlayActivity
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.databinding.ActivityGameOverBinding
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.AuthManager
import kotlinx.coroutines.launch

class GameOverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameOverBinding
    private var imageUrls: ArrayList<String>? = null

    // ✅ 必须和 LeaderboardActivity 用同一套 prefs/keys
    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    companion object {
        private const val PREFS_NAME = "MemoryGamePrefs"
        private const val KEY_LAST_SCORE_SECONDS = "last_score_seconds"
        private const val KEY_LAST_USERNAME = "last_score_username"
        private const val KEY_LAST_RUN_PENDING = "last_run_pending"
        private const val KEY_BEST_PREFIX = "best_seconds_" // best_seconds_<username>
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameOverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUrls = intent.getStringArrayListExtra("image_urls")

        // ✅ 取本局成绩（秒）
        val latestScore = intent.getIntExtra(LeaderboardActivity.EXTRA_LATEST_SCORE_SECONDS, -1)

        // 1) Restart Button
        binding.btnRestart.setOnClickListener {
            val i = Intent(this, PlayActivity::class.java)
            i.putStringArrayListExtra("image_urls", imageUrls)
            i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(i)
            finish()
        }

        // 2) Leaderboard Button: 先本地记录这次成绩/最好成绩 -> (登录则提交) -> 打开 leaderboard
        binding.btnLeaderboard.setOnClickListener {
            val auth = AuthManager.getInstance(this)
            val username = auth.getUsername() ?: "You"

            if (latestScore > 0) {
                saveThisRunAndBest(username, latestScore)
            }

            lifecycleScope.launch {
                // ✅ 登录才提交
                if (auth.isLoggedIn() && latestScore > 0) {
                    val ok = auth.submitGameScore(latestScore)
                    if (!ok) {
                        Toast.makeText(
                            this@GameOverActivity,
                            "Submit failed (Unauthorized / Server error).",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    if (!auth.isLoggedIn()) {
                        Toast.makeText(
                            this@GameOverActivity,
                            "Not logged in. Score will not be uploaded.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                // ✅ 打开排行榜：把本次成绩和用户名带过去（用于立即显示）
                val toLb = Intent(this@GameOverActivity, LeaderboardActivity::class.java)
                toLb.putExtra(LeaderboardActivity.EXTRA_LATEST_SCORE_SECONDS, latestScore)
                toLb.putExtra(LeaderboardActivity.EXTRA_LATEST_USERNAME, username)
                startActivity(toLb)
            }
        }

        // 3) Return Button
        binding.btnBack.setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(i)
            finish()
        }
    }

    private fun saveThisRunAndBest(username: String, latestScore: Int) {
        // ✅ 记录 “这次 run” 让 leaderboard 顶部只显示一次
        // ✅ 同时维护 best（取更小的秒数）
        val bestKey = KEY_BEST_PREFIX + username
        val oldBest = prefs.getInt(bestKey, Int.MAX_VALUE)
        val newBest = minOf(oldBest, latestScore)

        prefs.edit()
            .putInt(KEY_LAST_SCORE_SECONDS, latestScore)
            .putString(KEY_LAST_USERNAME, username)
            .putBoolean(KEY_LAST_RUN_PENDING, true)
            .putInt(bestKey, newBest)
            .apply()
    }
}
