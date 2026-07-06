package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.data.PeerEvalFormRow
import com.fuusy.hiddendanger.data.PeerEvalScoreItem
import com.fuusy.hiddendanger.databinding.ItemPeerEvalCategoryHeaderBinding
import com.fuusy.hiddendanger.databinding.ItemPeerEvalScoreItemBinding

class PeerEvalScoreFormAdapter : ListAdapter<PeerEvalFormRow, RecyclerView.ViewHolder>(DiffCallback()) {

    private val scores = linkedMapOf<String, Int>()

    fun getScores(): List<PeerEvalScoreItem> =
        scores.map { (itemId, score) -> PeerEvalScoreItem(itemId = itemId, score = score) }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is PeerEvalFormRow.Category -> VIEW_CATEGORY
        is PeerEvalFormRow.Score -> VIEW_SCORE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_CATEGORY -> CategoryVH(
                ItemPeerEvalCategoryHeaderBinding.inflate(inflater, parent, false)
            )
            else -> ScoreVH(
                ItemPeerEvalScoreItemBinding.inflate(inflater, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = getItem(position)) {
            is PeerEvalFormRow.Category -> (holder as CategoryVH).bind(row.name)
            is PeerEvalFormRow.Score -> (holder as ScoreVH).bind(row.item.id, row.item.title, row.item.description)
        }
    }

    private inner class CategoryVH(
        private val binding: ItemPeerEvalCategoryHeaderBinding
    ) : RecyclerView.ViewHolder(binding.root) {
        fun bind(name: String) {
            binding.tvCategory.text = name
        }
    }

    private inner class ScoreVH(
        private val binding: ItemPeerEvalScoreItemBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val hearts: List<ImageView> by lazy {
            listOf(
                binding.heart1,
                binding.heart2,
                binding.heart3,
                binding.heart4,
                binding.heart5
            )
        }

        fun bind(itemId: String, title: String, description: String) {
            binding.tvTitle.text = title
            binding.tvDescription.text = description
            val initial = scores[itemId] ?: DEFAULT_SCORE
            scores[itemId] = initial
            renderHearts(initial)
            hearts.forEachIndexed { index, heart ->
                heart.setOnClickListener {
                    val score = index + 1
                    scores[itemId] = score
                    renderHearts(score)
                }
            }
        }

        private fun renderHearts(score: Int) {
            hearts.forEachIndexed { index, heart ->
                val filled = index < score
                heart.setImageResource(
                    if (filled) R.drawable.ic_peer_heart_filled
                    else R.drawable.ic_peer_heart_outline
                )
            }
            binding.tvScore.text = "${score} 颗心"
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<PeerEvalFormRow>() {
        override fun areItemsTheSame(oldItem: PeerEvalFormRow, newItem: PeerEvalFormRow): Boolean {
            return when {
                oldItem is PeerEvalFormRow.Category && newItem is PeerEvalFormRow.Category ->
                    oldItem.name == newItem.name
                oldItem is PeerEvalFormRow.Score && newItem is PeerEvalFormRow.Score ->
                    oldItem.item.id == newItem.item.id
                else -> false
            }
        }

        override fun areContentsTheSame(oldItem: PeerEvalFormRow, newItem: PeerEvalFormRow) =
            oldItem == newItem
    }

    companion object {
        private const val VIEW_CATEGORY = 0
        private const val VIEW_SCORE = 1
        private const val DEFAULT_SCORE = 3
    }
}
