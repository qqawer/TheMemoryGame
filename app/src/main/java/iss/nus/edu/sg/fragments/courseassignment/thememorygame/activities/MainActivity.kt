package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback  // ✅ 新增这行import
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.R
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.AuthManager
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.FetchActivity // ✅ 仅添加必要导入

/**
 * Main Activity - Menu/Game Main Screen
 *
 * Displays different UI based on login status:
 * - Not logged in: Show [Login] [Start Game*] [Leaderboard]  (* Login required)
 * - Logged in: Show [VIP/Free Badge] [Username] [Start Game] [Leaderboard] [Logout]
 *
 * File path:
 * app/src/main/java/iss/nus/edu/sg/fragments/courseassignment/thememorygame/activities/MainActivity.kt
 */
class MainActivity : AppCompatActivity() {

    private lateinit var authManager: AuthManager

    // User info area (shown only when logged in)
    private lateinit var layoutUserInfo: View
    private lateinit var tvUsername: TextView
    private lateinit var tvVipStatus: TextView

    // Buttons for not logged in state
    private lateinit var btnLoginPrompt: Button

    // Buttons for logged in state
    private lateinit var btnLogout: Button

    // Common buttons
    private lateinit var btnStartGame: Button
    private lateinit var btnLeaderboard: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize AuthManager
        authManager = AuthManager.getInstance(this)

        // Initialize views
        initViews()

        // Setup click listeners
        setupClickListeners()

        // Update UI
        updateUI()

        // ✅ 新增：处理返回按钮（替代过时的onBackPressed）
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Exit app confirmation
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Exit App")
                    .setMessage("Are you sure you want to exit?")
                    .setPositiveButton("Exit") { _, _ ->
                        finish()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        })
    }

    override fun onResume() {
        super.onResume()
        // Update UI every time returning to this screen (handles post-login return)
        updateUI()
    }

    /**
     * Initialize all views
     */
    private fun initViews() {
        // User info area
        layoutUserInfo = findViewById(R.id.layoutUserInfo)
        tvUsername = findViewById(R.id.tvUsername)
        tvVipStatus = findViewById(R.id.tvVipStatus)

        // Buttons
        btnLoginPrompt = findViewById(R.id.btnLoginPrompt)
        btnLogout = findViewById(R.id.btnLogout)
        btnStartGame = findViewById(R.id.btnStartGame)
        btnLeaderboard = findViewById(R.id.btnLeaderboard)
    }

    /**
     * Setup all button click listeners
     */
    private fun setupClickListeners() {
        // Login button (shown when not logged in)
        btnLoginPrompt.setOnClickListener {
            navigateToLogin()
        }

        // Start game button
        btnStartGame.setOnClickListener {
            handleStartGame()
        }

        // Leaderboard button (no login required)
        btnLeaderboard.setOnClickListener {
            navigateToLeaderboard()
        }

        // Logout button (shown when logged in)
        btnLogout.setOnClickListener {
            handleLogout()
        }
    }

    /**
     * Update UI based on login status
     * Core method: controls all UI elements visibility/content
     */
    private fun updateUI() {
        if (authManager.isLoggedIn()) {
            // ========== Logged In State ==========
            showLoggedInUI()
        } else {
            // ========== Logged Out State ==========
            showLoggedOutUI()
        }
    }

    /**
     * Show UI for logged in state
     */
    private fun showLoggedInUI() {
        // Show user info area
        layoutUserInfo.visibility = View.VISIBLE

        // Show username
        val username = authManager.getUsername() ?: "Guest"
        tvUsername.text = username

        // Show VIP badge
        val isPaidUser = authManager.isPaidUser()
        if (isPaidUser) {
            tvVipStatus.text = "VIP"
            tvVipStatus.setTextColor(getColor(R.color.vip_gold))  // Gold
        } else {
            tvVipStatus.text = "Free"
            tvVipStatus.setTextColor(getColor(R.color.free_gray))  // Gray
        }

        // Hide login button
        btnLoginPrompt.visibility = View.GONE

        // Show logout button
        btnLogout.visibility = View.VISIBLE

        // Start game button text
        btnStartGame.text = getString(R.string.btn_start_game)
    }

    /**
     * Show UI for logged out state
     */
    private fun showLoggedOutUI() {
        // Hide user info area
        layoutUserInfo.visibility = View.GONE

        // Show login button
        btnLoginPrompt.visibility = View.GONE

        // Hide logout button
        btnLogout.visibility = View.GONE

        // Start game button text (hints login required)
        btnStartGame.text = getString(R.string.btn_start_game)
    }

    /**
     * Handle start game click
     */
    private fun handleStartGame() {
        if (authManager.isLoggedIn()) {
            // ✅ Logged in - Navigate to game screen
            navigateToGame()
        } else {
            // ❌ Not logged in - Show login required dialog
            showLoginRequiredDialog()
        }
    }

    /**
     * Show login required dialog
     */
    private fun showLoginRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle("Login Required")
            .setMessage("You need to login before starting the game. Login now?")
            .setPositiveButton("Login") { _, _ ->
                navigateToLogin()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Handle logout
     */
    private fun handleLogout() {
        AlertDialog.Builder(this)
            .setTitle("Confirm Logout")
            .setMessage("Are you sure you want to logout?")
            .setPositiveButton("Confirm") { _, _ ->
                // Clear login state
                authManager.logout()

                // Update UI
                updateUI()

                Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Navigate to login screen
     */
    private fun navigateToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        startActivity(intent)
        // Don't call finish(), allow user to return
    }

    /**
     * Navigate to game screen
     */
    private fun navigateToGame() {
        // TODO: Replace with actual Activity when Member 4 implements game screen
        Toast.makeText(this, "Entering game...", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, FetchActivity::class.java)
        startActivity(intent)
    }

    /**
     * Navigate to leaderboard screen
     */
    private fun navigateToLeaderboard() {
        // TODO: Replace with actual Activity when Member 5 implements leaderboard
        Toast.makeText(this, "Opening leaderboard...", Toast.LENGTH_SHORT).show()
        val intent = Intent(this, FetchActivity::class.java)
        startActivity(intent)
    }

    // ❌ 删除了过时的onBackPressed()方法
    // 已在onCreate()中使用OnBackPressedCallback替代
}
