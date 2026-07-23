package com.fuusy.hiddendanger.ui.model

import com.fuusy.common.network.UserIdProvider
import com.fuusy.hiddendanger.data.OkrPeriodHelper

object KrProgressHelper {

    /** userId 为空时视为本人负责（创建目标时未指派他人） */
    fun isKrOwner(item: GoalKrItem): Boolean {
        val me = UserIdProvider.current() ?: return true
        return item.userId == null || item.userId == me
    }

    fun canUpdateProgress(item: GoalKrItem): Boolean {
        if (!isKrOwner(item)) return false
        if (OkrPeriodHelper.isPeriodEnded(item.periodEndDate)) return false
        if (item.achieved || item.status == 1) return false
        // approvalStatus 为空时不拦截（兼容旧数据/部分接口未回传）
        if (item.approvalStatus != null && item.approvalStatus != 1) return false
        if (item.progressApprovalStatus == 0) return false
        return true
    }

    fun updateBlockedReason(item: GoalKrItem): String? = when {
        OkrPeriodHelper.isPeriodEnded(item.periodEndDate) -> "该目标周期已结束，无法更新进度"
        item.achieved || item.status == 1 -> "该 KR 已完成"
        item.approvalStatus == 0 -> item.pendingApproverHint ?: "等待审批 KR，通过后可更新进度"
        item.approvalStatus == 2 -> "KR 已被拒绝，无法更新进度"
        item.progressApprovalStatus == 0 -> progressPendingStatusHint(item)
        !isKrOwner(item) -> "仅 KR 负责人可更新进度"
        else -> null
    }

    fun progressStatusLabel(item: GoalKrItem): String? =
        when {
            item.progressApprovalStatus == 0 -> progressPendingStatusHint(item)
            else -> OkrPeriodHelper.progressApprovalLabel(item.progressApprovalStatus)
        }

    fun progressSubmitMessage(item: GoalKrItem): String {
        val name = item.nextProgressApproverName
        val role = item.nextProgressApproverRoleLabel
        return if (!name.isNullOrBlank() && !role.isNullOrBlank()) {
            "提交后将由 $name（$role）审批，通过后进度才会生效"
        } else if (!name.isNullOrBlank()) {
            "提交后将由 $name 审批，通过后进度才会生效"
        } else {
            "提交后将进入审批流程，通过后进度才会生效"
        }
    }

    fun progressSubmitSuccessMessage(item: GoalKrItem): String =
        when {
            !item.nextProgressApproverName.isNullOrBlank() && !item.nextProgressApproverRoleLabel.isNullOrBlank() ->
                "进度已提交，等待 ${item.nextProgressApproverName}（${item.nextProgressApproverRoleLabel}）审批"
            !item.nextProgressApproverName.isNullOrBlank() ->
                "进度已提交，等待 ${item.nextProgressApproverName} 审批"
            else -> "进度已提交，等待审批"
        }

    private fun progressPendingStatusHint(item: GoalKrItem): String =
        when {
            !item.pendingProgressApproverName.isNullOrBlank() && !item.pendingProgressApproverRoleLabel.isNullOrBlank() ->
                "进度更新已提交，等待 ${item.pendingProgressApproverName}（${item.pendingProgressApproverRoleLabel}）审批"
            !item.pendingProgressApproverName.isNullOrBlank() ->
                "进度更新已提交，等待 ${item.pendingProgressApproverName} 审批"
            else -> "进度更新已提交，等待审批"
        }
}
