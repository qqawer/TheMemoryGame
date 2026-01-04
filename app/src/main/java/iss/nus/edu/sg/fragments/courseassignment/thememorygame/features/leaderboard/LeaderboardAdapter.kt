package iss.nus.edu.sg.fragments.courseassignment.thememorygame.features.leaderboard

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.fragments.courseassignment.thememorygame.R

class LeaderboardAdapter(
    private var items: List<LeaderboardRow> = emptyList()
) : RecyclerView.Adapter<LeaderboardAdapter.VH>() {

    fun submit(newItems: List<LeaderboardRow>) {
        items = newItems
        notifyDataSetChanged()
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tvRank: TextView = v.findViewById(R.id.tvRank)
        val tvName: TextView = v.findViewById(R.id.tvName)
        val tvTime: TextView = v.findViewById(R.id.tvTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_leaderboard, parent, false)
        return VH(v)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val it = items[position]
        holder.tvRank.text = (position + 1).toString()
        // Top 3 badge
        val badgeRes = when (position) {
            0 -> R.drawable.bg_rank_badge_gold
            1 -> R.drawable.bg_rank_badge_silver
            2 -> R.drawable.bg_rank_badge_bronze
            else -> R.drawable.bg_rank_badge
        }
        holder.tvRank.setBackgroundResource(badgeRes)

        holder.tvName.text = it.displayName()
        holder.tvTime.text = formatHMS(it.completeTimeSeconds)

    }

    private fun formatHMS(totalSeconds: Int): String {
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) String.format("%d:%02d:%02d", h, m, s)
        else String.format("%02d:%02d", m, s)
    }

}
