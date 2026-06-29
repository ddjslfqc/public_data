package com.fuusy.hiddendanger.data

data class SimpleAttachment(
    val fileName: String,
    val fileSize: String,
    val fileUrl: String, // 直接是图片/视频地址
    val isVideo: Boolean,
    val downloadProgress: Int = 0, // 下载进度 0-100
    val isDownloading: Boolean = false, // 是否正在下载
    val totalSize: Long = 0, // 文件总大小（字节）
    val downloadedSize: Long = 0 // 已下载大小（字节）
) 