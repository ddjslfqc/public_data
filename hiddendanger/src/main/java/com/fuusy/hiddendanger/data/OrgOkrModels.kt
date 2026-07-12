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

/** 组织 OKR 对齐树平铺行（带层级缩进） */
data class OrgOkrAlignmentItem(
    val objective: OkrObjective,
    val ownerName: String,
    val deptName: String?,
    val depth: Int
)

object OrgOkrMapper {

    private val PLACEHOLDER_NAME = Regex("^用户-?\\d+$")

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

    fun groupByUser(
        objectives: List<OkrAlignmentObjective>,
        directoryNames: Map<Long, String> = emptyMap()
    ): List<OrgOkrUserSummary> {
        if (objectives.isEmpty()) return emptyList()
        return objectives
            .groupBy { it.userId ?: 0L }
            .filterKeys { it > 0L }
            .map { (userId, items) ->
                val mapped = items.map { toOkrObjective(it) }
                val avg = items.map { it.progress }.average().toInt()
                OrgOkrUserSummary(
                    userId = userId,
                    ownerName = resolveOwnerName(userId, items, directoryNames),
                    deptName = resolveDeptName(items),
                    objectiveCount = items.size,
                    avgProgress = avg,
                    objectives = mapped
                )
            }
            .sortedWith(compareBy({ it.deptName.orEmpty() }, { it.ownerName }))
    }

    fun filterByKeyword(users: List<OrgOkrUserSummary>, keyword: String): List<OrgOkrUserSummary> {
        val query = keyword.trim()
        if (query.isEmpty()) return users
        return users.filter { user ->
            user.ownerName.contains(query, ignoreCase = true) ||
                user.deptName?.contains(query, ignoreCase = true) == true
        }
    }

    /** 将对齐关系展开为带缩进的平铺列表（根目标 → 子目标） */
    fun buildAlignmentRows(
        objectives: List<OkrAlignmentObjective>,
        directoryNames: Map<Long, String> = emptyMap(),
        keyword: String = ""
    ): List<OrgOkrAlignmentItem> {
        if (objectives.isEmpty()) return emptyList()

        data class Meta(
            val objective: OkrObjective,
            val ownerName: String,
            val deptName: String?
        )

        val metas = objectives.map { item ->
            Meta(
                objective = toOkrObjective(item),
                ownerName = resolveOwnerName(item.userId ?: 0L, listOf(item), directoryNames),
                deptName = item.deptName?.takeIf { it.isNotBlank() }
            )
        }
        val krIdsInSet = metas.flatMap { meta ->
            meta.objective.keyResults.orEmpty().map { it.id }
        }.toSet()

        fun isRoot(meta: Meta): Boolean {
            val parentKrId = meta.objective.parentKrId ?: return true
            return parentKrId !in krIdsInSet
        }

        fun childrenOf(parent: Meta): List<Meta> {
            val parentKrIds = parent.objective.keyResults.orEmpty().map { it.id }.toSet()
            if (parentKrIds.isEmpty()) return emptyList()
            return metas.filter { meta ->
                meta.objective.parentKrId != null && meta.objective.parentKrId in parentKrIds
            }.sortedWith(compareBy({ it.deptName.orEmpty() }, { it.ownerName }))
        }

        val rows = mutableListOf<OrgOkrAlignmentItem>()
        val visited = mutableSetOf<Long>()

        fun walk(meta: Meta, depth: Int) {
            if (!visited.add(meta.objective.id)) return
            rows.add(
                OrgOkrAlignmentItem(
                    objective = meta.objective,
                    ownerName = meta.ownerName,
                    deptName = meta.deptName,
                    depth = depth
                )
            )
            childrenOf(meta).forEach { walk(it, depth + 1) }
        }

        metas.filter { isRoot(it) }
            .sortedWith(compareBy({ it.deptName.orEmpty() }, { it.ownerName }))
            .forEach { walk(it, 0) }

        metas.filter { meta -> meta.objective.id !in visited }
            .forEach { walk(it, 0) }

        return filterAlignmentRows(rows, keyword)
    }

    fun filterAlignmentRows(items: List<OrgOkrAlignmentItem>, keyword: String): List<OrgOkrAlignmentItem> {
        val query = keyword.trim()
        if (query.isEmpty()) return items
        return items.filter { item ->
            item.ownerName.contains(query, ignoreCase = true) ||
                item.deptName?.contains(query, ignoreCase = true) == true ||
                item.objective.title.contains(query, ignoreCase = true) ||
                item.objective.parentKr?.title?.contains(query, ignoreCase = true) == true
        }
    }

    private fun resolveOwnerName(
        userId: Long,
        items: List<OkrAlignmentObjective>,
        directoryNames: Map<Long, String>
    ): String {
        directoryNames[userId]?.takeIf { isDisplayName(it) }?.let { return it }
        items.mapNotNull { it.ownerName }
            .firstOrNull { isDisplayName(it) }
            ?.let { return it }
        return "成员 $userId"
    }

    private fun resolveDeptName(items: List<OkrAlignmentObjective>): String? =
        items.mapNotNull { it.deptName?.takeIf { it.isNotBlank() } }.firstOrNull()

    private fun isDisplayName(name: String): Boolean =
        name.isNotBlank() && !PLACEHOLDER_NAME.matches(name)
}
