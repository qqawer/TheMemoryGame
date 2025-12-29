package iss.nus.edu.sg.fragments.courseassignment.thememorygame

import android.content.Intent
import android.os.Bundle
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
    private lateinit var btnBack: Button
    private lateinit var btnConfirmSelection: Button

    private lateinit var imageAdapter: ImageAdapter
    private var downloadJob: Job? = null
    private val db by lazy { AppDatabase.getDatabase(this) }

    companion object {
        private const val MAX_SELECTION = 6
        private const val MAX_IMAGES = 20
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
                        Toast.makeText(this@FetchActivity, "No images found", Toast.LENGTH_SHORT).show()
                    }
                    return@launch
                }

                imageUrls.take(MAX_IMAGES).forEachIndexed { index, imgUrl ->
                    val destFile = File(imageDir, "img_${index}.jpg")
                    val success = withContext(Dispatchers.IO) {
                        downloadToFileWithUserAgent(imgUrl, destFile)
                    }

                    if (success) {
                        withContext(Dispatchers.Main) {
                            imageAdapter.addImage(ImageItem(destFile.absolutePath))
                            updateProgress(index + 1, MAX_IMAGES)
                        }
                    }
                }
            } catch (e: Exception) {
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
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                connection.inputStream.use { input ->
                    file.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            } else false
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private suspend fun extractImageUrls(url: String): List<String> {
        return withContext(Dispatchers.IO) {
            try {
                val document = Jsoup.connect(url).userAgent("Mozilla").timeout(10000).get()
                document.select("img[src]")
                    .mapNotNull { it.absUrl("src") }
                    .filter { it.startsWith("http") }
                    .distinct()
            } catch (e: Exception) { emptyList() }
        }
    }

    private fun updateProgress(current: Int, total: Int) {
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
