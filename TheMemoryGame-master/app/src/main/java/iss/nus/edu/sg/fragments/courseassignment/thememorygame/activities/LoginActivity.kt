package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.R
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.databinding.ActivityLoginBinding
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.AuthManager
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.LoginResult
import kotlinx.coroutines.launch

/**
 * Login Activity - English Version
 * 
 * Modifications:
 * 1. ✅ Added back button functionality
 * 2. ✅ Click back button to return to MainActivity
 * 3. ✅ After successful login, return to MainActivity (MainActivity will auto-refresh UI)
 * 
 * File path:
 * app/src/main/java/iss/nus/edu/sg/fragments/courseassignment/thememorygame/activities/LoginActivity.kt
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
        
        // ✅ New: Setup back button click listener
        binding.btnBack.setOnClickListener {
            handleBackPressed()
        }
        
        // Set login button click listener
        binding.btnLogin.setOnClickListener {
            performLogin()
        }
        
        // ✅ Modified: Allow user to return to main screen
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPressed()
            }
        })
    }
    
    /**
     * ✅ New: Handle back button press
     */
    private fun handleBackPressed() {
        // Return to main screen directly
        finish()
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
                    // ✅ Login successful
                    handleLoginSuccess(result)
                }
                
                is LoginResult.Error -> {
                    // ❌ Login failed
                    handleLoginError(result.message)
                }
            }
        }
    }
    
    /**
     * ✅ New: Handle login success
     */
    private fun handleLoginSuccess(result: LoginResult.Success) {
        // Show welcome message
        val userType = if (result.isPaidUser) "VIP User" else "Regular User"
        Toast.makeText(
            this,
            "Welcome back, ${result.username}! ($userType)",
            Toast.LENGTH_SHORT
        ).show()
        
        // Return to main screen
        // MainActivity will detect logged-in state in onResume() and auto-update UI
        finish()
    }
    
    /**
     * ✅ New: Handle login error
     */
    private fun handleLoginError(message: String) {
        setLoadingState(false)
        
        Toast.makeText(
            this,
            "Login failed: $message",
            Toast.LENGTH_LONG
        ).show()
        
        // Clear password field
        binding.etPassword.text?.clear()
        binding.etPassword.requestFocus()
    }
    
    /**
     * Set loading state
     */
    private fun setLoadingState(isLoading: Boolean) {
        binding.apply {
            // ✅ Modified: Disable back button while loading
            btnBack.isEnabled = !isLoading
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
}
