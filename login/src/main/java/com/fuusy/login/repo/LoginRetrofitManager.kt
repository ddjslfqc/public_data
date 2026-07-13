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

	@Volatile
	private var cachedBaseUrl: String? = null
	@Volatile
	private var mRetrofit: Retrofit? = null

	private fun retrofit(): Retrofit {
		val baseUrl = ServerConfig.getWorkOrderBaseUrl()
		val existing = mRetrofit
		if (existing != null && cachedBaseUrl == baseUrl) return existing
		return synchronized(this) {
			val again = mRetrofit
			if (again != null && cachedBaseUrl == baseUrl) return@synchronized again
			cachedBaseUrl = baseUrl
			Retrofit.Builder()
				.baseUrl(baseUrl)
				.client(mOkClient)
				.addConverterFactory(GsonConverterFactory.create())
				.build()
				.also { mRetrofit = it }
		}
	}

	/** 服务器配置变更后调用，避免继续走旧的 Base URL */
	fun invalidate() {
		synchronized(this) {
			cachedBaseUrl = null
			mRetrofit = null
		}
	}

	fun <T> getService(serviceClass: Class<T>): T {
		return retrofit().create(serviceClass)
	}
} 