package com.fuusy.hiddendanger.ui.adapter

sealed class AttachmentItem {
    data class Media(val path: String, val type: MediaType, val duration: Long = 0L) : AttachmentItem()
    object AddButton : AttachmentItem()

    enum class MediaType {
        IMAGE, VIDEO
    }
} 