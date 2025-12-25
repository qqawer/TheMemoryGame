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
 * 图片适配器：用于在 RecyclerView 中显示图片列表并处理选中逻辑
 */
class ImageAdapter(
    private val onImageClick: (Int) -> Unit
) : RecyclerView.Adapter<ImageAdapter.ImageViewHolder>() {

    private val images = mutableListOf<ImageItem>()

    inner class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivImage: ImageView = itemView.findViewById(R.id.ivImage)
        val vOverlay: View = itemView.findViewById(R.id.vOverlay)
        val tvCheckMark: TextView = itemView.findViewById(R.id.tvCheckMark)

        fun bind(item: ImageItem, position: Int) {
            val imgFile = File(item.url)

            Glide.with(itemView.context)
                .load(imgFile)
                .placeholder(ColorDrawable(Color.LTGRAY))
                .error(ColorDrawable(Color.RED))
                .centerCrop()
                .into(ivImage)

            vOverlay.visibility = if (item.isSelected) View.VISIBLE else View.GONE
            tvCheckMark.visibility = if (item.isSelected) View.VISIBLE else View.GONE

            itemView.setOnClickListener {
                onImageClick(position)
            }
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

    // 新增：获取所有图片的路径（用于保存历史）
    fun getAllImagePaths(): List<String> {
        return images.map { it.url }
    }
}
