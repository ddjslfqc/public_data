package com.fuusy.common.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "video_cache")
data class VideoCacheEntity(
    @PrimaryKey val remoteUrl: String,
    val localPath: String,
    val fileName: String,
    val downloadStatus: Int, // 0: 未下载, 1: 下载中, 2: 下载完成, 3: 下载失败
    val updateTime: Long
) 