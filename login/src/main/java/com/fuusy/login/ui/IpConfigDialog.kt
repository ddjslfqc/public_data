package com.fuusy.login.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.view.Window
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.fuusy.common.utils.SpUtils
import com.fuusy.login.R

class IpConfigDialog(context: Context) : Dialog(context) {

    private lateinit var etIp1: EditText
    private lateinit var etPort1: EditText
    private lateinit var etIp2: EditText
    private lateinit var etPort2: EditText
    private lateinit var etIp3: EditText
    private lateinit var etPort3: EditText
    private lateinit var btnSave: Button
    private lateinit var btnCancel: Button

    companion object {
        const val KEY_IP_1 = "custom_ip_1"
        const val KEY_PORT_1 = "custom_port_1"
        const val KEY_IP_2 = "custom_ip_2"
        const val KEY_PORT_2 = "custom_port_2"
        const val KEY_IP_3 = "custom_ip_3"
        const val KEY_PORT_3 = "custom_port_3"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.dialog_ip_config)
        
        // 设置对话框宽度为屏幕的80%
        val layoutParams = window?.attributes
        layoutParams?.width = (context.resources.displayMetrics.widthPixels * 0.8).toInt()
        window?.attributes = layoutParams
        
        initViews()
        loadCurrentConfig()
        setupListeners()
    }

    private fun initViews() {
        etIp1 = findViewById(R.id.et_ip_1)
        etPort1 = findViewById(R.id.et_port_1)
        etIp2 = findViewById(R.id.et_ip_2)
        etPort2 = findViewById(R.id.et_port_2)
        etIp3 = findViewById(R.id.et_ip_3)
        etPort3 = findViewById(R.id.et_port_3)
        btnSave = findViewById(R.id.btn_save)
        btnCancel = findViewById(R.id.btn_cancel)
    }

    private fun loadCurrentConfig() {
        // 加载当前保存的配置
        val ip1 = SpUtils.getString(KEY_IP_1) ?: "10.237.25.119"
        val port1 = SpUtils.getString(KEY_PORT_1) ?: "9250"
        val ip2 = SpUtils.getString(KEY_IP_2) ?: "10.237.25.119"
        val port2 = SpUtils.getString(KEY_PORT_2) ?: "8088"
        val ip3 = SpUtils.getString(KEY_IP_3) ?: "10.237.25.119"
        val port3 = SpUtils.getString(KEY_PORT_3) ?: "8055"

        etIp1.setText(ip1)
        etPort1.setText(port1)
        etIp2.setText(ip2)
        etPort2.setText(port2)
        etIp3.setText(ip3)
        etPort3.setText(port3)
    }

    private fun setupListeners() {
        btnSave.setOnClickListener {
            saveConfig()
        }

        btnCancel.setOnClickListener {
            dismiss()
        }
    }

    private fun saveConfig() {
        val ip1 = etIp1.text.toString().trim()
        val port1 = etPort1.text.toString().trim()
        val ip2 = etIp2.text.toString().trim()
        val port2 = etPort2.text.toString().trim()
        val ip3 = etIp3.text.toString().trim()
        val port3 = etPort3.text.toString().trim()

        // 验证输入
        if (TextUtils.isEmpty(ip1) || TextUtils.isEmpty(port1)) {
            Toast.makeText(context, "请填写第一个IP和端口", Toast.LENGTH_SHORT).show()
            return
        }

        if (TextUtils.isEmpty(ip2) || TextUtils.isEmpty(port2)) {
            Toast.makeText(context, "请填写第二个IP和端口", Toast.LENGTH_SHORT).show()
            return
        }

        if (TextUtils.isEmpty(ip3) || TextUtils.isEmpty(port3)) {
            Toast.makeText(context, "请填写第三个IP和端口", Toast.LENGTH_SHORT).show()
            return
        }

        // 保存配置
        SpUtils.put(KEY_IP_1, ip1)
        SpUtils.put(KEY_PORT_1, port1)
        SpUtils.put(KEY_IP_2, ip2)
        SpUtils.put(KEY_PORT_2, port2)
        SpUtils.put(KEY_IP_3, ip3)
        SpUtils.put(KEY_PORT_3, port3)

        Toast.makeText(context, "配置已保存", Toast.LENGTH_SHORT).show()
        dismiss()
    }
} 