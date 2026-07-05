package com.fuusy.project.network

import com.fuusy.common.network.ServerConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.fuusy.project.repo.ProjectApi
import com.fuusy.project.repo.VideoApi
import com.fuusy.common.network.RetrofitManager

object ApiService {
	// 与 workorder-api.md Base URL 一致，项目/工单/视频共用主站
	val retrofit: Retrofit by lazy {
		Retrofit.Builder().baseUrl(ServerConfig.getBaseUrl()).addConverterFactory(GsonConverterFactory.create())
			.client(RetrofitManager.client)
			.build()
	}

	val projectApi: ProjectApi by lazy { retrofit.create(ProjectApi::class.java) }
	val videoApi: VideoApi by lazy { retrofit.create(VideoApi::class.java) }
}