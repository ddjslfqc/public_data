package com.fuusy.hiddendanger.data

/**
 * 360 互评 Mock 数据：后端接口未就绪时保证闭环可演示。
 * 后端实现 [OkrApi.getPeerEvalReceived] / [OkrApi.getPeerEvalSubmission] 后 App 将自动切到真实数据。
 */
object PeerEvalMockData {

    fun received(period: String): PeerEvalReceivedResponse {
        val breakdown = PeerEvalTemplate.items.map { item ->
            PeerEvalScoreBreakdownItem(
                itemId = item.id,
                itemTitle = item.title,
                averageScore = mockAverageFor(item.id)
            )
        }
        return PeerEvalReceivedResponse(
            period = period,
            evaluatorCount = 5,
            averageScore = 4.3,
            publishedAt = "2026-07-06",
            scoreBreakdown = breakdown,
            highlights = listOf(
                "协作积极，项目推进中主动补位",
                "沟通清晰，问题响应及时",
                "交付质量稳定，联调配合顺畅"
            ),
            suggestions = listOf(
                "建议更早同步跨部门风险",
                "文档沉淀可以更完善一些"
            )
        )
    }

    fun sentDetail(
        period: String,
        targetUserId: Long,
        targetUserName: String?,
        deptName: String?
    ): PeerEvalSubmissionDetail {
        val scores = PeerEvalTemplate.items.map { item ->
            PeerEvalScoreItem(itemId = item.id, score = mockSentScoreFor(item.id))
        }
        return PeerEvalSubmissionDetail(
            submissionId = 1000L + targetUserId,
            period = period,
            targetUserId = targetUserId,
            targetUserName = targetUserName,
            deptName = deptName,
            scores = scores,
            highlight = "本季度合作顺畅，关键节点都能及时响应。",
            suggestion = "建议大型需求评审再早一点拉齐相关方。",
            averageScore = scores.map { it.score }.average(),
            submittedAt = "2026-07-05 14:30:00"
        )
    }

    private fun mockAverageFor(itemId: String): Double = when (itemId) {
        "proactive_collab" -> 4.6
        "team_awareness" -> 4.2
        "delivery_timeliness" -> 4.4
        "delivery_quality" -> 4.3
        "communication_clarity" -> 4.5
        "issue_handling" -> 4.1
        "responsibility" -> 4.4
        "problem_solving" -> 4.2
        else -> 4.0
    }

    private fun mockSentScoreFor(itemId: String): Int = when (itemId) {
        "proactive_collab" -> 5
        "team_awareness" -> 4
        "delivery_timeliness" -> 5
        "delivery_quality" -> 4
        "communication_clarity" -> 5
        "issue_handling" -> 4
        "responsibility" -> 5
        "problem_solving" -> 4
        else -> 4
    }
}
