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
        holder.tvName.text = it.displayName()
        holder.tvTime.text = "${it.displaySeconds()}s"
    }
}
