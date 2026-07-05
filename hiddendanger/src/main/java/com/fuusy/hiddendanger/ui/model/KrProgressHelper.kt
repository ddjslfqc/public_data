package com.fuusy.hiddendanger.ui.model

import com.fuusy.common.network.UserIdProvider

object KrProgressHelper {

    /** userId 为空时视为本人负责（创建目标时未指派他人） */
    fun isKrOwner(item: GoalKrItem): Boolean {
        val me = UserIdProvider.current() ?: return true
        return item.userId == null || item.userId == me
    }

    fun canUpdateProgress(item: GoalKrItem): Boolean {
        if (!isKrOwner(item)) return false
        if (item.achieved || item.status == 1) return false
        if (item.approvalStatus != 1) return false
        if (item.progressApprovalStatus == 0) return false
        return true
    }

    fun updateBlockedReason(item: GoalKrItem): String? = when {
        item.achieved || item.status == 1 -> "该 KR 已完成"
        item.approvalStatus == 0 -> "等待目标创建人审批 KR，通过后可更新进度"
        item.approvalStatus == 2 -> "KR 已被拒绝，无法更新进度"
        item.progressApprovalStatus == 0 -> "进度更新已提交，等待目标创建人审批"
        !isKrOwner(item) -> "仅 KR 负责人可更新进度"
        else -> null
    }

    fun progressStatusLabel(item: GoalKrItem): String? =
        com.fuusy.hiddendanger.data.OkrPeriodHelper.progressApprovalLabel(item.progressApprovalStatus)
}
