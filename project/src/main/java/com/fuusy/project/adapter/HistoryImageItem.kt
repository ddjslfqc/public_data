package com.fuusy.project.adapter

data class HistoryImageItem(
    val fileName: String,
    val fileSize: String,
    val fileUrl: String,
    val isVideo: Boolean = false,
    var downloadProgress: Int = 0,
    var downloadedSize: Long = 0,
    var totalSize: Long = 0,
    var isDownloading: Boolean = false
) 