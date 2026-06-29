package com.fuusy.jetpackkt

import android.os.Bundle
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.base.BaseActivity
import com.fuusy.jetpackkt.databinding.ActivitySplashBinding

class SplashActivity : BaseActivity<ActivitySplashBinding>() {

    override fun initData(savedInstanceState: Bundle?) {
        // 直接跳转到登录页面
        ARouter.getInstance().build("/login/LoginActivity").navigation()
        finish()
    }

    override fun getLayoutId(): Int = R.layout.activity_splash
}