package com.fuusy.project.bean

import java.io.Serializable

data class AiAlarmRecord(
    val address: String,
    val appendtime: String,
    val device: String,
    val id: String,
    val image: String,
    val ip: String,
    val item: String,
    val itemName: String,
    val type: String
) : Serializable