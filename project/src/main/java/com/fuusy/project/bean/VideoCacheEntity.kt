package com.fuusy.project.bean

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_cache")
data class VideoCacheEntity(
    @PrimaryKey val remoteUrl: String,  // 远程视频URL作为主键
    val localPath: String,              // 本地文件路径
    val fileName: String,               // 文件名
    val downloadStatus: Int,            // 下载状态：0=未开始，1=下载中，2=下载完成，3=下载失败
    val fileSize: Long = 0,             // 文件大小
    val downloadProgress: Int = 0,      // 下载进度 0-100
    val updateTime: Long = System.currentTimeMillis() // 更新时间
) 