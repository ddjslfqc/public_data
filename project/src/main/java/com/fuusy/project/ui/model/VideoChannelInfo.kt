package com.fuusy.project.ui.model

data class VideoChannelInfo(
    val channelNo: Int,
    val streamUrl: String,
    val cameraip: String = "",
    val channelId: String = "",
    val region: String = "",
    val deviceId: String = "",
    val person: String = ""
) 