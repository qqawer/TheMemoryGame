package iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.ads

import android.app.Activity
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AdsLoader {

    private const val TAG = "AdsLoader"
    private const val BASE_URL = "http://10.0.2.2:5011" // emulator
    private const val ENDPOINT = "/api/Ad/active"

    /**
     * Safe: if views are missing in activity_play.xml, this does nothing and won't crash.
     * Requires your layout to optionally include:
     *  - adContainer (View)
     *  - tvAdTitle (TextView)
     *  - ivAd (ImageView)
     *  - tvAdStatus (TextView) [optional]
     *
     * Paid users -> hide ad container.
     */
    fun tryLoadAndShow(activity: Activity, root: View) {
        val containerId = activity.resources.getIdentifier("adContainer", "id", activity.packageName)
        val titleId = activity.resources.getIdentifier("tvAdTitle", "id", activity.packageName)
        val imageId = activity.resources.getIdentifier("ivAd", "id", activity.packageName)
        val statusId = activity.resources.getIdentifier("tvAdStatus", "id", activity.packageName)

        if (containerId == 0 || titleId == 0 || imageId == 0) {
            // Layout doesn't have ad UI -> ignore
            Log.d(TAG, "Ad views not found in layout. Skip.")
            return
        }

        val adContainer = root.findViewById<View>(containerId)
        val tvTitle = root.findViewById<TextView>(titleId)
        val ivAd = root.findViewById<ImageView>(imageId)
        val tvStatus = if (statusId != 0) root.findViewById<TextView>(statusId) else null

        // Read login info from SharedPreferences (matches your AuthManager)
        val prefs = activity.getSharedPreferences("MemoryGamePrefs", Activity.MODE_PRIVATE)
        val token = prefs.getString("auth_token", null)
        val isPaid = prefs.getBoolean("is_paid_user", false)

        if (isPaid) {
            adContainer.visibility = View.GONE
            return
        } else {
            adContainer.visibility = View.VISIBLE
        }

        // load in background without lifecycleScope dependency
        CoroutineScope(Dispatchers.Main).launch {
            tvStatus?.text = ""
            val result = withContext(Dispatchers.IO) { fetchFirstAd(token) }

            if (result == null) {
                tvTitle.text = "Advertisement"
                ivAd.setImageDrawable(null)
                tvStatus?.text = "Ad: empty response"
                return@launch
            }

            tvTitle.text = result.title.ifBlank { "Advertisement" }

            if (result.imageUrl.isNotBlank()) {
                val finalUrl = toFullUrl(result.imageUrl)
                Glide.with(activity).load(finalUrl).into(ivAd)
            } else {
                ivAd.setImageDrawable(null)
            }
        }
    }

    private data class Ad(val title: String, val imageUrl: String)

    private fun toFullUrl(pathOrUrl: String): String {
        val s = pathOrUrl.trim()
        if (s.startsWith("http://") || s.startsWith("https://")) return s
        val p = if (s.startsWith("/")) s else "/$s"
        return BASE_URL + p
    }

    /**
     * Calls GET /api/Ad/active
     * Response format sample:
     * {"code":200,"message":"...","data":[{"id":1,"adTitle":"dog1","adImageUrl":"http://...","isActive":true}, ...]}
     */
    private fun fetchFirstAd(token: String?): Ad? {
        var conn: HttpURLConnection? = null
        return try {
            val url = URL(BASE_URL + ENDPOINT)
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10000
                readTimeout = 10000
                setRequestProperty("Accept", "application/json")
                token?.takeIf { it.isNotBlank() }?.let {
                    setRequestProperty("Authorization", "Bearer $it")
                }
            }

            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream.bufferedReader().use { it.readText() }

            if (code !in 200..299) {
                Log.e(TAG, "Ad fetch failed: HTTP $code, body=$body")
                return null
            }

            val json = JSONObject(body)
            val data = json.opt("data") ?: return null

            when (data) {
                is JSONArray -> {
                    if (data.length() == 0) return null
                    val obj = data.optJSONObject(0) ?: return null
                    Ad(
                        title = obj.optString("adTitle", ""),
                        imageUrl = obj.optString("adImageUrl", obj.optString("adImageUrlUrl", ""))
                    )
                }
                is JSONObject -> {
                    Ad(
                        title = data.optString("adTitle", ""),
                        imageUrl = data.optString("adImageUrl", data.optString("adImageUrlUrl", ""))
                    )
                }
                else -> null
            }
        } catch (e: Exception) {
            Log.e(TAG, "fetchFirstAd exception: ${e.message}", e)
            null
        } finally {
            conn?.disconnect()
        }
    }
}
