package com.fuusy.project.ui.activity

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fuusy.project.databinding.DialogStatusPickerBinding
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class StatusPickerFragment : BottomSheetDialogFragment() {

    private var _binding: DialogStatusPickerBinding? = null
    private val binding get() = _binding!!

    var onStatusSelected: ((String) -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogStatusPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.ivClose.setOnClickListener { dismiss() }
        binding.tvStatusAll.setOnClickListener {
            onStatusSelected?.invoke("全部")
            dismiss()
        }
        binding.tvStatusProcessing.setOnClickListener {
            onStatusSelected?.invoke("处理中")
            dismiss()
        }
        binding.tvStatusDone.setOnClickListener {
            onStatusSelected?.invoke("已处理")
            dismiss()
        }
        binding.tvStatusCancel.setOnClickListener {
            onStatusSelected?.invoke("已取消")
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 