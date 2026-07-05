package com.fuusy.hiddendanger.ui.model

import com.fuusy.hiddendanger.data.OkrKrComment

data class OkrInboxGroup(
    val krId: Long,
    val krTitle: String?,
    val preview: OkrKrComment,
    val inboxCount: Int
)

object OkrInboxGrouper {
    fun group(list: List<OkrKrComment>): List<OkrInboxGroup> {
        return list.groupBy { it.krId }
            .map { (_, comments) ->
                val sorted = comments.sortedByDescending { it.createTime.orEmpty() }
                val latest = sorted.first()
                OkrInboxGroup(
                    krId = latest.krId,
                    krTitle = latest.krTitle,
                    preview = latest,
                    inboxCount = comments.size
                )
            }
            .sortedByDescending { it.preview.createTime.orEmpty() }
    }
}
