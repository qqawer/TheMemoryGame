package com.example.memorygameandroid

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.memorygameandroid.data.network.LoginRequest
import com.example.memorygameandroid.data.network.RetrofitClient
import com.example.memorygameandroid.data.session.SessionManager
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val etU = findViewById<EditText>(R.id.etUsername)
        val etP = findViewById<EditText>(R.id.etPassword)
        val tv = findViewById<TextView>(R.id.tvStatus)
        val btn = findViewById<Button>(R.id.btnLogin)

        val api = RetrofitClient.create()
        val session = SessionManager(this)

        btn.setOnClickListener {
            val u = etU.text.toString().trim()
            val p = etP.text.toString().trim()

            lifecycleScope.launch {
                try {
                    tv.text = "Logging in..."
                    val resp = api.login(LoginRequest(u, p))

                    val token = resp.data?.token
                    if (token.isNullOrBlank()) {
                        tv.text = "Login failed: token is empty"
                        return@launch
                    }

                    session.saveToken(token)

                    tv.text = "Login OK!"
                    startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                    finish()
                } catch (e: Exception) {
                    tv.text = "Login failed: ${e.message}"
                }
            }
        }
    }
}
