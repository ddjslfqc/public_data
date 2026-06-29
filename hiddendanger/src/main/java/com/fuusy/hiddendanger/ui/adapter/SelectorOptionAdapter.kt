package com.fuusy.hiddendanger.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.databinding.ItemSelectorOptionBinding

class SelectorOptionAdapter(
    private val options: List<String>,
    private val onItemClick: (String, Int) -> Unit
) : RecyclerView.Adapter<SelectorOptionAdapter.OptionViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): OptionViewHolder {
        val binding = ItemSelectorOptionBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return OptionViewHolder(binding)
    }

    override fun onBindViewHolder(holder: OptionViewHolder, position: Int) {
        holder.bind(options[position], position)
    }

    override fun getItemCount(): Int = options.size

    inner class OptionViewHolder(private val binding: ItemSelectorOptionBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(option: String, position: Int) {
            binding.tvOption.text = option
            binding.root.setOnClickListener {
                onItemClick(option, position)
            }
        }
    }
}