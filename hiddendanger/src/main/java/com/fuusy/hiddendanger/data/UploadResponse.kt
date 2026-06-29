package com.fuusy.hiddendanger.data
 
data class UploadResponse(
    val code: Int,
    val message: String,
    val data: String? // 上传后返回的文件url
) 