package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameOverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        imageUrls = intent.getStringArrayListExtra("image_urls")

        // ✅ 取本局成绩（秒）
        val latestScore = intent.getIntExtra(LeaderboardActivity.EXTRA_LATEST_SCORE_SECONDS, -1)

        // 1. Restart Button
        binding.btnRestart.setOnClickListener {
            val i = Intent(this, PlayActivity::class.java)
            i.putStringArrayListExtra("image_urls", imageUrls)
            i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(i)
            finish()
        }

        // 2. Leaderboard Button: submit(如果登录) -> open leaderboard
        binding.btnLeaderboard.setOnClickListener {
            val auth = AuthManager.getInstance(this)

            lifecycleScope.launch {
                // ✅ 登录才允许提交（后端 submit 必须 Authorize）
                if (auth.isLoggedIn() && latestScore > 0) {
                    val ok = auth.submitGameScore(latestScore)
                    if (!ok) {
                        Toast.makeText(
                            this@GameOverActivity,
                            "Submit failed (not authorized or server error).",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    // 未登录：只能本地展示 this run，不会上榜
                    if (!auth.isLoggedIn()) {
                        Toast.makeText(
                            this@GameOverActivity,
                            "Not logged in. Score will not be uploaded.",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                val toLb = Intent(this@GameOverActivity, LeaderboardActivity::class.java)
                toLb.putExtra(LeaderboardActivity.EXTRA_LATEST_SCORE_SECONDS, latestScore)
                toLb.putExtra(
                    LeaderboardActivity.EXTRA_LATEST_USERNAME,
                    auth.getUsername() ?: "You"
                )
                startActivity(toLb)
            }
        }

        // 3. Return Button
        binding.btnBack.setOnClickListener {
            val i = Intent(this, MainActivity::class.java)
            i.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(i)
            finish()
        }
    }
}
