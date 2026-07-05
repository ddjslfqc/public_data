package com.fuusy.hiddendanger.ui.model

import android.content.Intent
import com.fuusy.hiddendanger.data.OkrKeyResult
import com.fuusy.hiddendanger.data.OkrObjective
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.google.gson.Gson

object KrNavHelper {

    const val EXTRA_KR_JSON = "kr_json"

    private val gson = Gson()

    fun goalKrItem(objective: OkrObjective, kr: OkrKeyResult): GoalKrItem {
        return GoalKrItem(
            id = kr.id,
            objectiveId = objective.id,
            objectiveTitle = objective.title,
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
            approvalLabel = OkrPeriodHelper.approvalLabel(kr.approvalStatus)
        )
    }

    fun putExtra(intent: Intent, item: GoalKrItem) {
        intent.putExtra(EXTRA_KR_JSON, gson.toJson(item))
    }

    fun fromIntent(intent: Intent): GoalKrItem? {
        val json = intent.getStringExtra(EXTRA_KR_JSON) ?: return null
        return runCatching { gson.fromJson(json, GoalKrItem::class.java) }.getOrNull()
    }
}
