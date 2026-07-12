package com.fuusy.login.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import android.widget.Switch
import com.fuusy.common.utils.IpConfigUtils
import com.fuusy.login.R

class IpConfigDialog(context: Context) : Dialog(context) {

    private lateinit var swUseLocal: Switch
    private lateinit var layoutLocal: LinearLayout
    private lateinit var layoutRemote: LinearLayout
    private lateinit var etLocalIp: EditText
    private lateinit var etLocalPort: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_ip_config)

        val layoutParams = window?.attributes
        layoutParams?.width = (context.resources.displayMetrics.widthPixels * 0.8).toInt()
        window?.attributes = layoutParams

        initViews()
        loadCurrentConfig()
        setupListeners()
    }

    private fun initViews() {
        swUseLocal = findViewById(R.id.sw_use_local)
        layoutLocal = findViewById(R.id.layout_local)
        layoutRemote = findViewById(R.id.layout_remote)
        etLocalIp = findViewById(R.id.et_local_ip)
        etLocalPort = findViewById(R.id.et_local_port)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
    }

    private fun loadCurrentConfig() {
        val useLocal = IpConfigUtils.isUseLocalServer()
        swUseLocal.isChecked = useLocal
        swUseLocal.isEnabled = true
        etLocalIp.setText(IpConfigUtils.getLocalServerIp())
        etLocalPort.setText(IpConfigUtils.getLocalServerPort())
        updateModeVisibility(useLocal)
    }

    private fun setupListeners() {
        swUseLocal.setOnCheckedChangeListener { _, isChecked ->
            updateModeVisibility(isChecked)
        }
        btnSave.setOnClickListener { saveConfig() }
        btnCancel.setOnClickListener { dismiss() }
    }

    private fun updateModeVisibility(useLocal: Boolean) {
        layoutLocal.visibility = if (useLocal) View.VISIBLE else View.GONE
        layoutRemote.visibility = if (useLocal) View.GONE else View.VISIBLE
    }

    private fun saveConfig() {
        if (swUseLocal.isChecked) {
            val ip = etLocalIp.text.toString().trim()
            val port = etLocalPort.text.toString().trim().ifEmpty { "9220" }
            if (TextUtils.isEmpty(ip)) {
                Toast.makeText(context, "请填写电脑局域网 IP", Toast.LENGTH_SHORT).show()
                return
            }
            IpConfigUtils.saveLocalServer(ip, port)
            Toast.makeText(context, "已切换为本地：$ip:$port", Toast.LENGTH_SHORT).show()
        } else {
            IpConfigUtils.setUseLocalServer(false)
            Toast.makeText(context, "已切换为远程测试服", Toast.LENGTH_SHORT).show()
        }
        dismiss()
    }
}
