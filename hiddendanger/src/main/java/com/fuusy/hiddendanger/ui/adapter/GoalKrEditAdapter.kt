package com.fuusy.hiddendanger.ui.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.SeekBar
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.databinding.ItemGoalKrEditBinding
import com.fuusy.hiddendanger.ui.model.GoalKrEditItem
import com.fuusy.hiddendanger.ui.model.GoalKrWeightHelper

class GoalKrEditAdapter(
    private val onDelete: (Int) -> Unit,
    private val onChanged: () -> Unit,
    private val onItemAdded: (Int) -> Unit = {},
    private val onAssigneeClick: (Int) -> Unit = {},
    private val onWeightChanged: (Int, Int) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<GoalKrEditAdapter.VH>() {

    private val items = mutableListOf(GoalKrEditItem())
    private var nextId = 1L
    private var showWeightSliders = false

    fun submitItems(newItems: List<GoalKrEditItem>) {
        items.clear()
        items.addAll(newItems.ifEmpty { listOf(createEmptyItem()) })
        nextId = (items.maxOfOrNull { it.id } ?: 0L) + 1
        showWeightSliders = items.size > 1
        notifyDataSetChanged()
    }

    fun addItem(): Boolean {
        items.add(createEmptyItem())
        redistributeWeightsEqually()
        val index = items.lastIndex
        notifyDataSetChanged()
        onChanged()
        onItemAdded(index)
        return true
    }

    fun removeItem(position: Int) {
        if (items.size <= MIN_KR_COUNT || position !in items.indices) return
        items.removeAt(position)
        redistributeWeightsEqually()
        notifyDataSetChanged()
        onChanged()
    }

    fun currentItems(): List<GoalKrEditItem> = items.map { it.copy() }

    fun itemCount(): Int = items.size

    fun canAddMore(): Boolean = items.size < MAX_KR_COUNT

    fun canRemove(): Boolean = items.size > MIN_KR_COUNT

    fun totalWeight(): Int = GoalKrWeightHelper.total(items.map { it.weight })

    fun applyLinkedWeights(changedIndex: Int, newWeight: Int) {
        if (changedIndex !in items.indices) return
        val weights = items.map { it.weight }.toIntArray()
        val adjusted = GoalKrWeightHelper.adjustLinked(weights, changedIndex, newWeight)
        adjusted.forEachIndexed { index, weight ->
            items[index] = items[index].copy(weight = weight)
        }
        showWeightSliders = items.size > 1
        notifyDataSetChanged()
        onChanged()
    }

    private fun redistributeWeightsEqually() {
        val distributed = GoalKrWeightHelper.distributeEqually(items.size)
        distributed.forEachIndexed { index, weight ->
            items[index] = items[index].copy(weight = weight)
        }
        showWeightSliders = items.size > 1
    }

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
        private var weightListener: SeekBar.OnSeekBarChangeListener? = null
        private var targetListener: SeekBar.OnSeekBarChangeListener? = null

        fun bind(item: GoalKrEditItem, position: Int) {
            clearWatchers()
            clearSeekListeners()

            binding.tvKrIndex.text = (position + 1).toString()
            binding.btnDelete.isVisible = canRemove()
            binding.btnDelete.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onDelete(pos)
                }
            }

            binding.etKrTitle.setText(item.title)
            binding.tvAssignee.text = item.assigneeName.ifBlank { "本人" }

            binding.rowAssignee.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) onAssigneeClick(pos)
            }

            bindTargetUi(item)
            bindWeightUi(item)

            addWatcher(binding.etKrTitle) { syncCurrentItem() }
        }

        private fun bindTargetUi(item: GoalKrEditItem) {
            val percent = item.targetPercent.coerceIn(0, 100)
            binding.tvTarget.text = "$percent%"
            binding.seekTarget.max = 100
            binding.seekTarget.progress = percent

            targetListener = object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    binding.tvTarget.text = "$progress%"
                    if (!fromUser) return
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    items[pos] = items[pos].copy(targetPercent = progress)
                    onChanged()
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
            binding.seekTarget.setOnSeekBarChangeListener(targetListener)
        }

        private fun bindWeightUi(item: GoalKrEditItem) {
            val multi = showWeightSliders
            binding.rowWeight.isVisible = multi
            if (!multi) return

            binding.tvWeight.text = "${item.weight}%"
            binding.seekWeight.max = 100
            binding.seekWeight.progress = item.weight

            weightListener = object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (!fromUser) return
                    val pos = bindingAdapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    val weight = progress.coerceAtLeast(GoalKrWeightHelper.MIN_WEIGHT)
                    onWeightChanged(pos, weight)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            }
            binding.seekWeight.setOnSeekBarChangeListener(weightListener)
        }

        private fun syncCurrentItem() {
            val pos = bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION || pos !in items.indices) return
            items[pos] = items[pos].copy(
                title = binding.etKrTitle.text?.toString().orEmpty()
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

        private fun clearSeekListeners() {
            binding.seekWeight.setOnSeekBarChangeListener(null)
            binding.seekTarget.setOnSeekBarChangeListener(null)
            weightListener = null
            targetListener = null
        }
    }

    companion object {
        const val MIN_KR_COUNT = 1
        const val MAX_KR_COUNT = 20
    }
}
