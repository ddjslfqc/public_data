package com.fuusy.hiddendanger.data

import retrofit2.http.GET
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.DELETE
import retrofit2.http.Path
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.Part
import okhttp3.RequestBody

interface WorkOrderApiService {
    @GET("/init")
    suspend fun getFormStructure(): FormStructureResponse

    @POST("/work/add")
    suspend fun saveWorkOrder(@Body workOrder: Map<String, @JvmSuppressWildcards Any>): retrofit2.Response<Any>

    @POST("/work/update")
    suspend fun updateWorkOrder(@Body workOrder: Map<String, @JvmSuppressWildcards Any>): retrofit2.Response<Any>

    @DELETE("/work/{id}")
    suspend fun deleteWorkOrderById(@Path("id") id: String): retrofit2.Response<Any>

    @Multipart
    @POST("/work/upload")
    suspend fun uploadAttachment(
        @Part file: MultipartBody.Part,
        @Part("fileName") fileName: RequestBody,
        @Part("fileType") fileType: RequestBody
    ): retrofit2.Response<UploadResponse>
}