package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.FetchActivity
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.databinding.ActivityGameOverBinding
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.AuthManager
import kotlinx.coroutines.launch

class GameOverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameOverBinding
    private var imageUrls: ArrayList<String>? = null

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
    }

    companion object {
        private const val TAG = "GameOverActivity"

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

        val auth = AuthManager.getInstance(this)

        // Prioritize username from intent (passed from PlayActivity)
        val username = intent.getStringExtra(LeaderboardActivity.EXTRA_LATEST_USERNAME)
            ?: auth.getUsername()
            ?: "You"

        // Try everything to read the score (in seconds) for this round
        val latestScore = readLatestScoreSeconds(intent)

        Log.d(TAG, "incoming extras: latestScore=$latestScore username=$username")

        // 1) Restart
        binding.btnRestart.setOnClickListener {
            val i = Intent(this, FetchActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(i)
            finish()
        }

        // 2) Leaderboard
        binding.btnLeaderboard.setOnClickListener {
            if (latestScore <= 0) {
                Toast.makeText(
                    this,
                    "Score not found. (This run header will be hidden)",
                    Toast.LENGTH_SHORT
                ).show()

                // Still allow entering the leaderboard, just don't display "this run"
                startActivity(Intent(this, LeaderboardActivity::class.java))
                return@setOnClickListener
            }

            // Has score: save best + pending
            saveThisRunAndBest(username, latestScore)

            lifecycleScope.launch {
                // Submit only when logged in
                if (auth.isLoggedIn()) {
                    val ok = auth.submitGameScore(latestScore)
                    if (!ok) {
                        Toast.makeText(
                            this@GameOverActivity,
                            "Submit failed (Unauthorized / Server error).",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    Toast.makeText(
                        this@GameOverActivity,
                        "Not logged in. Score will not be uploaded.",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                // Open leaderboard: pass score & username, and mark as fromGameOver
                val toLb = Intent(this@GameOverActivity, LeaderboardActivity::class.java)
                toLb.putExtra(LeaderboardActivity.EXTRA_LATEST_SCORE_SECONDS, latestScore)
                toLb.putExtra(LeaderboardActivity.EXTRA_LATEST_USERNAME, username)
                toLb.putExtra(LeaderboardActivity.EXTRA_FROM_GAMEOVER, true)
                startActivity(toLb)
            }
        }

        // 3) Back to Menu
        binding.btnBack.setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(i)
            finish()
        }
    }

    private fun readLatestScoreSeconds(intent: Intent): Int {
        val keys = listOf(
            LeaderboardActivity.EXTRA_LATEST_SCORE_SECONDS, // "latest_score_seconds"
            "latest_score_seconds",
            "completionTimeSeconds",
            "completion_time_seconds",
            "CompleteTimeSeconds",
            "completeTimeSeconds",
            "scoreSeconds",
            "score_seconds"
        )

        for (k in keys) {
            val v = intent.getIntExtra(k, -1)
            if (v > 0) return v
        }

        for (k in keys) {
            val v = intent.getLongExtra(k, -1L)
            if (v > 0L && v <= Int.MAX_VALUE) return v.toInt()
        }

        for (k in keys) {
            val s = intent.getStringExtra(k)?.trim()
            val n = s?.toIntOrNull()
            if (n != null && n > 0) return n
        }

        return -1
    }

    private fun saveThisRunAndBest(username: String, latestScore: Int) {
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
