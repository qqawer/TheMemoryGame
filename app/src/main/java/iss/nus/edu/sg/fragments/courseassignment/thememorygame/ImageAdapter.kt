package iss.nus.edu.sg.fragments.courseassignment.thememorygame

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

/**
 * Image adapter: used to display a list of images in a RecyclerView and handle selection logic
 */
class ImageAdapter(
    private val onImageClick: (Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    private val images = mutableListOf<ImageItem>()

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivImage: ImageView = itemView.findViewById(R.id.ivImage)
        val vOverlay: View = itemView.findViewById(R.id.vOverlay)
        val tvCheckMark: TextView = itemView.findViewById(R.id.tvCheckMark)
        val ivCheckGold: ImageView = itemView.findViewById(R.id.ivCheckGold)

        // New: selected gold border
        val vBorder: View = itemView.findViewById(R.id.vBorder)

        fun bind(item: ImageItem, position: Int) {
            val imgFile = File(item.url)
            Glide.with(itemView.context)
                .load(imgFile)
                .placeholder(android.R.color.transparent)
                .error(android.R.color.transparent)
                .centerCrop()
                .into(ivImage)

            val selected = item.isSelected

            // Desaturate if not selected / Highlight if selected
            vOverlay.alpha = if (selected) 0f else 0.55f
            vBorder.alpha = if (selected) 1f else 0f

            // Center checkmark: use alpha for fade-in/fade-out (more "advanced" than visibility)
            ivCheckGold.alpha = if (selected) 0.95f else 0f

            // The old top-right badge is no longer used (but the id is kept)
            tvCheckMark.visibility = View.GONE

            // Slightly enlarge when selected
            itemView.scaleX = if (selected) 1.03f else 1.0f
            itemView.scaleY = if (selected) 1.03f else 1.0f

            itemView.setOnClickListener { onImageClick(position) }
            itemView.alpha = 1f
            ivImage.alpha = 1f
            itemView.rotation = 0f
        }
    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        holder.bind(images[position], position)
    }

    override fun getItemCount() = images.size

    fun addImage(image: ImageItem) {
        images.add(image)
        notifyItemInserted(images.size - 1)
    }

    fun clearImages() {
        val size = images.size
        images.clear()
        notifyItemRangeRemoved(0, size)
    }

    fun toggleSelection(position: Int) {
        images[position].isSelected = !images[position].isSelected
        notifyItemChanged(position)
    }

    fun getSelectedCount() = images.count { it.isSelected }

    fun getSelectedImages(): List<String> {
        return images.filter { it.isSelected }.map { it.url }
    }

    // New: get all image paths (for saving history)
    fun getAllImagePaths(): List<String> {
        return images.map { it.url }
    }
}
