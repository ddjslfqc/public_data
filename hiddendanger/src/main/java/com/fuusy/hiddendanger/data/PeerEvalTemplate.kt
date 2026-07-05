package com.fuusy.hiddendanger.data

/** Q2 协作互评打分模板（一期写死，后续可改接口下发） */
object PeerEvalTemplate {

    val items: List<PeerEvalTemplateItem> = listOf(
        PeerEvalTemplateItem(
            id = "proactive_collab",
            category = "协作配合",
            title = "主动协作",
            description = "本季度合作中，能主动配合、及时响应，不推诿扯皮",
            sortOrder = 1
        ),
        PeerEvalTemplateItem(
            id = "team_awareness",
            category = "协作配合",
            title = "团队意识",
            description = "愿意共享信息/资源，帮助团队整体目标推进",
            sortOrder = 2
        ),
        PeerEvalTemplateItem(
            id = "delivery_timeliness",
            category = "交付结果",
            title = "交付时效",
            description = "承诺的事项能按时完成，少拖延",
            sortOrder = 3
        ),
        PeerEvalTemplateItem(
            id = "delivery_quality",
            category = "交付结果",
            title = "交付质量",
            description = "交付结果符合预期，返工少、可信赖",
            sortOrder = 4
        ),
        PeerEvalTemplateItem(
            id = "communication_clarity",
            category = "沟通反馈",
            title = "沟通清晰",
            description = "信息同步及时，表达清楚，减少误解",
            sortOrder = 5
        ),
        PeerEvalTemplateItem(
            id = "issue_handling",
            category = "沟通反馈",
            title = "问题处理",
            description = "出现分歧或问题时，能理性沟通、推动解决",
            sortOrder = 6
        ),
        PeerEvalTemplateItem(
            id = "responsibility",
            category = "专业担当",
            title = "责任心",
            description = "对本职和协作事项负责，跟到底",
            sortOrder = 7
        ),
        PeerEvalTemplateItem(
            id = "problem_solving",
            category = "专业担当",
            title = "解决问题",
            description = "遇到困难能主动想办法，而不是只抛问题",
            sortOrder = 8
        )
    )

    fun formRows(): List<PeerEvalFormRow> {
        val rows = mutableListOf<PeerEvalFormRow>()
        var lastCategory: String? = null
        for (item in items.sortedBy { it.sortOrder }) {
            if (item.category != lastCategory) {
                rows += PeerEvalFormRow.Category(item.category)
                lastCategory = item.category
            }
            rows += PeerEvalFormRow.Score(item)
        }
        return rows
    }
}

data class PeerEvalTemplateItem(
    val id: String,
    val category: String,
    val title: String,
    val description: String,
    val sortOrder: Int = 0
)

sealed class PeerEvalFormRow {
    data class Category(val name: String) : PeerEvalFormRow()
    data class Score(val item: PeerEvalTemplateItem) : PeerEvalFormRow()
}
