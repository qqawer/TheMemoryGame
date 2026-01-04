package iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.ads

import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

class AdPagerAdapter(
    private val imageUrls: List<String>
) : RecyclerView.Adapter<AdPagerAdapter.AdVH>() {

    class AdVH(val iv: ImageView) : RecyclerView.ViewHolder(iv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdVH {
        val iv = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return AdVH(iv)
    }

    override fun onBindViewHolder(holder: AdVH, position: Int) {
        val url = imageUrls[position]
        Glide.with(holder.iv)
            .load(url)
            .into(holder.iv)
    }

    override fun getItemCount(): Int = imageUrls.size
}
