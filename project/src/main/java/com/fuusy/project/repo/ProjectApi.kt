package com.fuusy.project.repo

import com.fuusy.common.network.BaseResp
import com.fuusy.project.bean.ProjectItem
import retrofit2.http.POST
import retrofit2.http.GET
import com.fuusy.project.bean.WorkOrderListResponse

interface ProjectApi {
    @POST("app/getItemList")
    suspend fun getItemList(): BaseResp<List<ProjectItem>>

    @GET("/work/list")
    suspend fun getWorkOrderList(): WorkOrderListResponse
}