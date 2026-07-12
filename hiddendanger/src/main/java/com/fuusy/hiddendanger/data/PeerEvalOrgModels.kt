package com.fuusy.hiddendanger.data

/** GET /mobile/okr/peer-eval/org/overview */
data class PeerEvalOrgOverviewResponse(
    val period: String,
    val deptId: Long? = null,
    val totalUserCount: Int = 0,
    val reviewPrepCompletedCount: Int = 0,
    val evalPendingTotal: Int = 0,
    val evalCompletedTotal: Int = 0,
    val users: List<PeerEvalOrgUserItem> = emptyList()
)

data class PeerEvalOrgUserItem(
    val userId: Long,
    val nickName: String? = null,
    val deptName: String? = null,
    val deptId: Long? = null,
    val reviewPrepCompleted: Boolean = false,
    val collaboratorCount: Int = 0,
    val evalPendingCount: Int = 0,
    val evalCompletedCount: Int = 0,
    val receivedCount: Int = 0,
    val receivedAverageScore: Double? = null
) {
    fun displayName(): String =
        nickName?.takeIf { it.isNotBlank() } ?: "成员 $userId"

    fun receivedSummary(): String {
        if (receivedCount <= 0) return "暂无收到评价"
        val score = receivedAverageScore ?: 0.0
        return "收到 $receivedCount 人 · ${String.format("%.1f", score)} 分"
    }
}

object PeerEvalOrgMapper {

    fun filterByKeyword(users: List<PeerEvalOrgUserItem>, keyword: String): List<PeerEvalOrgUserItem> {
        val query = keyword.trim()
        if (query.isEmpty()) return users
        return users.filter { user ->
            user.displayName().contains(query, ignoreCase = true) ||
                user.deptName?.contains(query, ignoreCase = true) == true
        }
    }

    fun departmentsFrom(users: List<PeerEvalOrgUserItem>): List<OkrDepartment> =
        users.mapNotNull { user ->
            val id = user.deptId ?: return@mapNotNull null
            val name = user.deptName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to name
        }
            .distinctBy { it.first }
            .map { (id, name) -> OkrDepartment(id = id, name = name) }
            .sortedBy { it.name }

    fun filterByDeptId(users: List<PeerEvalOrgUserItem>, deptId: Long?): List<PeerEvalOrgUserItem> {
        if (deptId == null) return users
        return users.filter { it.deptId == deptId }
    }
}
