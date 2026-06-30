package com.fuusy.jetpackkt

import android.app.Application
import android.content.Context
import androidx.multidex.BuildConfig
import androidx.multidex.MultiDex
import androidx.paging.ExperimentalPagingApi
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.loadsir.EmptyCallback
import com.fuusy.common.loadsir.ErrorCallback
import com.fuusy.common.loadsir.LoadingCallback
import com.fuusy.common.utils.AppHelper
import com.fuusy.common.network.UserIdProvider
import com.fuusy.common.utils.SpUtils
import com.fuusy.login.di.moduleLogin
import com.fuusy.project.di.moduleProject
import com.kingja.loadsir.core.LoadSir
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import com.fuusy.service.repo.DbHelper
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.fuusy.hiddendanger.ui.album.CustomAlbumViewModel
import com.fuusy.jetpackkt.crash.GlobalExceptionProtector


@ExperimentalPagingApi
class MainApp : Application() {

    private val modules = arrayListOf(
        moduleLogin, moduleProject
    )

    override fun onCreate() {
        super.onCreate()
        
        // 初始化 SpUtils
        SpUtils.init(this)
        
        initARouter()
        initLoadSir()
        initKoin()
        AppHelper.init(this.applicationContext)
        UserIdProvider.restoreFromCache()

        GlobalExceptionProtector.install(this)

        GlobalScope.launch(Dispatchers.IO) {
            DbHelper.getUserInfo(this@MainApp)?.id?.toLong()?.let { UserIdProvider.update(it) }
            com.fuusy.hiddendanger.viewmodel.PersonalViewModel.preloadRecentMedia(this@MainApp)
            CustomAlbumViewModel.preloadAlbum(this@MainApp, maxCount = 100)
        }
    }

    //koin
    private fun initKoin() {
        startKoin {
            androidLogger()
            androidContext(this@MainApp)
            modules(modules)
        }
    }


    private fun initLoadSir() {
        LoadSir.beginBuilder()
            .addCallback(ErrorCallback())
            .addCallback(LoadingCallback())
            .addCallback(EmptyCallback())
            .setDefaultCallback(LoadingCallback::class.java)
            .commit()
    }

    private fun initARouter() {
        ARouter.init(this)
        if (BuildConfig.DEBUG) {
            ARouter.openLog()
            ARouter.openDebug()
        }
    }

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        MultiDex.install(base)
    }
}