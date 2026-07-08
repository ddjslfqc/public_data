package com.fuusy.hiddendanger.ui

import android.graphics.Color
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.ui.model.ArchiveDistributionItem
import com.fuusy.hiddendanger.ui.model.ArchiveEvaluationItem
import com.fuusy.hiddendanger.ui.model.EvaluationRecordItem
import com.fuusy.hiddendanger.ui.model.EvaluationRecordSummary
import com.fuusy.project.workorder.WorkOrderArchiveTagDto
import com.fuusy.project.workorder.WorkOrderEvaluationItemDto
import com.fuusy.project.workorder.WorkOrderEvaluationSummaryDto

object WorkOrderEvaluationUiMapper {

    private val avatarColors = intArrayOf(
        Color.parseColor("#1365EC"),
        Color.parseColor("#EA9300"),
        Color.parseColor("#00AA60"),
        Color.parseColor("#6A2DF6"),
        Color.parseColor("#E5484D")
    )

    fun toSummary(dto: WorkOrderEvaluationSummaryDto): EvaluationRecordSummary =
        EvaluationRecordSummary(
            averageRating = dto.averageRating ?: "--",
            positiveRate = dto.positiveRate ?: "0%",
            totalCount = dto.totalCount
        )

    fun toRecordItem(dto: WorkOrderEvaluationItemDto): EvaluationRecordItem {
        val name = dto.reviewerName?.takeIf { it.isNotBlank() } ?: "匿名"
        return EvaluationRecordItem(
            id = dto.id ?: dto.workOrderId ?: "",
            reviewerName = name,
            reviewerInitial = name.firstOrNull()?.toString() ?: "?",
            avatarColor = avatarColors[kotlin.math.abs(name.hashCode()) % avatarColors.size],
            department = dto.department?.takeIf { it.isNotBlank() } ?: "--",
            date = dto.date ?: "",
            rating = (dto.score ?: 0).toFloat(),
            content = dto.content?.takeIf { it.isNotBlank() } ?: dto.tag ?: "",
            workOrderTitle = dto.workOrderTitle ?: dto.workOrderNo ?: "",
            workOrderId = dto.workOrderId
        )
    }

    fun toArchiveDistribution(dto: WorkOrderArchiveTagDto): ArchiveDistributionItem =
        ArchiveDistributionItem(
            label = dto.label ?: "其他",
            count = dto.count
        )

    fun toArchiveEvaluation(dto: WorkOrderEvaluationItemDto): ArchiveEvaluationItem {
        val name = dto.reviewerName?.takeIf { it.isNotBlank() } ?: "匿名"
        val score = dto.score ?: 0
        val tagStyle = tagStyleForScore(score)
        val meta = buildString {
            dto.workOrderNo?.let { append(it) }
            if (dto.date != null) {
                if (isNotEmpty()) append(" · ")
                append(dto.date)
            }
        }
        return ArchiveEvaluationItem(
            reviewerName = name,
            reviewerInitial = name.firstOrNull()?.toString() ?: "?",
            avatarColor = avatarColors[kotlin.math.abs(name.hashCode()) % avatarColors.size],
            rating = score.toFloat(),
            tag = dto.tag,
            tagTextColor = tagStyle.first,
            tagBackgroundRes = tagStyle.second,
            content = dto.content?.takeIf { it.isNotBlank() } ?: dto.tag ?: "",
            meta = meta.ifBlank { "--" },
            workOrderId = dto.workOrderId
        )
    }

    private fun tagStyleForScore(score: Int): Pair<Int, Int> = when {
        score >= 4 -> Color.parseColor("#00AA60") to R.drawable.bg_archive_tag_good
        score == 3 -> Color.parseColor("#686D79") to R.drawable.bg_archive_tag_dept
        else -> Color.parseColor("#EA9300") to R.drawable.bg_archive_tag_warn
    }
}
