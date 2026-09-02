package com.fuusy.hiddendanger.data

import com.google.gson.annotations.SerializedName

/** 业务服文件上传响应（载体：MinIO/Nginx，如 /profile/ai/xxx.pdf） */
data class KnowledgeFileDto(
    @SerializedName("url") val url: String? = null,
    @SerializedName("filePath") val filePath: String? = null,
    @SerializedName("fileName") val fileName: String? = null,
    val fileType: String? = null
) {
    fun resolvedUrl(): String = url?.trim().orEmpty().ifBlank { filePath.orEmpty().trim() }
}

/** 千文入库请求：文件已在载体上，提交可访问链接即可识别 */
data class KnowledgeIngestRequest(
    val url: String,
    val title: String? = null,
    @SerializedName("content_type") val contentType: String? = null
)

data class KnowledgeIngestResponse(
    @SerializedName("task_id") val taskId: String? = null,
    val status: String? = null,
    @SerializedName("source_url") val sourceUrl: String? = null,
    val message: String? = null
)

/** App 本地展示的上传记录 */
data class KnowledgeUploadItem(
    val id: String,
    val displayName: String,
    val sourceUrl: String,
    val status: KnowledgeUploadStatus,
    val time: String,
    val error: String? = null
)

enum class KnowledgeUploadStatus(val label: String) {
    UPLOADING("上传中…"),
    INGESTING("识别入库中…"),
    CARRIER_DONE("已上传"),
    INDEXED("已入库"),
    FAILED("失败")
}
