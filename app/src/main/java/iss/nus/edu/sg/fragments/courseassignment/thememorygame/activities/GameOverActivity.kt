package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.PlayActivity
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.databinding.ActivityGameOverBinding

class GameOverActivity : AppCompatActivity() {

    private lateinit var binding: ActivityGameOverBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityGameOverBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. Restart Button: Starts a new game
        binding.btnRestart.setOnClickListener {
            val intent = Intent(this, PlayActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)
            finish()
        }

        // 2. Leaderboard Button: Opens the leaderboard screen
        binding.btnLeaderboard.setOnClickListener {
            val intent = Intent(this, LeaderboardActivity::class.java)
            startActivity(intent)
        }

        // 3. Return Button: Finishes this activity, returning to the main menu
        binding.btnBack.setOnClickListener {
            finish()
        }
    }
}
