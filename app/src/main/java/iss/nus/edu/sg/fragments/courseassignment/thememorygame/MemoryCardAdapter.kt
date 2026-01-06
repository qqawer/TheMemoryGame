package iss.nus.edu.sg.fragments.courseassignment.thememorygame

import android.animation.Animator
import android.animation.AnimatorInflater
import android.animation.AnimatorListenerAdapter
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

    /**
     * Default bind: no animation to avoid issues with RecyclerView recycling.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(cards[position], animateFlip = false, playMatchEffect = false)
    }

    /**
     * Bind with payload: forces a specific animation (flip / flip_back / match).
     * You need to trigger this from PlayActivity using notifyItemChanged(pos, "flip").
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: MutableList<Any>) {
        if (payloads.isEmpty()) {
            holder.bind(cards[position], animateFlip = false, playMatchEffect = false)
            return
        }

        val payload = payloads[0] as? String
        when (payload) {
            "flip" -> holder.bind(cards[position], animateFlip = true, playMatchEffect = false)
            "flip_back" -> holder.bind(cards[position], animateFlip = true, playMatchEffect = false)
            "match" -> holder.bind(cards[position], animateFlip = true, playMatchEffect = true)
            else -> holder.bind(cards[position], animateFlip = false, playMatchEffect = false)
        }
    }

    override fun getItemCount() = cards.size

    inner class ViewHolder(private val binding: ItemCardBinding) : RecyclerView.ViewHolder(binding.root) {

        private var isAnimating = false

        init {
            // 3D depth: without this, rotationY would look "flat" or even distorted.
            val density = binding.root.resources.displayMetrics.density
            binding.cardContainer.cameraDistance = 8000f * density

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                val card = cards[pos]
                // Don't allow clicks during animation or if matched, to prevent state corruption from rapid clicks.
                if (isAnimating || card.isMatched) return@setOnClickListener

                onCardClicked(pos)
            }
        }

        fun bind(card: MemoryCard, animateFlip: Boolean, playMatchEffect: Boolean) {
            val shouldShowFront = card.isFaceUp || card.isMatched

            // 1) First, prepare the front image to avoid a blank flash during the flip.
            if (card.contentSource is String) {
                Glide.with(binding.ivFront.context)
                    .load(card.contentSource)
                    .into(binding.ivFront)
            } else {
                binding.ivFront.setImageResource(card.contentSource as Int)
            }

            // 2) Back image (you can replace this with a more refined card back drawable).
            // If you haven't created card_back_placeholder, use ic_card_back for now.
            val backRes = runCatching { R.drawable.card_back_placeholder }.getOrElse { R.drawable.ic_card_back }
            binding.ivBack.setImageResource(backRes)

            // 3) Match overlay (keeping the checkmark for now, can be replaced with a badge/rune later).
            binding.ivCheckMark.visibility = if (card.isMatched) View.VISIBLE else View.GONE
            binding.viewMatchedGlow.visibility = if (card.isMatched) View.VISIBLE else View.GONE
            binding.viewMatchedDim.visibility = if (card.isMatched) View.VISIBLE else View.GONE
            binding.viewCheckGlow.visibility = if (card.isMatched) View.VISIBLE else View.GONE

            if (!animateFlip) {
                // No animation: directly sync the front/back state (to prevent display errors due to RecyclerView recycling).
                setFaceState(shouldShowFront)
                resetTransforms()
            } else {
                // Play flip animation: force execution (not relying on "detecting state change").
                animateFlip(toShowFront = shouldShowFront) {
                    if (playMatchEffect && card.isMatched) {
                        playMatchedEffect()
                    }
                }
            }
        }

        private fun setFaceState(showFront: Boolean) {
            binding.viewFront.visibility = if (showFront) View.VISIBLE else View.GONE
            binding.viewBack.visibility = if (showFront) View.GONE else View.VISIBLE
        }

        private fun resetTransforms() {
            binding.cardContainer.rotationY = 0f
            binding.cardContainer.scaleX = 1f
            binding.cardContainer.scaleY = 1f
            binding.cardContainer.translationX = 0f
        }

        private fun animateFlip(toShowFront: Boolean, onEnd: () -> Unit) {
            if (isAnimating) return
            isAnimating = true

            val ctx = binding.root.context
            val container = binding.cardContainer

            // Wind-up: slightly scale up (for a sense of power).
            container.animate().cancel()
            container.scaleX = 1f
            container.scaleY = 1f
            container.animate()
                .scaleX(1.04f)
                .scaleY(1.04f)
                .setDuration(60)
                .start()

            val flipOut = AnimatorInflater.loadAnimator(ctx, R.animator.flip_out).apply {
                setTarget(container)
            }
            val flipIn = AnimatorInflater.loadAnimator(ctx, R.animator.flip_in).apply {
                setTarget(container)
            }

            flipOut.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    // Switch faces at the 90° point.
                    setFaceState(toShowFront)

                    flipIn.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            // Bounce back to final state.
                            container.animate()
                                .scaleX(1f)
                                .scaleY(1f)
                                .setDuration(70)
                                .start()

                            isAnimating = false
                            onEnd()
                        }
                    })

                    flipIn.start()
                }
            })

            flipOut.start()
        }

        private fun playMatchedEffect() {
            // Match success: bounce back + a more "solid" impact feel.
            val container = binding.cardContainer
            container.animate().cancel()
            container.animate()
                .scaleX(1.06f)
                .scaleY(1.06f)
                .setDuration(120)
                .withEndAction {
                    container.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(160)
                        .start()
                }
                .start()
        }
    }
}
