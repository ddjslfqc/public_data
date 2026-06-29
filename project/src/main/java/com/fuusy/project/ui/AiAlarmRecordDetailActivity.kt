package com.fuusy.project.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.lifecycle.ViewModelProvider
import com.fuusy.project.R
import com.fuusy.project.bean.AiAlarmRecord
import com.fuusy.project.databinding.ActivityAiAlarmRecordDetailBinding
import com.fuusy.project.viewmodel.AiAlarmRecordDetailViewModel

class AiAlarmRecordDetailActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = 0xFFFF5A1F.toInt()
        val binding: ActivityAiAlarmRecordDetailBinding = DataBindingUtil.setContentView(this, R.layout.activity_ai_alarm_record_detail)
        val viewModel = ViewModelProvider(this)[AiAlarmRecordDetailViewModel::class.java]
        binding.viewModel = viewModel
        binding.lifecycleOwner = this
        val record = intent.getSerializableExtra("ai_alarm_record") as? AiAlarmRecord
        record?.let { viewModel.record.value = it }
        binding.ivBack.setOnClickListener { finish() }
        // 创建工单按钮事件可在此添加
    }
} 