package com.fuusy.hiddendanger.ui.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.hiddendanger.databinding.ItemFormInputTextBinding
import com.fuusy.hiddendanger.databinding.ItemFormSelectorBinding

class DynamicFormAdapter(
    private var formItems: MutableList<FormItem>,
    private val onSelectorClick: (FormItem, Int) -> Unit = { _, _ -> },
    private val onInputChanged: (key: String, value: String) -> Unit = { _, _ -> }
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val VIEW_TYPE_INPUT_TEXT = 0
        const val VIEW_TYPE_SELECTOR = 2
    }

    data class FormItem(
        val key: String,
        val type: Int,
        val label: String,
        val isRequired: Boolean,
        var value: String = "",
        val placeholder: String? = null,
        val options: List<OptionItem>? = null
    )

    data class OptionItem(
        val value: String, val label: String
    )

    override fun getItemViewType(position: Int): Int {
        return formItems[position].type
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_INPUT_TEXT -> {
                val binding = ItemFormInputTextBinding.inflate(inflater, parent, false)
                InputTextViewHolder(binding)
            }


            VIEW_TYPE_SELECTOR -> {
                val binding = ItemFormSelectorBinding.inflate(inflater, parent, false)
                SelectorViewHolder(binding)
            }

            else -> {
                val binding = ItemFormInputTextBinding.inflate(inflater, parent, false)
                InputTextViewHolder(binding)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = formItems[position]
        when (holder) {
            is InputTextViewHolder -> holder.bind(item)
            is SelectorViewHolder -> holder.bind(item)
        }
    }

    override fun onBindViewHolder(
        holder: RecyclerView.ViewHolder, position: Int, payloads: List<Any>
    ) {
        if (payloads.isEmpty()) {
            super.onBindViewHolder(holder, position, payloads)
        } else {
            // 有payload时只更新数据，不重新绑定整个ViewHolder
            val item = formItems[position]
            when (holder) {
                is InputTextViewHolder -> holder.updateValue(item.value)
                is SelectorViewHolder -> holder.updateValue(item.value)
            }
        }
    }

    override fun getItemCount(): Int = formItems.size

    fun updateData(newFormItems: List<FormItem>) {
        // 检查数据是否真的发生了变化
        if (this.formItems != newFormItems) {
            this.formItems = newFormItems.toMutableList()
            notifyDataSetChanged()
        }
    }

    fun getItem(index: Int): FormItem = formItems[index]

    fun updateSingleItem(index: Int, newItem: FormItem) {
        if (index < formItems.size) {
            formItems[index] = newItem
            // 只更新数据，不调用任何notify方法，避免滚动
            // UI更新将通过其他方式处理
        }
    }

    inner class InputTextViewHolder(private val binding: ItemFormInputTextBinding) :
        RecyclerView.ViewHolder(binding.root) {
        private var currentWatcher: android.text.TextWatcher? = null
        private var lastUpdateTime = 0L
        private val UPDATE_DELAY = 300L // 300ms防抖延迟

        fun bind(item: FormItem) {
            binding.label.text = item.label
            binding.editText.hint = item.placeholder

            // 移除之前的TextWatcher
            currentWatcher?.let { binding.editText.removeTextChangedListener(it) }

            // 只在初始绑定时设置文本，避免在输入过程中重新设置
            if (binding.editText.text.toString() != item.value && binding.editText.text.isEmpty()) {
                binding.editText.setText(item.value)
            }

            val watcher = object : android.text.TextWatcher {
                override fun afterTextChanged(s: android.text.Editable?) {
                    val newValue = s?.toString() ?: ""
                    if (newValue != item.value) {
                        // 添加防抖机制，避免频繁触发更新
                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastUpdateTime > UPDATE_DELAY) {
                            onInputChanged(item.key, newValue)
                            lastUpdateTime = currentTime
                        }
                    }
                }

                override fun beforeTextChanged(
                    s: CharSequence?, start: Int, count: Int, after: Int
                ) {
                }

                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            }
            binding.editText.addTextChangedListener(watcher)
            currentWatcher = watcher
            binding.ivStartIcon.visibility = if (item.isRequired) View.VISIBLE else View.INVISIBLE
        }

        fun updateValue(newValue: String) {
            binding.editText.setText(newValue)
        }
    }

    inner class SelectorViewHolder(private val binding: ItemFormSelectorBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FormItem) {
            binding.label.text = item.label
            binding.selectedValue.text = resolveDisplayText(item)
            updateTextColor(item.placeholder)
            binding.ivStartIcon.visibility = if (item.isRequired) View.VISIBLE else View.INVISIBLE
            binding.linBg.setOnClickListener {
                onSelectorClick(item, adapterPosition)
                true
            }
        }

        fun updateValue(newValue: String) {
            binding.selectedValue.text = newValue
            updateTextColor(null)
        }

        private fun resolveDisplayText(item: FormItem): String {
            val raw = item.value.trim()
            if (raw.isNotBlank() && raw != "请输入" && raw != "请选择") {
                return raw
            }
            return item.placeholder?.takeIf { it.isNotBlank() } ?: "请选择"
        }

        private fun updateTextColor(placeholder: String?) {
            val text = binding.selectedValue.text?.toString().orEmpty()
            val isPlaceholder = text.isBlank() ||
                text == "请输入" ||
                text == "请选择" ||
                text == placeholder
            binding.selectedValue.setTextColor(
                Color.parseColor(if (isPlaceholder) "#999999" else "#e6000000")
            )
        }
    }
}