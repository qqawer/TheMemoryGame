package com.example.memorygameandroid

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.memorygameandroid.data.auth.TokenParser
import com.example.memorygameandroid.data.network.AdDto
import com.example.memorygameandroid.data.network.RetrofitClient
import com.example.memorygameandroid.data.network.ScoreSubmitRequest
import com.example.memorygameandroid.data.session.SessionManager
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private val gson = Gson()
    private val baseUrlNoSlash = "http://10.0.2.2:5011" // 模拟器用这个

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        val session = SessionManager(this)
        val token = session.getToken()

        if (token.isNullOrBlank()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        val parsed = TokenParser.parse(token)
        val bearer = "Bearer $token"
        val api = RetrofitClient.create()

        val tvUserInfo = findViewById<TextView>(R.id.tvUserInfo)
        val tvStatusMain = findViewById<TextView>(R.id.tvStatusMain)

        tvUserInfo.text = "userId=${parsed.userId} | isPaid=${parsed.isPaid}"

        // Logout
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            session.clear()
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        // Ads
        val adContainer = findViewById<View>(R.id.adContainer)
        val tvAdTitle = findViewById<TextView>(R.id.tvAdTitle)
        val ivAd = findViewById<ImageView>(R.id.ivAd)

        if (parsed.isPaid) {
            adContainer.visibility = View.GONE
        } else {
            adContainer.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    tvStatusMain.text = ""
                    val resp = api.getActiveAd(bearer)
                    val ad = extractSingleAd(resp.data)

                    if (ad == null) {
                        tvAdTitle.text = "Advertisement"
                        ivAd.setImageDrawable(null)
                        tvStatusMain.text = "Ad: empty response"
                        return@launch
                    }

                    tvAdTitle.text = ad.adTitle?.takeIf { it.isNotBlank() } ?: "Advertisement"

                    val img = ad.adImageUrl?.trim().orEmpty()
                    if (img.isNotBlank()) {
                        val url = fullUrl(img)
                        Glide.with(this@MainActivity).load(url).into(ivAd)
                    } else {
                        ivAd.setImageDrawable(null)
                    }

                } catch (e: Exception) {
                    tvStatusMain.text = "Ad load failed: ${e.message}"
                    tvAdTitle.text = "Advertisement"
                    ivAd.setImageDrawable(null)
                }
            }
        }

        // Submit demo score
        findViewById<Button>(R.id.btnSubmitDemoScore).setOnClickListener {
            lifecycleScope.launch {
                try {
                    val seconds = Random.nextInt(30, 201)
                    val resp = api.submitScore(bearer, ScoreSubmitRequest(seconds))
                    Toast.makeText(
                        this@MainActivity,
                        "Submit OK: ${resp.message} (${seconds}s)",
                        Toast.LENGTH_SHORT
                    ).show()
                } catch (e: Exception) {
                    Toast.makeText(
                        this@MainActivity,
                        "Submit failed: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        // Open leaderboard
        findViewById<Button>(R.id.btnOpenLeaderboard).setOnClickListener {
            startActivity(Intent(this, LeaderboardActivity::class.java))
        }
    }

    // ✅ 支持后端返回：data = {..} 或 data = [ {..} ]
    private fun extractSingleAd(data: JsonElement?): AdDto? {
        if (data == null || data.isJsonNull) return null

        return try {
            when {
                data.isJsonObject -> gson.fromJson(data, AdDto::class.java)
                data.isJsonArray -> {
                    val listType = object : TypeToken<List<AdDto>>() {}.type
                    val list: List<AdDto> = gson.fromJson(data, listType)
                    list.firstOrNull()
                }
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun fullUrl(pathOrUrl: String): String {
        if (pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://")) return pathOrUrl
        val p = if (pathOrUrl.startsWith("/")) pathOrUrl else "/$pathOrUrl"
        return baseUrlNoSlash + p
    }
}
