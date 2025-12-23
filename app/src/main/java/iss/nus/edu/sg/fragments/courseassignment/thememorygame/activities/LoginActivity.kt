package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.PlayActivity
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.R
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.databinding.ActivityLoginBinding
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.AuthManager
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.LoginResult
import kotlinx.coroutines.launch

/**
 * Login Activity
 * First screen shown when app starts
 */
class LoginActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityLoginBinding
    private lateinit var authManager: AuthManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Use ViewBinding
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        // Initialize AuthManager
        authManager = AuthManager.getInstance(this)
        
        // Check if already logged in
        if (authManager.isLoggedIn()) {
            // Already logged in, navigate directly to game
            navigateToPlayActivity()
            return
        }
        
        // Set login button click listener
        binding.btnLogin.setOnClickListener {
            performLogin()
        }
        
        // Handle back button press using new API
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Don't allow going back unless user really wants to exit
                Toast.makeText(
                    this@LoginActivity,
                    "Please login first",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }
    
    /**
     * Perform login operation
     */
    private fun performLogin() {
        val username = binding.etUsername.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        
        // Basic validation
        if (username.isEmpty()) {
            binding.etUsername.error = getString(R.string.error_empty_username)
            binding.etUsername.requestFocus()
            return
        }
        
        if (password.isEmpty()) {
            binding.etPassword.error = getString(R.string.error_empty_password)
            binding.etPassword.requestFocus()
            return
        }
        
        // Show loading state
        setLoadingState(true)
        
        // Execute network request using coroutine
        lifecycleScope.launch {
            when (val result = authManager.login(username, password)) {
                is LoginResult.Success -> {
                    // Login successful
                    Toast.makeText(
                        this@LoginActivity,
                        "Welcome, ${result.username}!",
                        Toast.LENGTH_SHORT
                    ).show()
                    
                    // Navigate to game screen
                    navigateToPlayActivity()
                }
                
                is LoginResult.Error -> {
                    // Login failed, show error message
                    setLoadingState(false)
                    Toast.makeText(
                        this@LoginActivity,
                        result.message,
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }
    
    /**
     * Set loading state
     */
    private fun setLoadingState(isLoading: Boolean) {
        binding.apply {
            btnLogin.isEnabled = !isLoading
            etUsername.isEnabled = !isLoading
            etPassword.isEnabled = !isLoading
            progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            
            btnLogin.text = if (isLoading) {
                getString(R.string.btn_login_loading)
            } else {
                getString(R.string.btn_login)
            }
        }
    }
    
    /**
     * Navigate to game screen (PlayActivity)
     */
    private fun navigateToPlayActivity() {
        val intent = Intent(this, PlayActivity::class.java)
        
        // Pass user information to game screen
        intent.putExtra("username", authManager.getUsername())
        intent.putExtra("isPaidUser", authManager.isPaidUser())
        
        startActivity(intent)
        finish()  // Close login screen to prevent going back
    }
}
