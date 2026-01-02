package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.R
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.FetchActivity
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.AuthManager

/**
 * Main Activity - Menu/Game Main Screen
 *
 * File path:
 * app/src/main/java/iss/nus/edu/sg/fragments/courseassignment/thememorygame/activities/MainActivity.kt
 */
class MainActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager

    private lateinit var layoutUserInfo: View
    private lateinit var tvUsername: TextView
    private lateinit var tvVipStatus: TextView

    private lateinit var btnLoginPrompt: Button
    private lateinit var btnLogout: Button
    private lateinit var btnStartGame: Button
    private lateinit var btnLeaderboard: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        authManager = AuthManager.getInstance(this)

        initViews()
        setupClickListeners()
        updateUI()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Exit App")
                    .setMessage("Are you sure you want to exit?")
                    .setPositiveButton("Exit") { _, _ -> finish() }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    private fun initViews() {
        layoutUserInfo = findViewById(R.id.layoutUserInfo)
        tvUsername = findViewById(R.id.tvUsername)
        tvVipStatus = findViewById(R.id.tvVipStatus)

        btnLoginPrompt = findViewById(R.id.btnLoginPrompt)
        btnLogout = findViewById(R.id.btnLogout)
        btnStartGame = findViewById(R.id.btnStartGame)
        btnLeaderboard = findViewById(R.id.btnLeaderboard)
    }

    private fun setupClickListeners() {
        btnLoginPrompt.setOnClickListener { navigateToLogin() }

        btnStartGame.setOnClickListener { handleStartGame() }

        btnLeaderboard.setOnClickListener { navigateToLeaderboard() }

        btnLogout.setOnClickListener { handleLogout() }
    }

    private fun updateUI() {
        if (authManager.isLoggedIn()) showLoggedInUI() else showLoggedOutUI()
    }

    private fun showLoggedInUI() {
        layoutUserInfo.visibility = View.VISIBLE

        val username = authManager.getUsername() ?: "Guest"
        tvUsername.text = username

        val isPaidUser = authManager.isPaidUser()
        if (isPaidUser) {
            tvVipStatus.text = "VIP"
            tvVipStatus.setTextColor(getColor(R.color.vip_gold))
        } else {
            tvVipStatus.text = "Free"
            tvVipStatus.setTextColor(getColor(R.color.free_gray))
        }

        btnLoginPrompt.visibility = View.GONE
        btnLogout.visibility = View.VISIBLE
        btnStartGame.text = getString(R.string.btn_start_game)
    }

    private fun showLoggedOutUI() {
        layoutUserInfo.visibility = View.GONE

        // ✅ 未登录：要显示登录按钮
        btnLoginPrompt.visibility = View.VISIBLE

        btnLogout.visibility = View.GONE
        btnStartGame.text = getString(R.string.btn_start_game)
    }

    private fun handleStartGame() {
        if (authManager.isLoggedIn()) {
            navigateToGame()
        } else {
            showLoginRequiredDialog()
        }
    }

    private fun showLoginRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Login Required")
            .setMessage("You need to login before starting the game. Login now?")
            .setPositiveButton("Login") { _, _ -> navigateToLogin() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun handleLogout() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Confirm") { _, _ ->
                authManager.logout()
                updateUI()
                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun navigateToLogin() {
        startActivity(Intent(this, LoginActivity::class.java))
    }

    private fun navigateToGame() {
        Toast.makeText(this, "Entering game...", Toast.LENGTH_SHORT).show()
        // 你们项目目前是 FetchActivity 负责下载图片/进入游戏流程
        startActivity(Intent(this, FetchActivity::class.java))
    }

    private fun navigateToLeaderboard() {
        Toast.makeText(this, "Opening leaderboard...", Toast.LENGTH_SHORT).show()
        // ✅ 关键修复：这里必须打开 LeaderboardActivity，而不是 FetchActivity
        startActivity(Intent(this, LeaderboardActivity::class.java))
    }
}
