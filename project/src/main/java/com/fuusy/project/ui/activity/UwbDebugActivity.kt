package com.fuusy.project.ui.activity

import android.graphics.drawable.GradientDrawable
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.project.R
import com.fuusy.project.databinding.ActivityUwbDebugBinding
import com.fuusy.project.ui.UwbLogAdapter
import com.fuusy.project.uwb.UwbDebugViewModel

@Route(path = "/project/UwbDebugActivity")
class UwbDebugActivity : BaseVmActivity<ActivityUwbDebugBinding>() {

    private lateinit var viewModel: UwbDebugViewModel
    private val logAdapter = UwbLogAdapter()

    override fun getLayoutId(): Int = R.layout.activity_uwb_debug

    override fun initData() {
        viewModel = ViewModelProvider(this)[UwbDebugViewModel::class.java]
        mBinding.etHost.setText(viewModel.loadSavedHost())
        mBinding.etPort.setText(viewModel.loadSavedPort())

        mBinding.btnBack.setOnClickListener { finish() }
        mBinding.rvLogs.layoutManager = LinearLayoutManager(this)
        mBinding.rvLogs.adapter = logAdapter

        mBinding.btnConnect.setOnClickListener {
            val host = mBinding.etHost.text?.toString().orEmpty()
            val port = mBinding.etPort.text?.toString().orEmpty()
            if (viewModel.connected.value == true) {
                viewModel.disconnect()
            } else {
                viewModel.connect(host, port)
            }
        }

        mBinding.btnQueryInfo.setOnClickListener { viewModel.sendQueryModuleInfo() }
        mBinding.btnReadModule.setOnClickListener { viewModel.sendReadModuleInfo() }
        mBinding.btnClearLog.setOnClickListener { viewModel.clearLogs() }
        mBinding.btnSendHex.setOnClickListener {
            viewModel.sendHex(mBinding.etHex.text?.toString().orEmpty())
        }

        viewModel.connected.observe(this) { connected ->
            updateConnectionUi(connected)
        }
        viewModel.logs.observe(this) { entries ->
            logAdapter.submitList(entries)
            if (entries.isNotEmpty()) {
                mBinding.rvLogs.scrollToPosition(0)
            }
        }
    }

    private fun updateConnectionUi(connected: Boolean) {
        mBinding.btnConnect.text = if (connected) "断开" else "连接"
        mBinding.tvStatus.text = if (connected) "已连接" else "未连接"
        val color = ContextCompat.getColor(
            this,
            if (connected) R.color.home_green else R.color.home_text_normal
        )
        (mBinding.viewStatusDot.background as? GradientDrawable)?.setColor(color)
            ?: mBinding.viewStatusDot.setBackgroundColor(color)
    }
}
