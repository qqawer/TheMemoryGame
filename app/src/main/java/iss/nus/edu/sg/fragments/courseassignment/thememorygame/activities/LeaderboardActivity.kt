package iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities

import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.R
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.leaderboard.LeaderboardAdapter
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.leaderboard.LeaderboardRow
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.ApiResponse
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.ApiService
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class LeaderboardActivity : AppCompatActivity() {

    private lateinit var rv: RecyclerView
    private lateinit var pb: ProgressBar
    private lateinit var tvStatus: TextView
    private val adapter = LeaderboardAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_leaderboard)

        rv = findViewById(R.id.rvLeaderboard)
        pb = findViewById(R.id.pbLoading)
        tvStatus = findViewById(R.id.tvStatus)

        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { goBack() }
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = goBack()
        })

        loadLeaderboard()
    }

    private fun goBack() {
        finish()
    }

    private fun loadLeaderboard() {
        pb.visibility = View.VISIBLE
        tvStatus.visibility = View.GONE
        rv.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val token = getSharedPreferences("MemoryGamePrefs", MODE_PRIVATE)
                    .getString("auth_token", null)

                val api = ApiService()
                val resp = api.get("/Score/leaderboard?page=1&size=50", token = token)

                val list = when (resp) {
                    is ApiResponse.Success -> parseLeaderboard(resp.data)
                    else -> emptyList()
                }

                pb.visibility = View.GONE

                if (list.isEmpty()) {
                    tvStatus.text = "No scores yet."
                    tvStatus.visibility = View.VISIBLE
                    return@launch
                }

                // 越小越好（更快）
                adapter.submit(list.sortedBy { it.completionTimeSeconds })
                rv.visibility = View.VISIBLE

            } catch (e: Exception) {
                pb.visibility = View.GONE
                tvStatus.text = "Load failed: ${e.message}"
                tvStatus.visibility = View.VISIBLE
            }
        }
    }

    /**
     * 兼容后端多种返回：
     * - { data: [ {username, completionTimeSeconds} ] }
     * - { data: { items:[...] } }
     */
    private fun parseLeaderboard(root: JSONObject): List<LeaderboardRow> {
        val data = root.opt("data") ?: return emptyList()

        val arr: JSONArray? = when (data) {
            is JSONArray -> data
            is JSONObject -> data.optJSONArray("items")
                ?: data.optJSONArray("leaderboard")
                ?: data.optJSONArray("data")
            else -> null
        }

        if (arr == null || arr.length() == 0) return emptyList()

        val out = ArrayList<LeaderboardRow>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("username", o.optString("userName", "unknown"))
            val sec = o.optInt(
                "completionTimeSeconds",
                o.optInt("completeTimeSeconds", o.optInt("timeSeconds", 0))
            )
            out.add(LeaderboardRow(username = name, completionTimeSeconds = sec))
        }
        return out
    }
}
