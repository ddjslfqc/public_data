package com.fuusy.project.uwb

enum class UwbLogDirection { RX, TX, INFO }

data class UwbLogEntry(
    val id: Long,
    val timestamp: Long,
    val direction: UwbLogDirection,
    val title: String,
    val detail: String,
    val hex: String? = null
)
