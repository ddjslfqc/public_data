package com.fuusy.login

import android.app.Application
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.login.di.moduleLogin
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class LoginApp : Application() {
    
    override fun onCreate() {
        super.onCreate()
        initKoin()
        initARouter()
    }
    
    private fun initKoin() {
        startKoin {
            androidLogger()
            androidContext(this@LoginApp)
            modules(moduleLogin)
        }
    }
    
    private fun initARouter() {
        ARouter.init(this)
    }
}