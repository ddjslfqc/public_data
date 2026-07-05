package com.fuusy.jetpackkt

import android.os.Bundle
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.base.BaseActivity
import com.fuusy.common.update.AppUpdateChecker
import com.fuusy.jetpackkt.databinding.ActivitySplashBinding

class SplashActivity : BaseActivity<ActivitySplashBinding>() {

    override fun initData(savedInstanceState: Bundle?) {
        AppUpdateChecker.checkOnLaunch(
            activity = this,
            smallIcon = R.mipmap.logo_new
        ) {
            goLogin()
        }
    }

    private fun goLogin() {
        if (isFinishing || isDestroyed) return
        ARouter.getInstance().build("/login/LoginActivity").navigation()
        finish()
    }

    override fun getLayoutId(): Int = R.layout.activity_splash
}
