package iss.nus.edu.sg.fragments.courseassignment.thememorygame

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
                if (adapterPosition != RecyclerView.NO_POSITION) {
                    onCardClicked(adapterPosition)
                }
            }
        }

        fun bind(card: MemoryCard) {
            // Load the image if the card is face up or already matched
            if (card.isFaceUp || card.isMatched) {
                if (card.contentSource is String) {
                    Glide.with(binding.ivCard.context).load(card.contentSource).into(binding.ivCard)
                } else {
                    binding.ivCard.setImageResource(card.contentSource as Int)
                }
            } else {
                // Show the card back if it's face down
                binding.ivCard.setImageResource(R.drawable.ic_card_back)
            }

            // Show or hide the checkmark based on the matched state
            binding.ivCheckMark.visibility = if (card.isMatched) View.VISIBLE else View.GONE
        }
    }
}
