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
     * Ad entity: for both carousel/single image modes.
     * imageUrl will eventually be converted to a full URL that can be loaded directly (toFullUrl).
     */
    data class Ad(val title: String, val imageUrl: String)

    /**
     * Recommended for carousel: fetch active ads list (callback on the main thread).
     */
    fun fetchActiveAds(activity: Activity, onResult: (List<Ad>) -> Unit) {
        val prefs = activity.getSharedPreferences("MemoryGamePrefs", Activity.MODE_PRIVATE)
        val token = prefs.getString("auth_token", null)

        CoroutineScope(Dispatchers.Main).launch {
            val ads = withContext(Dispatchers.IO) { fetchAds(token) }
            onResult(ads)
        }
    }

    /**
     * For backward compatibility: if ivAd exists in the layout, load the first ad image.
     * If you have changed to ViewPager2(vpAds), it will not detect ivAd and will skip automatically (without crashing).
     *
     * Paid users -> hide ad container.
     */
    fun tryLoadAndShow(activity: Activity, root: View) {
        val containerId = activity.resources.getIdentifier("adContainer", "id", activity.packageName)
        if (containerId == 0) {
            Log.d(TAG, "adContainer not found. Skip.")
            return
        }
        val adContainer = root.findViewById<View>(containerId)

        val prefs = activity.getSharedPreferences("MemoryGamePrefs", Activity.MODE_PRIVATE)
        val token = prefs.getString("auth_token", null)
        val isPaid = prefs.getBoolean("is_paid_user", false)

        if (isPaid) {
            adContainer.visibility = View.GONE
            return
        } else {
            adContainer.visibility = View.VISIBLE
        }

        // Old layout: ivAd
        val imageId = activity.resources.getIdentifier("ivAd", "id", activity.packageName)
        if (imageId == 0) {
            Log.d(TAG, "ivAd not found in layout. (Maybe using ViewPager2) Skip single-image load.")
            return
        }

        val ivAd = root.findViewById<ImageView>(imageId)

        // title/status optional
        val titleId = activity.resources.getIdentifier("tvAdTitle", "id", activity.packageName)
        val statusId = activity.resources.getIdentifier("tvAdStatus", "id", activity.packageName)
        val tvTitle = if (titleId != 0) root.findViewById<TextView>(titleId) else null
        val tvStatus = if (statusId != 0) root.findViewById<TextView>(statusId) else null

        CoroutineScope(Dispatchers.Main).launch {
            tvStatus?.text = ""
            val ads = withContext(Dispatchers.IO) { fetchAds(token) }

            if (ads.isEmpty()) {
                tvTitle?.text = "Advertisement"
                ivAd.setImageDrawable(null)
                tvStatus?.text = "Ad: empty response"
                return@launch
            }

            val first = ads.first()
            tvTitle?.text = first.title.ifBlank { "Advertisement" }

            if (first.imageUrl.isNotBlank()) {
                Log.d(TAG, "Loading ad image: ${first.imageUrl}")
                Glide.with(activity).load(first.imageUrl).into(ivAd)
            } else {
                ivAd.setImageDrawable(null)
            }
        }
    }

    // ------------------------
    // Internal helpers
    // ------------------------

    private fun toFullUrl(pathOrUrl: String): String {
        val s = pathOrUrl.trim()
        val raw = if (s.startsWith("http://") || s.startsWith("https://")) {
            s
        } else {
            val p = if (s.startsWith("/")) s else "/$s"
            BASE_URL + p
        }

        // 👇 Force refresh ad image (during development)
        val sep = if (raw.contains("?")) "&" else "?"
        return raw + "${sep}v=20260105"
    }


    /**
     * Calls GET /api/Ad/active
     * Response format sample:
     * {"code":200,"message":"...","data":[{"id":1,"adTitle":"dog1","adImageUrl":"http://...","isActive":true}, ...]}
     */
    private fun fetchAds(token: String?): List<Ad> {
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
                return emptyList()
            }

            val json = JSONObject(body)
            val data = json.opt("data") ?: return emptyList()

            val list = mutableListOf<Ad>()

            when (data) {
                is JSONArray -> {
                    for (i in 0 until data.length()) {
                        val obj = data.optJSONObject(i) ?: continue
                        val title = obj.optString("adTitle", "")
                        val img = obj.optString("adImageUrl", obj.optString("adImageUrlUrl", ""))
                        if (img.isNotBlank()) {
                            list.add(
                                Ad(
                                    title = title,
                                    imageUrl = toFullUrl(img)
                                )
                            )
                        }
                    }
                }

                is JSONObject -> {
                    val title = data.optString("adTitle", "")
                    val img = data.optString("adImageUrl", data.optString("adImageUrlUrl", ""))
                    if (img.isNotBlank()) {
                        list.add(
                            Ad(
                                title = title,
                                imageUrl = toFullUrl(img)
                            )
                        )
                    }
                }

                else -> {
                    // unexpected format
                    return emptyList()
                }
            }

            list
        } catch (e: Exception) {
            Log.e(TAG, "fetchAds exception: ${e.message}", e)
            emptyList()
        } finally {
            conn?.disconnect()
        }
    }
}
