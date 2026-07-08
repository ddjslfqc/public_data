package com.fuusy.hiddendanger.ui.model

import android.content.Intent
import com.fuusy.hiddendanger.data.OkrKeyResult
import com.fuusy.hiddendanger.data.OkrKrComment
import com.fuusy.hiddendanger.data.OkrKrDetailResponse
import com.fuusy.hiddendanger.data.OkrObjective
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.google.gson.Gson

object KrNavHelper {

    const val EXTRA_KR_JSON = "kr_json"

    private val gson = Gson()

    fun goalKrItem(objective: OkrObjective, kr: OkrKeyResult): GoalKrItem {
        return goalKrItem(
            detail = null,
            objective = objective,
            kr = kr,
            periodEndDate = objective.endDate
        )
    }

    /** 由 KR 详情接口映射为详情页模型 */
    fun goalKrItem(detail: OkrKrDetailResponse, periodEndDate: String? = null): GoalKrItem {
        return goalKrItem(
            detail = detail,
            objective = null,
            kr = detail.toKeyResult(),
            periodEndDate = periodEndDate ?: detail.objective?.endDate
        )
    }

    private fun goalKrItem(
        detail: OkrKrDetailResponse?,
        objective: OkrObjective?,
        kr: OkrKeyResult,
        periodEndDate: String?
    ): GoalKrItem {
        val objectiveTitle = objective?.title
            ?: detail?.objective?.title
            ?: ""
        val objectiveId = objective?.id
            ?: detail?.objectiveId
            ?: kr.objectiveId
            ?: 0L
        return GoalKrItem(
            id = kr.id,
            objectiveId = objectiveId,
            objectiveTitle = objectiveTitle,
            title = kr.title,
            targetValue = kr.targetValue,
            currentValue = kr.currentValue,
            unit = kr.unit,
            weight = kr.weight,
            status = kr.status,
            approvalStatus = kr.approvalStatus,
            userId = kr.userId,
            progressApprovalStatus = kr.progressApprovalStatus,
            pendingProgressValue = kr.pendingProgressValue,
            valueLabel = OkrPeriodHelper.krValueLabel(kr),
            progressPercent = OkrPeriodHelper.krProgressPercent(kr),
            achieved = kr.achieved || kr.status == 1,
            approvalLabel = OkrPeriodHelper.approvalLabel(kr.approvalStatus),
            periodEndDate = periodEndDate
        )
    }

    private fun OkrKrDetailResponse.toKeyResult(): OkrKeyResult = OkrKeyResult(
        id = id,
        objectiveId = objectiveId,
        title = title,
        targetValue = targetValue,
        weight = weight,
        currentValue = currentValue,
        unit = unit,
        status = status,
        approvalStatus = approvalStatus,
        sortOrder = sortOrder,
        userId = userId,
        progressApprovalStatus = progressApprovalStatus,
        pendingProgressValue = pendingProgressValue,
        achieved = status == 1,
        attachments = attachments,
        comments = comments
    )

    fun putExtra(intent: Intent, item: GoalKrItem) {
        intent.putExtra(EXTRA_KR_JSON, gson.toJson(item))
    }

    fun fromIntent(intent: Intent): GoalKrItem? {
        val json = intent.getStringExtra(EXTRA_KR_JSON) ?: return null
        return runCatching { gson.fromJson(json, GoalKrItem::class.java) }.getOrNull()
    }

    /** 评论收件箱跳转时的兜底数据（后续详情页会尝试补全） */
    fun fromComment(comment: OkrKrComment): GoalKrItem = GoalKrItem(
        id = comment.krId,
        title = comment.krTitle?.takeIf { it.isNotBlank() } ?: "KR #${comment.krId}",
        userId = comment.krUserId,
        valueLabel = "—",
        progressPercent = 0
    )
}
