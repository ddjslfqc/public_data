package com.fuusy.hiddendanger.ui.album

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class AlbumMediaItem(
    val id: String,
    val path: String,
    val type: MediaType, // IMAGE, VIDEO
    val date: String, // yyyy-MM-dd
    val aiTag: Boolean = false,
    val duration: Long = 0L // 视频时长，图片为0
) : Parcelable {
    enum class MediaType { IMAGE, VIDEO }
} 