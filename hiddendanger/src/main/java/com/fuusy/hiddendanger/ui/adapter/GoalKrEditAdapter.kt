package com.fuusy.hiddendanger.ui.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.databinding.ItemGoalKrEditBinding
import com.fuusy.hiddendanger.ui.model.GoalKrEditItem

class GoalKrEditAdapter(
    private val onDelete: (Int) -> Unit,
    private val onChanged: () -> Unit,
    private val onItemAdded: (Int) -> Unit = {},
    private val onAssigneeClick: (Int) -> Unit = {}
) : RecyclerView.Adapter<GoalKrEditAdapter.VH>() {

    private val items = mutableListOf(GoalKrEditItem())
    private var nextId = 1L

    fun submitItems(newItems: List<GoalKrEditItem>) {
        items.clear()
        items.addAll(newItems.ifEmpty { listOf(createEmptyItem()) })
        nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        notifyDataSetChanged()
    }

    fun addItem(): Boolean {
        items.add(createEmptyItem())
        val index = items.lastIndex
        notifyItemInserted(index)
        onChanged()
        onItemAdded(index)
        return true
    }

    fun removeItem(position: Int) {
        if (items.size <= MIN_KR_COUNT || position !in items.indices) return
        items.removeAt(position)
        notifyItemRemoved(position)
        if (position < items.size) {
            notifyItemRangeChanged(position, items.size - position)
        }
        onChanged()
    }

    fun currentItems(): List<GoalKrEditItem> = items.map { it.copy() }

    fun itemCount(): Int = items.size

    fun canAddMore(): Boolean = items.size < MAX_KR_COUNT

    fun canRemove(): Boolean = items.size > MIN_KR_COUNT

    private fun createEmptyItem(): GoalKrEditItem {
        return GoalKrEditItem(id = nextId++)
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemGoalKrEditBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], position)
    }

    inner class VH(private val binding: ItemGoalKrEditBinding) :
        RecyclerView.ViewHolder(binding.root) {

        private val watchers = mutableListOf<Pair<android.widget.EditText, TextWatcher>>()

        fun bind(item: GoalKrEditItem, position: Int) {
            clearWatchers()

            binding.tvKrIndex.text = (position + 1).toString()
            binding.btnDelete.isVisible = canRemove()
            binding.btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDelete(pos)
                }
            }

            binding.etKrTitle.setText(item.title)
            binding.etTargetValue.setText(item.targetValue)
            binding.etUnit.setText(item.unit)
            binding.tvAssignee.text = item.assigneeName.ifBlank { "本人" }
            binding.rowAssignee.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onAssigneeClick(pos)
            }

            addWatcher(binding.etKrTitle) { syncCurrentItem() }
            addWatcher(binding.etTargetValue) { syncCurrentItem() }
            addWatcher(binding.etUnit) { syncCurrentItem() }
        }

        private fun syncCurrentItem() {
            val pos = bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos !in items.indices) return
            items[pos] = items[pos].copy(
                title = binding.etKrTitle.text?.toString().orEmpty(),
                targetValue = binding.etTargetValue.text?.toString().orEmpty(),
                unit = binding.etUnit.text?.toString().orEmpty()
            )
            onChanged()
        }

        private fun addWatcher(
            editText: android.widget.EditText,
            onAfter: () -> Unit
        ) {
            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                override fun afterTextChanged(s: Editable?) = onAfter()
            }
            editText.addTextChangedListener(watcher)
            watchers.add(editText to watcher)
        }

        fun requestTitleFocus() {
            binding.etKrTitle.requestFocus()
        }

        private fun clearWatchers() {
            watchers.forEach { (editText, watcher) ->
                editText.removeTextChangedListener(watcher)
            }
            watchers.clear()
        }
    }

    companion object {
        const val MIN_KR_COUNT = 1
        const val MAX_KR_COUNT = 20
    }
}
