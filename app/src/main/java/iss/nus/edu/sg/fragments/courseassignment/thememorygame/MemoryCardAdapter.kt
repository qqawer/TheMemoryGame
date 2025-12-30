package iss.nus.edu.sg.fragments.courseassignment.thememorygame

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.LayerDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.databinding.ItemCardBinding

class MemoryCardAdapter(
    private val cards: List<MemoryCard>,
    private val onCardClicked: (Int) -> Unit
) : RecyclerView.Adapter<MemoryCardAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(cards[position])
    }

    override fun getItemCount() = cards.size

    inner class ViewHolder(private val binding: ItemCardBinding) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.root.setOnClickListener {
                if (adapterPosition != RecyclerView.NO_POSITION && !cards[adapterPosition].isMatched) {
                    onCardClicked(adapterPosition)
                }
            }
        }

        fun bind(card: MemoryCard) {
            val cardView = binding.root as CardView
            // Reset state
            cardView.setCardBackgroundColor(Color.WHITE)
            binding.ivCard.scaleType = ImageView.ScaleType.CENTER_CROP

            // When card is matched
            if (card.isMatched) {
                // 1. Show the original image
                if (card.contentSource is String) {
                    Glide.with(binding.ivCard.context).load(card.contentSource).into(binding.ivCard)
                } else {
                    binding.ivCard.setImageResource(card.contentSource as Int)
                }

                // 2. Create overlay and checkmark drawables
                val overlay = ColorDrawable(Color.parseColor("#99888888")) // Semi-transparent grey
                val check = ContextCompat.getDrawable(binding.root.context, R.drawable.ic_check_large)
                
                // 3. Combine them in a LayerDrawable and set as foreground
                val layers = arrayOf(overlay, check)
                val layerDrawable = LayerDrawable(layers)
                binding.ivCard.foreground = layerDrawable
            } 
            // When card is not matched
            else {
                binding.ivCard.foreground = null // Clear the foreground
                if (card.isFaceUp) {
                    if (card.contentSource is String) {
                        Glide.with(binding.ivCard.context).load(card.contentSource).into(binding.ivCard)
                    } else {
                        binding.ivCard.setImageResource(card.contentSource as Int)
                    }
                } else {
                    binding.ivCard.setImageResource(R.drawable.ic_card_back)
                }
            }
        }
    }
}
