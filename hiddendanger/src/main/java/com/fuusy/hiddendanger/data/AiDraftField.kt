package com.fuusy.hiddendanger.data

/** 草稿表单字段（工单 / 日报） */
data class AiDraftField(
    val key: String,
    val label: String,
    val value: String = "",
    val required: Boolean = false,
    val multiline: Boolean = false,
    /** options map 中的 key：types / departments / users / priorities */
    val optionsKey: String? = null
)

object AiDraftFieldDefs {
    val workOrder: List<AiDraftField> = listOf(
        AiDraftField("title", "工单标题", required = true),
        AiDraftField("brief", "任务描述", required = true, multiline = true),
        AiDraftField("typeCode", "工单类型", required = true, optionsKey = "types"),
        AiDraftField("responsibleDept", "负责部门", required = true, optionsKey = "departments"),
        AiDraftField("rectificationPerson", "负责人", optionsKey = "users"),
        AiDraftField("priority", "优先级", optionsKey = "priorities"),
        AiDraftField("project", "关联项目"),
        AiDraftField("expectedCompletionTime", "预计完成时间")
    )

    /** 日报：正文 + 日期（提交人即当前登录用户，无需推送人字段） */
    val daily: List<AiDraftField> = listOf(
        AiDraftField("content", "日报正文", required = true, multiline = true),
        AiDraftField("reportDate", "日期", required = true)
    )

    val weekly: List<AiDraftField> = listOf(
        AiDraftField("userName", "提交人", required = true),
        AiDraftField("weekStart", "周开始日期", required = true),
        AiDraftField("weekEnd", "周结束日期", required = true),
        AiDraftField("summary", "本周工作总结", required = true, multiline = true),
        AiDraftField("issues", "遇到的问题/需要的支持", multiline = true)
    )

    val monthly: List<AiDraftField> = listOf(
        AiDraftField("userName", "提交人", required = true),
        AiDraftField("month", "月份", required = true),
        AiDraftField("achievements", "本月工作成果/突破", required = true, multiline = true),
        AiDraftField("issues", "遇到的问题/需要的支持", multiline = true)
    )
}
