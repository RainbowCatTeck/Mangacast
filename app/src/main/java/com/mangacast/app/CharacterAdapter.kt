package com.mangacast.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.CircleCropTransformation

class CharacterAdapter(
    private val onCharacterClick: (CharacterEntry) -> Unit
) : RecyclerView.Adapter<CharacterAdapter.VH>() {

    private var allChars: List<CharacterEntry> = emptyList()
    private var displayed: List<CharacterEntry> = emptyList()
    private var currentFilter = "all"

    fun setCharacters(chars: List<CharacterEntry>) {
        allChars = chars
        applyFilter()
    }

    fun filter(role: String) {
        currentFilter = role
        applyFilter()
    }

    private fun applyFilter() {
        displayed = if (currentFilter == "all") allChars
        else allChars.filter { it.role == currentFilter }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_character, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(displayed[position], onCharacterClick)
    }

    override fun getItemCount() = displayed.size

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        private val avatar: ImageView = v.findViewById(R.id.charAvatar)
        private val nameEn: TextView = v.findViewById(R.id.charNameEn)
        private val nameJp: TextView = v.findViewById(R.id.charNameJp)
        private val roleBadge: TextView = v.findViewById(R.id.charRole)
        private val roleBar: View = v.findViewById(R.id.roleBar)

        fun bind(entry: CharacterEntry, onClick: (CharacterEntry) -> Unit) {
            itemView.setOnClickListener { onClick(entry) }
            nameEn.text = entry.name

            if (entry.nameKanji.isNotBlank()) {
                nameJp.text = entry.nameKanji
                nameJp.visibility = View.VISIBLE
            } else {
                nameJp.visibility = View.GONE
            }

            roleBadge.text = entry.role

            val ctx = itemView.context
            if (entry.role == "Main") {
                roleBadge.setBackgroundResource(R.drawable.badge_main)
                roleBadge.setTextColor(ctx.getColor(R.color.accent))
                roleBar.setBackgroundColor(ctx.getColor(R.color.accent))
            } else {
                roleBadge.setBackgroundResource(R.drawable.badge_supporting)
                roleBadge.setTextColor(ctx.getColor(R.color.blue))
                roleBar.setBackgroundColor(ctx.getColor(R.color.blue))
            }

            if (entry.imageUrl.isNotBlank()) {
                avatar.load(entry.imageUrl) {
                    transformations(CircleCropTransformation())
                    crossfade(true)
                    placeholder(R.drawable.ic_avatar_placeholder)
                    error(R.drawable.ic_avatar_placeholder)
                }
            } else {
                avatar.setImageResource(R.drawable.ic_avatar_placeholder)
            }
        }
    }
}
