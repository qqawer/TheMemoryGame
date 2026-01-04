package iss.nus.edu.sg.fragments.courseassignment.thememorygame

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.db.AppDatabase
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.model.FetchHistory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class FetchActivity : AppCompatActivity() {

    private lateinit var etUrl: EditText
    private lateinit var btnFetch: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvProgress: TextView
    private lateinit var rvImages: RecyclerView
    private lateinit var btnContinue: Button
    private lateinit var btnBack: ImageButton
    private lateinit var btnConfirmSelection: Button

    private lateinit var imageAdapter: ImageAdapter
    private var downloadJob: Job? = null
    private val db by lazy { AppDatabase.getDatabase(this) }

    companion object {
        private const val TAG = "FetchActivity"
        private const val MAX_SELECTION = 6
        private const val MAX_IMAGES = 20

        // 更像真实浏览器的 UA（很多站会挡 “Mozilla” 这种过短 UA）
        private const val UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fetch)
        initViews()
        setupRecyclerView()
        setupListeners()
    }

    private fun initViews() {
        etUrl = findViewById(R.id.etUrl)
        btnFetch = findViewById(R.id.btnFetch)
        progressBar = findViewById(R.id.progressBar)
        tvProgress = findViewById(R.id.tvProgress)
        rvImages = findViewById(R.id.rvImages)
        btnContinue = findViewById(R.id.btnContinue)
        btnBack = findViewById(R.id.btnBack)
        btnConfirmSelection = findViewById(R.id.btnConfirmSelection)
    }

    private fun setupRecyclerView() {
        imageAdapter = ImageAdapter { position ->
            handleImageClick(position)
        }
        rvImages.apply {
            adapter = imageAdapter
            layoutManager = GridLayoutManager(this@FetchActivity, 4)
        }
    }

    private fun setupListeners() {
        btnFetch.setOnClickListener {
            val url = etUrl.text.toString().trim()
            if (url.isNotEmpty()) {
                checkHistoryAndStart(url)
            } else {
                Toast.makeText(this, "Please enter a URL", Toast.LENGTH_SHORT).show()
            }
        }

        btnBack.setOnClickListener { finish() }

        btnConfirmSelection.setOnClickListener {
            saveCurrentToHistory()
            navigateToPlayActivity()
        }

        btnContinue.setOnClickListener {
            saveCurrentToHistory()
            navigateToPlayActivity()
        }
    }

    private fun checkHistoryAndStart(url: String) {
        lifecycleScope.launch {
            val history = withContext(Dispatchers.IO) {
                db.historyDao().getHistoryByUrl(url)
            }
            if (history != null) {
                loadFromHistory(history)
            } else {
                startFetching(url)
            }
        }
    }

    private fun loadFromHistory(history: FetchHistory) {
        imageAdapter.clearImages()
        val allPaths = history.allImagePaths.split(",").filter { it.isNotEmpty() }
        val selectedPaths = history.selectedImagePaths.split(",").toSet()

        allPaths.forEach { path ->
            val item = ImageItem(path)
            if (selectedPaths.contains(path)) {
                item.isSelected = true
            }
            imageAdapter.addImage(item)
        }
        updateContinueButton()
        Toast.makeText(this, "Loaded from history", Toast.LENGTH_SHORT).show()
    }

    private fun saveCurrentToHistory() {
        val url = etUrl.text.toString().trim()
        val allPaths = imageAdapter.getAllImagePaths().joinToString(",")
        val selectedPaths = imageAdapter.getSelectedImages().joinToString(",")

        lifecycleScope.launch(Dispatchers.IO) {
            db.historyDao().insertHistory(FetchHistory(url, allPaths, selectedPaths))
        }
    }

    private fun startFetching(url: String) {
        downloadJob?.cancel()
        imageAdapter.clearImages()
        updateContinueButton()

        val imageDir = File(getExternalFilesDir(null), "images/${System.currentTimeMillis()}")
        imageDir.mkdirs()

        progressBar.visibility = View.VISIBLE
        tvProgress.visibility = View.VISIBLE
        btnFetch.isEnabled = false

        downloadJob = lifecycleScope.launch {
            try {
                val imageUrls = extractImageUrls(url)
                if (imageUrls.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            this@FetchActivity,
                            "No images found.\nTry a page with real images (e.g. Wikipedia/Commons) or a different URL.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                val targets = imageUrls.take(MAX_IMAGES)
                val total = targets.size

                targets.forEachIndexed { index, imgUrl ->
                    val destFile = File(imageDir, "img_${index}.jpg")
                    val success = withContext(Dispatchers.IO) {
                        downloadToFileWithUserAgent(imgUrl, destFile)
                    }

                    if (success) {
                        withContext(Dispatchers.Main) {
                            imageAdapter.addImage(ImageItem(destFile.absolutePath))
                            updateProgress(index + 1, total)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fetch error: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FetchActivity, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            } finally {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    tvProgress.visibility = View.GONE
                    btnFetch.isEnabled = true
                }
            }
        }
    }

    private fun downloadToFileWithUserAgent(url: String, file: File): Boolean {
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", UA)
            connection.setRequestProperty("Accept", "image/*,*/*;q=0.8")
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 8000
            connection.readTimeout = 8000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else {
                Log.w(TAG, "Download failed ${connection.responseCode} for $url")
                false
            }
        } catch (e: Exception) {
            Log.w(TAG, "Download exception: ${e.message} url=$url", e)
            false
        }
    }

    /**
     * ✅ 更强的图片提取：
     * - img[src]
     * - img[data-src] / data-original / data-lazy-src
     * - img[srcset] / source[srcset]（取 srcset 里的第一张）
     */
    private suspend fun extractImageUrls(url: String): List<String> = withContext(Dispatchers.IO) {
        try {
            val doc: Document = Jsoup.connect(url)
                .userAgent(UA)
                .referrer("https://www.google.com/")
                .timeout(15000)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .get()

            val out = LinkedHashSet<String>()

            // 1) 常规 img[src]
            doc.select("img[src]").forEach { el ->
                val abs = el.absUrl("src").trim()
                if (abs.startsWith("http")) out.add(abs)
            }

            // 2) 懒加载 data-src / data-original / data-lazy-src
            listOf("data-src", "data-original", "data-lazy-src").forEach { attr ->
                doc.select("img[$attr]").forEach { el ->
                    val abs = el.absUrl(attr).trim()
                    if (abs.startsWith("http")) out.add(abs)
                }
            }

            // 3) srcset（img 或 source）
            fun pickFirstFromSrcset(srcset: String): String? {
                // srcset 格式：url1 1x, url2 2x 或 url1 300w, url2 600w
                val first = srcset.split(",")
                    .map { it.trim() }
                    .firstOrNull { it.isNotEmpty() }
                    ?: return null
                val urlPart = first.split(" ")
                    .firstOrNull { it.isNotEmpty() }
                    ?: return null
                return urlPart
            }

            doc.select("img[srcset]").forEach { el ->
                val srcset = el.attr("srcset").trim()
                val candidate = pickFirstFromSrcset(srcset) ?: return@forEach
                val abs = el.absUrl("srcset").trim() // 有些站 absUrl("srcset") 不靠谱，所以下面补一手
                if (abs.startsWith("http")) out.add(abs) else if (candidate.startsWith("http")) out.add(candidate)
            }

            doc.select("source[srcset]").forEach { el ->
                val candidate = pickFirstFromSrcset(el.attr("srcset").trim()) ?: return@forEach
                val abs = try {
                    // source 没有 absUrl 对 srcset 的好支持，手动处理相对路径
                    URL(URL(url), candidate).toString()
                } catch (_: Exception) {
                    candidate
                }
                if (abs.startsWith("http")) out.add(abs)
            }

            Log.d(TAG, "extractImageUrls found=${out.size} url=$url")
            out.toList()
        } catch (e: Exception) {
            Log.e(TAG, "extractImageUrls failed: ${e.message} url=$url", e)
            emptyList()
        }
    }

    private fun updateProgress(current: Int, total: Int) {
        if (total <= 0) return
        progressBar.progress = (current * 100) / total
        tvProgress.text = "Downloading $current of $total images..."
    }

    private fun handleImageClick(position: Int) {
        imageAdapter.toggleSelection(position)
        if (imageAdapter.getSelectedCount() > MAX_SELECTION) {
            imageAdapter.toggleSelection(position)
            Toast.makeText(this, "Select only $MAX_SELECTION images", Toast.LENGTH_SHORT).show()
        }
        updateContinueButton()
    }

    private fun updateContinueButton() {
        val count = imageAdapter.getSelectedCount()
        val isReady = (count == MAX_SELECTION)

        btnConfirmSelection.isEnabled = isReady
        btnConfirmSelection.text = "Confirm ($count/$MAX_SELECTION)"

        btnContinue.isEnabled = isReady
        btnContinue.text = "Continue ($count/$MAX_SELECTION selected)"
    }

    private fun navigateToPlayActivity() {
        val intent = Intent(this, PlayActivity::class.java)
        // ✅ 修正 Key 为 "image_urls"，并使用 ArrayList<String> 以匹配 PlayActivity 的接收逻辑
        val selectedImages = ArrayList(imageAdapter.getSelectedImages())
        intent.putStringArrayListExtra("image_urls", selectedImages)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        downloadJob?.cancel()
    }
}
