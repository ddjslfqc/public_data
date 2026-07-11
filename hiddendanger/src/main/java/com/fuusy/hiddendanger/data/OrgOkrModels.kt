package com.fuusy.hiddendanger.data

/** 组织 OKR 人员汇总（由 alignment-tree 聚合） */
data class OrgOkrUserSummary(
    val userId: Long,
    val ownerName: String,
    val deptName: String?,
    val objectiveCount: Int,
    val avgProgress: Int,
    val objectives: List<OkrObjective>
)

object OrgOkrMapper {

    fun toOkrObjective(item: OkrAlignmentObjective): OkrObjective = OkrObjective(
        id = item.id,
        title = item.title,
        userId = item.userId,
        periodType = item.periodType,
        periodLabel = item.periodLabel,
        startDate = item.startDate,
        endDate = item.endDate,
        status = item.status,
        statusLabel = item.statusLabel,
        progress = item.progress,
        progressText = item.progressText,
        parentKrId = item.parentKrId,
        parentKr = item.parentKr,
        deptId = item.deptId,
        keyResults = item.keyResults
    )

    fun groupByUser(objectives: List<OkrAlignmentObjective>): List<OrgOkrUserSummary> {
        if (objectives.isEmpty()) return emptyList()
        return objectives
            .groupBy { it.userId ?: 0L }
            .filterKeys { it > 0L }
            .map { (userId, items) ->
                val mapped = items.map { toOkrObjective(it) }
                val first = items.first()
                val avg = items.map { it.progress }.average().toInt()
                OrgOkrUserSummary(
                    userId = userId,
                    ownerName = first.ownerName?.takeIf { it.isNotBlank() } ?: "用户$userId",
                    deptName = first.deptName,
                    objectiveCount = items.size,
                    avgProgress = avg,
                    objectives = mapped
                )
            }
            .sortedWith(compareBy({ it.deptName.orEmpty() }, { it.ownerName }))
    }
}
