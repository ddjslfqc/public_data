package com.fuusy.project.network

import com.fuusy.common.network.ServerConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.fuusy.project.repo.ProjectApi
import com.fuusy.project.repo.VideoApi
import com.fuusy.common.network.RetrofitManager

object ApiService {
	// Retrofit 单例
	val retrofitByWorkOrder: Retrofit by lazy {
		Retrofit.Builder().baseUrl(ServerConfig.getWorkOrderBaseUrl()).addConverterFactory(GsonConverterFactory.create())
			.client(RetrofitManager.client)
			.build()
	}

	// Retrofit 单例
	val retrofit: Retrofit by lazy {
		Retrofit.Builder().baseUrl(ServerConfig.getBaseUrl()).addConverterFactory(GsonConverterFactory.create())
			.client(RetrofitManager.client)
			.build()
	}

	// 各种接口
	val projectApi: ProjectApi by lazy { retrofitByWorkOrder.create(ProjectApi::class.java) }
	val videoApi: VideoApi by lazy { retrofit.create(VideoApi::class.java) }
}