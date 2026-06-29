package com.fuusy.common.network

import DetailedLoggingInterceptor
import android.util.Log
import com.fuusy.common.network.config.LocalCookieJar
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

private const val TAG = "RetrofitManager"

object RetrofitManager {

    private val mOkClient = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS).retryOnConnectionFailure(true).followRedirects(false)
        .cookieJar(LocalCookieJar()).addInterceptor(DetailedLoggingInterceptor()) // 使用自定义拦截器
        .build()

    // 暴露全局 OkHttpClient，供其他模块复用，确保统一的日志与配置
    val client: OkHttpClient
        get() = mOkClient

    private var mRetrofit: Retrofit? = null


    fun initRetrofit(): RetrofitManager {
        mRetrofit = Retrofit.Builder().baseUrl(ServerConfig.getBaseUrl()) // 恢复原服务器地址
            .client(mOkClient).addConverterFactory(GsonConverterFactory.create()).build()
        return this
    }

    fun <T> getService(serviceClass: Class<T>): T {
        if (mRetrofit == null) {
            throw UninitializedPropertyAccessException("Retrofit必须初始化")
        } else {
            return mRetrofit!!.create(serviceClass)
        }
    }
}