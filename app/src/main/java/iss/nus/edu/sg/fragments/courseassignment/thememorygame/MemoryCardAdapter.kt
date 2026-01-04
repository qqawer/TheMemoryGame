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
     * 默认 bind：不播动画，避免 RecyclerView 复用导致乱飞
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(cards[position], animateFlip = false, playMatchEffect = false)
    }

    /**
     * 带 payload 的 bind：强制执行某种动画（flip / flip_back / match）
     * 你需要在 PlayActivity 里用 notifyItemChanged(pos, "flip") 等方式触发
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
            // 3D 纵深：没有这个 rotationY 会很“扁”甚至变形怪
            val density = binding.root.resources.displayMetrics.density
            binding.cardContainer.cameraDistance = 8000f * density

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return@setOnClickListener

                val card = cards[pos]
                // 动画中/已匹配不让点，避免连点造成状态错乱
                if (isAnimating || card.isMatched) return@setOnClickListener

                onCardClicked(pos)
            }
        }

        fun bind(card: MemoryCard, animateFlip: Boolean, playMatchEffect: Boolean) {
            val shouldShowFront = card.isFaceUp || card.isMatched

            // 1) 先准备正面图（避免翻过去一瞬间空白）
            if (card.contentSource is String) {
                Glide.with(binding.ivFront.context)
                    .load(card.contentSource)
                    .into(binding.ivFront)
            } else {
                binding.ivFront.setImageResource(card.contentSource as Int)
            }

            // 2) 背面图（你可以换成更精致的卡背 drawable）
            // 如果你没做 card_back_placeholder，就先用 ic_card_back
            val backRes = runCatching { R.drawable.card_back_placeholder }.getOrElse { R.drawable.ic_card_back }
            binding.ivBack.setImageResource(backRes)

            // 3) 匹配遮罩（先保留勾，后面可以换成徽章/符文）
            binding.ivCheckMark.visibility = if (card.isMatched) View.VISIBLE else View.GONE
            binding.viewMatchedGlow.visibility = if (card.isMatched) View.VISIBLE else View.GONE
            binding.viewMatchedDim.visibility = if (card.isMatched) View.VISIBLE else View.GONE
            binding.viewCheckGlow.visibility = if (card.isMatched) View.VISIBLE else View.GONE

            if (!animateFlip) {
                // 不播动画：直接同步正反面（防 RecyclerView 复用导致显示错）
                setFaceState(shouldShowFront)
                resetTransforms()
            } else {
                // 播翻牌动画：强制执行（不靠“检测状态变化”）
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

            // 蓄力：轻微放大（力量感）
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
                    // 90° 处切面
                    setFaceState(toShowFront)

                    flipIn.addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) {
                            // 回弹落地
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
            // 匹配成功：回弹 + “稳重”一点的冲击感
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
