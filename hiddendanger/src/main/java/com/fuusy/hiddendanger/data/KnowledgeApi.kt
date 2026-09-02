package com.fuusy.hiddendanger.data

import com.fuusy.common.network.BaseResp
import com.google.gson.annotations.SerializedName
import okhttp3.MultipartBody
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

/**
 * 知识库文件载体：业务服务器文件存储（与工单附件同源 OSS/Nginx）。
 * 路径需后端提供；若未部署则 Repository 会尝试 common/upload 兜底。
 */
interface KnowledgeApi {

    @Multipart
    @POST("mobile/knowledge/upload")
    suspend fun upload(@Part file: MultipartBody.Part): BaseResp<KnowledgeFileDto>

    /** RuoYi 系常见通用上传（url 可能在根节点） */
    @Multipart
    @POST("common/upload")
    suspend fun uploadCommon(@Part file: MultipartBody.Part): CommonUploadResp
}

/** RuoYi common/upload 响应：url 可能在 data 内，也可能在根上 */
data class CommonUploadResp(
    @SerializedName("code") val code: Int = -1,
    @SerializedName("msg") val msg: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    @SerializedName("data") val data: KnowledgeFileDto? = null
) {
    val isSuccess: Boolean get() = code == 0 || code == 200

    fun toFileDto(): KnowledgeFileDto? {
        if (data != null && data.resolvedUrl().isNotBlank()) return data
        val u = url?.trim().orEmpty()
        if (u.isBlank()) return null
        return KnowledgeFileDto(url = u, fileName = fileName, filePath = u)
    }
}
