package com.fuusy.project.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AlarmRecordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 设置状态栏为白色，字体为深色
        window.statusBarColor = 0xFFFFFFFF.toInt()
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, AiAlarmRecordFragment())
            .commit()
    }
}
