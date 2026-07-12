package com.fuusy.hiddendanger.data

/** 组织视角 · 团队成员（OKR + 复盘合并） */
data class OrgTeamMemberItem(
    val userId: Long,
    val ownerName: String,
    val deptName: String?,
    val deptId: Long?,
    val objectiveCount: Int,
    val avgProgress: Int,
    val reviewPrepCompleted: Boolean,
    val objectives: List<OkrObjective> = emptyList()
)

object OrgTeamMapper {

    fun merge(
        peerUsers: List<PeerEvalOrgUserItem>,
        okrUsers: List<OrgOkrUserSummary>
    ): List<OrgTeamMemberItem> {
        val okrMap = okrUsers.associateBy { it.userId }
        return peerUsers.map { peer ->
            val okr = okrMap[peer.userId]
            OrgTeamMemberItem(
                userId = peer.userId,
                ownerName = peer.displayName(),
                deptName = peer.deptName,
                deptId = peer.deptId,
                objectiveCount = okr?.objectiveCount ?: 0,
                avgProgress = okr?.avgProgress ?: 0,
                reviewPrepCompleted = peer.reviewPrepCompleted,
                objectives = okr?.objectives.orEmpty()
            )
        }.sortedWith(compareBy({ it.deptName.orEmpty() }, { it.ownerName }))
    }

    fun filterByKeyword(members: List<OrgTeamMemberItem>, keyword: String): List<OrgTeamMemberItem> {
        val query = keyword.trim()
        if (query.isEmpty()) return members
        return members.filter { member ->
            member.ownerName.contains(query, ignoreCase = true) ||
                member.deptName?.contains(query, ignoreCase = true) == true
        }
    }

    fun departmentsFrom(members: List<OrgTeamMemberItem>): List<OkrDepartment> =
        members.mapNotNull { member ->
            val id = member.deptId ?: return@mapNotNull null
            val name = member.deptName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to name
        }
            .distinctBy { it.first }
            .map { (id, name) -> OkrDepartment(id = id, name = name) }
            .sortedBy { it.name }
}
