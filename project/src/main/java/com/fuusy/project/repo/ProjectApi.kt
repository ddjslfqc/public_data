package com.fuusy.project.repo

import com.fuusy.common.network.BaseResp
import com.fuusy.project.bean.ProjectItem
import retrofit2.http.POST

/** 非工单模块接口；工单见 [com.fuusy.project.workorder.WorkOrderApi]（workorder-api.md） */
interface ProjectApi {
    @POST("app/getItemList")
    suspend fun getItemList(): BaseResp<List<ProjectItem>>
}
