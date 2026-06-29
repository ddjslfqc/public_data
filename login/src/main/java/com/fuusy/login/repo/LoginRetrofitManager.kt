package com.fuusy.login.repo

import com.fuusy.common.network.config.LocalCookieJar
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.network.RetrofitManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object LoginRetrofitManager {
	private val mOkClient = RetrofitManager.client

	private val mRetrofit: Retrofit = Retrofit.Builder()
		.baseUrl(ServerConfig.getWorkOrderBaseUrl()) // 使用全局服务器配置
		.client(mOkClient)
		.addConverterFactory(GsonConverterFactory.create())
		.build()

	fun <T> getService(serviceClass: Class<T>): T {
		return mRetrofit.create(serviceClass)
	}
} 