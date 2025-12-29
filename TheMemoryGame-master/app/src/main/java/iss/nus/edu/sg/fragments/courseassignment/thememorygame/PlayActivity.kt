
package iss.nus.edu.sg.fragments.courseassignment.thememorygame

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.databinding.ActivityPlayBinding
import kotlinx.coroutines.*

class PlayActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayBinding
    private lateinit var memoryCards: MutableList<MemoryCard>
    private lateinit var adapter: MemoryCardAdapter
    private var indexOfSingleSelectedCard: Int? = null

    private var timerJob: Job? = null
    private var timerSeconds = 0
    private var isTimerStarted = false
    private var matches = 0
    private val totalPairs = 6 // Based on 6 unique images
    private var imageUrls: ArrayList<String>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "The Memory Game"

        imageUrls = intent.getStringArrayListExtra("image_urls")
        if (imageUrls == null) {
            Toast.makeText(this, "Using temporary image set for testing.", Toast.LENGTH_LONG).show()
            imageUrls = ArrayList(listOf(
                "https://ss2.bdstatic.com/70cFvXSh_Q1YnxGkpoWK1HF6hhy/it/u=1659552792,3869332496&fm=253&gp=0.jpg",
                "https://th.bing.com/th/id/R.b8e84a0907bf9b5128dfa48be0ae48af?rik=tfH6k%2fT3hkauqw&riu=http%3a%2f%2fwww.08lr.cn%2fuploads%2fallimg%2f220330%2f1-2300141M0.jpg&ehk=dR6hTo1o7lNsHkpE62oIzMtJ%2bmxktf7%2fx6tp3Zt2uB8%3d&risl=&pid=ImgRaw&r=0",
                "https://img.shetu66.com/2023/03/10/1678415374269462.jpg",
                "https://picx.zhimg.com/v2-ab23d513fab6abd0a27cda9ba9676383_720w.jpg?source=172ae18b",
                "https://img.shetu66.com/2023/07/14/1689317564415678.png",
                "https://images.pexels.com/photos/3536216/pexels-photo-3536216.jpeg?auto=compress&cs=tinysrgb&w=800&lazy=load"
            ))
        }
        setupGame()
        adapter = MemoryCardAdapter(memoryCards) { position ->
            onCardClicked(position)
        }
        binding.rvCards.adapter = adapter
        binding.rvCards.layoutManager = GridLayoutManager(this, 3)

        binding.btnRestart.setOnClickListener {
            restartGame()
        }

        binding.btnLeaderboard.setOnClickListener {
            Toast.makeText(this, "功能待开发", Toast.LENGTH_SHORT).show()
        }
    }

    private fun restartGame() {
        timerJob?.cancel()
        isTimerStarted = false
        matches = 0
        indexOfSingleSelectedCard = null
        timerSeconds = 0
        binding.tvTimer.text = "00:00:00"
        setupGame()
        adapter.notifyDataSetChanged()
    }

    private fun startTimer() {
        isTimerStarted = true
        timerJob = CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(1000)
                timerSeconds++
                val hours = timerSeconds / 3600
                val minutes = (timerSeconds % 3600) / 60
                val seconds = timerSeconds % 60
                binding.tvTimer.text = String.format("%02d:%02d:%02d", hours, minutes, seconds)
            }
        }
    }

    private fun setupGame() {
        binding.tvMatches.text = "Matches: 0 / $totalPairs"
        var cardId = 0
        val duplicatedImages = (imageUrls!! + imageUrls!!).map { content ->
            MemoryCard(cardId++, content)
        }
        if (::memoryCards.isInitialized) {
            memoryCards.clear()
            memoryCards.addAll(duplicatedImages.shuffled())
        } else {
            memoryCards = duplicatedImages.shuffled().toMutableList()
        }
    }

    private fun onCardClicked(position: Int) {
        if (!isTimerStarted) {
            startTimer()
        }
        val card = memoryCards[position]
        if (card.isFaceUp || card.isMatched) return
        card.isFaceUp = true
        adapter.notifyItemChanged(position)
        if (indexOfSingleSelectedCard == null) {
            indexOfSingleSelectedCard = position
        } else {
            checkForMatch(indexOfSingleSelectedCard!!, position)
            indexOfSingleSelectedCard = null
        }
    }

    private fun checkForMatch(position1: Int, position2: Int) {
        if (memoryCards[position1].contentSource == memoryCards[position2].contentSource) {
            memoryCards[position1].isMatched = true
            memoryCards[position2].isMatched = true
            matches++
            binding.tvMatches.text = "Matches: $matches / $totalPairs"
            checkForGameOver()
        } else {
            CoroutineScope(Dispatchers.Main).launch {
                delay(1000)
                memoryCards[position1].isFaceUp = false
                memoryCards[position2].isFaceUp = false
                adapter.notifyItemChanged(position1)
                adapter.notifyItemChanged(position2)
            }
        }
    }

    private fun checkForGameOver() {
        if (matches == totalPairs) {
            timerJob?.cancel()
            Toast.makeText(this, "Game Over! Final Time: ${binding.tvTimer.text}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timerJob?.cancel()
    }
}
