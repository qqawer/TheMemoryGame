package iss.nus.edu.sg.fragments.courseassignment.thememorygame

import android.content.Intent
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities.GameOverActivity
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.activities.LeaderboardActivity
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.databinding.ActivityPlayBinding
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.databinding.IncludeHudGameBinding
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.ads.AdPagerAdapter
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.ads.AdsLoader
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.network.AuthManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayBinding
    private lateinit var hud: IncludeHudGameBinding

    private lateinit var memoryCards: MutableList<MemoryCard>
    private lateinit var adapter: MemoryCardAdapter

    // ===== Sound =====
    private lateinit var soundPool: SoundPool
    private var sFlip = 0
    private var sMatch = 0
    private var sWin = 0
    private var soundsReady = false

    // ===== Ads Carousel =====
    private lateinit var vpAds: ViewPager2
    private val adHandler = Handler(Looper.getMainLooper())
    private var adRunnable: Runnable? = null
    private val adIntervalMs = 3000L

    // ===== Game State =====
    private var indexOfSingleSelectedCard: Int? = null
    private var isChecking = false

    private var timerJob: Job? = null
    private var timerSeconds = 0
    private var isTimerStarted = false

    // ✅ 精确计时：记录开始时刻（毫秒）
    private var startTimeMs = 0L

    private var matches = 0
    private val totalPairs = 6
    private var imageUrls: ArrayList<String>? = null

    // ===== Bottom spacing for last row =====
    private var bottomDecoration: BottomSpaceDecoration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // ===== SoundPool =====
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(4)
            .setAudioAttributes(attrs)
            .build()

        // ✅ 按你 raw 文件名
        sFlip = soundPool.load(this, R.raw.flip_card, 1)
        // ⚠️ 你这里 match / win 的文件名看起来写反了，但不影响计时/提交
        sMatch = soundPool.load(this, R.raw.win, 1)
        sWin = soundPool.load(this, R.raw.match_success, 1)

        soundPool.setOnLoadCompleteListener { _, _, _ ->
            soundsReady = true
        }

        // ===== HUD =====
        hud = binding.hudGame
        hud.tvTitle.text = "The Memory Game"
        hud.btnBack.setOnClickListener { finish() }
        hud.btnRestart.setOnClickListener { restartGame() }

        // ===== Game Images =====
        imageUrls = intent.getStringArrayListExtra("image_urls")
        if (imageUrls == null || imageUrls!!.size < totalPairs) {
            Toast.makeText(
                this,
                "Couldn't fetch enough images. Using default set.",
                Toast.LENGTH_LONG
            ).show()

            imageUrls = arrayListOf(
                "https://images.pexels.com/photos/3536216/pexels-photo-3536216.jpeg",
                "https://img.shetu66.com/2023/07/14/1689317564415678.png",
                "https://picx.zhimg.com/v2-ab23d513fab6abd0a27cda9ba9676383_720w.jpg",
                "https://img.shetu66.com/2023/03/10/1678415374269462.jpg",
                "https://th.bing.com/th/id/R.b8e84a0907bf9b5128dfa48be0ae48af",
                "https://ss2.bdstatic.com/70cFvXSh_Q1YnxGkpoWK1HF6hhy/it/u=1659552792"
            )
        }

        setupGame()

        adapter = MemoryCardAdapter(memoryCards) { position ->
            onCardClicked(position)
        }

        binding.rvCards.layoutManager = GridLayoutManager(this, 3)
        binding.rvCards.adapter = adapter

        // 避免 RV 默认动画干扰翻牌
        (binding.rvCards.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)
            ?.supportsChangeAnimations = false
        binding.rvCards.itemAnimator?.changeDuration = 0

        // ===== Ads Carousel (NETWORK) =====
        vpAds = binding.vpAds

        val prefs = getSharedPreferences("MemoryGamePrefs", MODE_PRIVATE)
        val isPaid = prefs.getBoolean("is_paid_user", false)

        if (isPaid) {
            binding.adContainer.visibility = View.GONE
            applyBottomSpaceForAd(showAd = false)
        } else {
            binding.adContainer.visibility = View.VISIBLE

            AdsLoader.fetchActiveAds(this) { ads ->
                val urls = ads.map { it.imageUrl }.distinct()

                if (urls.isEmpty()) {
                    binding.adContainer.visibility = View.GONE
                    applyBottomSpaceForAd(showAd = false)
                    stopAdAutoScroll()
                    return@fetchActiveAds
                }

                vpAds.adapter = AdPagerAdapter(urls)
                vpAds.offscreenPageLimit = 1

                binding.adContainer.post { applyBottomSpaceForAd(showAd = true) }

                if (urls.size > 1) {
                    startAdAutoScroll()
                }
            }
        }
    }

    private fun applyBottomSpaceForAd(showAd: Boolean) {
        binding.root.post {
            val density = resources.displayMetrics.density
            val extraGapPx = (32f * density).toInt()

            if (!showAd || binding.adContainer.visibility != View.VISIBLE) {
                bottomDecoration?.let { binding.rvCards.removeItemDecoration(it) }
                bottomDecoration = BottomSpaceDecoration((8f * density).toInt())
                binding.rvCards.addItemDecoration(bottomDecoration!!)
                binding.rvCards.invalidateItemDecorations()
                return@post
            }

            val rvLoc = IntArray(2)
            val adLoc = IntArray(2)
            binding.rvCards.getLocationOnScreen(rvLoc)
            binding.adContainer.getLocationOnScreen(adLoc)

            val rvBottom = rvLoc[1] + binding.rvCards.height
            val adTop = adLoc[1]

            val overlap = (rvBottom - adTop).coerceAtLeast(0)
            val spacePx = overlap + extraGapPx

            bottomDecoration?.let { binding.rvCards.removeItemDecoration(it) }
            bottomDecoration = BottomSpaceDecoration(spacePx)
            binding.rvCards.addItemDecoration(bottomDecoration!!)
            binding.rvCards.invalidateItemDecorations()
        }
    }

    private fun playSfx(soundId: Int, volume: Float = 1.0f) {
        if (!soundsReady || soundId == 0) return
        soundPool.play(soundId, volume, volume, 1, 0, 1.0f)
    }

    // ===== Ads Auto Scroll =====
    override fun onResume() {
        super.onResume()
        if ((vpAds.adapter?.itemCount ?: 0) > 1 && binding.adContainer.visibility == View.VISIBLE) {
            startAdAutoScroll()
        }
    }

    override fun onPause() {
        super.onPause()
        stopAdAutoScroll()
    }

    private fun startAdAutoScroll() {
        stopAdAutoScroll()
        adRunnable = object : Runnable {
            override fun run() {
                val count = vpAds.adapter?.itemCount ?: return
                if (count <= 1) return

                vpAds.currentItem = (vpAds.currentItem + 1) % count
                adHandler.postDelayed(this, adIntervalMs)
            }
        }
        adHandler.postDelayed(adRunnable!!, adIntervalMs)
    }

    private fun stopAdAutoScroll() {
        adRunnable?.let { adHandler.removeCallbacks(it) }
        adRunnable = null
    }

    // ===== Game Logic =====
    private fun restartGame() {
        timerJob?.cancel()
        isTimerStarted = false
        matches = 0
        indexOfSingleSelectedCard = null
        isChecking = false

        // ✅ reset 计时
        timerSeconds = 0
        startTimeMs = 0L

        hud.tvTimer.text = "00:00:00"
        hud.tvMatches.text = "Matches: 0 / $totalPairs"

        setupGame()
        adapter.notifyDataSetChanged()
    }

    /**
     * ✅ 精确计时：
     * 用 SystemClock.elapsedRealtime() 计算秒数
     * 显示/传递/提交都用同一个 timerSeconds，不会出现 “15/16” 跳秒不一致
     */
    private fun startTimer() {
        isTimerStarted = true
        startTimeMs = SystemClock.elapsedRealtime()

        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(200) // 更新更平滑，但秒数来自系统时钟，精确
                val elapsedSec = ((SystemClock.elapsedRealtime() - startTimeMs) / 1000).toInt()
                timerSeconds = elapsedSec

                val h = elapsedSec / 3600
                val m = (elapsedSec % 3600) / 60
                val s = elapsedSec % 60
                hud.tvTimer.text = String.format("%02d:%02d:%02d", h, m, s)
            }
        }
    }

    private fun setupGame() {
        hud.tvMatches.text = "Matches: 0 / $totalPairs"

        val urls = imageUrls ?: return
        var id = 0
        val newCards = (urls + urls).map { MemoryCard(id++, it) }.shuffled()

        if (::memoryCards.isInitialized) {
            // ✅ 关键：不换引用，原地更新
            memoryCards.clear()
            memoryCards.addAll(newCards)
        } else {
            memoryCards = newCards.toMutableList()
        }
    }


    private fun onCardClicked(position: Int) {
        if (isChecking) return
        if (!isTimerStarted) startTimer()

        val card = memoryCards[position]
        if (card.isFaceUp || card.isMatched) return

        card.isFaceUp = true
        adapter.notifyItemChanged(position, "flip")
        playSfx(sFlip, 0.85f)

        if (indexOfSingleSelectedCard == null) {
            indexOfSingleSelectedCard = position
        } else {
            isChecking = true
            checkForMatch(indexOfSingleSelectedCard!!, position)
            indexOfSingleSelectedCard = null
        }
    }

    private fun checkForMatch(p1: Int, p2: Int) {
        if (memoryCards[p1].contentSource == memoryCards[p2].contentSource) {
            memoryCards[p1].isMatched = true
            memoryCards[p2].isMatched = true

            adapter.notifyItemChanged(p1, "match")
            adapter.notifyItemChanged(p2, "match")
            playSfx(sMatch, 1.0f)

            matches++
            hud.tvMatches.text = "Matches: $matches / $totalPairs"

            isChecking = false
            if (matches == totalPairs) finishGame()
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                delay(800)
                memoryCards[p1].isFaceUp = false
                memoryCards[p2].isFaceUp = false

                adapter.notifyItemChanged(p1, "flip_back")
                adapter.notifyItemChanged(p2, "flip_back")

                isChecking = false
            }
        }
    }

    private fun finishGame() {
        playSfx(sWin, 1.0f)
        timerJob?.cancel()

        val username = AuthManager.getInstance(this).getUsername() ?: "You"

        // ✅ 最终成绩：再用系统时钟兜底算一次，防止 200ms delay 刚好没刷新到最后一秒
        val finalSeconds = if (isTimerStarted && startTimeMs > 0L) {
            ((SystemClock.elapsedRealtime() - startTimeMs) / 1000).toInt()
        } else {
            timerSeconds
        }.coerceAtLeast(0)

        timerSeconds = finalSeconds

        val intent = Intent(this, GameOverActivity::class.java).apply {
            putStringArrayListExtra("image_urls", imageUrls)

            // ✅ 关键：把本局成绩和用户名传给 GameOverActivity
            putExtra(LeaderboardActivity.EXTRA_LATEST_SCORE_SECONDS, finalSeconds)
            putExtra(LeaderboardActivity.EXTRA_LATEST_USERNAME, username)
        }

        startActivity(intent)
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAdAutoScroll()
        timerJob?.cancel()
        if (::soundPool.isInitialized) soundPool.release()
    }

    /**
     * Only adds bottom space to the last row to avoid being "pressed" by the ad.
     * Does NOT change RV measurement, so it won't cause black screen / content pushed away.
     */
    class BottomSpaceDecoration(private val spacePx: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: android.graphics.Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            val position = parent.getChildAdapterPosition(view)
            if (position == RecyclerView.NO_POSITION) return

            val lm = parent.layoutManager as? GridLayoutManager ?: return
            val spanCount = lm.spanCount
            val itemCount = state.itemCount
            val isLastRow = position >= itemCount - spanCount

            if (isLastRow) outRect.bottom = spacePx
        }
    }
}
