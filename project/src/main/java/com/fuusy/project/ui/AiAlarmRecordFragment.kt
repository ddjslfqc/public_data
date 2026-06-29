package com.fuusy.project.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.fuusy.project.R
import com.fuusy.project.databinding.FragmentAlarmRecordBinding
import com.fuusy.project.viewmodel.AiAlarmRecordViewModel
import com.bigkoo.pickerview.builder.TimePickerBuilder
import com.bigkoo.pickerview.view.TimePickerView
import java.text.SimpleDateFormat
import java.util.*

class AiAlarmRecordFragment : Fragment() {
    private lateinit var adapter: AiAlarmRecordAdapter
    private var _binding: FragmentAlarmRecordBinding? = null
    private val binding get() = _binding!!
    private lateinit var viewModel: AiAlarmRecordViewModel

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentAlarmRecordBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        adapter = AiAlarmRecordAdapter {
            val intent = Intent(requireContext(), AiAlarmRecordDetailActivity::class.java)
            intent.putExtra("ai_alarm_record", it)
            startActivity(intent)
        }
        binding.rvAlarmList.layoutManager = LinearLayoutManager(context)
        binding.rvAlarmList.adapter = adapter
        viewModel = ViewModelProvider(this)[AiAlarmRecordViewModel::class.java]
        viewModel.aiAlarmList.observe(viewLifecycleOwner) {
            adapter.data = it
            adapter.notifyDataSetChanged()
        }
        // 日期选择功能
        binding.root.findViewById<TextView>(R.id.tvFilterStart)?.setOnClickListener {
            showDatePicker(true)
        }
        binding.root.findViewById<TextView>(R.id.tvFilterEnd)?.setOnClickListener {
            showDatePicker(false)
        }
    }

    private fun showDatePicker(isStartTime: Boolean) {
        val title = if (isStartTime) "起始时间" else "终止时间"
        val calendar = Calendar.getInstance()
        TimePickerBuilder(requireContext()) { date, _ ->
            val format = SimpleDateFormat("yyyy/MM/dd", Locale.CHINA)
            val formattedDate = format.format(date)
            if (isStartTime) {
                binding.root.findViewById<TextView>(R.id.tvFilterStart)?.text = formattedDate
            } else {
                binding.root.findViewById<TextView>(R.id.tvFilterEnd)?.text = formattedDate
            }
        }.setType(booleanArrayOf(true, true, true, false, false, false)).setCancelText("清除")
            .setSubmitText("确定").setTitleText(title).setOutSideCancelable(true).isCyclic(false)
            .setTitleColor(android.graphics.Color.BLACK)
            .setSubmitColor(android.graphics.Color.parseColor("#1465EB"))
            .setCancelColor(android.graphics.Color.parseColor("#666666"))
            .setTitleBgColor(android.graphics.Color.WHITE).setBgColor(android.graphics.Color.WHITE)
            .setDate(calendar).setLabel("年", "月", "日", "", "", "").isCenterLabel(false)
            .setTextColorCenter(android.graphics.Color.parseColor("#1465EB"))
            .setTextColorOut(android.graphics.Color.GRAY).setContentTextSize(16).setSubCalSize(15)
            .setTitleSize(17).setLineSpacingMultiplier(2.0f).build().show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
} 