package com.fuusy.hiddendanger.data

import com.fuusy.hiddendanger.ui.adapter.DynamicFormAdapter
import com.fuusy.project.workorder.OptionItemDto
import com.fuusy.project.workorder.WorkOrderOptionsDto

/** 创建工单表单，字段与 workorder-api.md §3.4 create 请求体对齐 */
object WorkOrderMockForm {

    fun formItems(options: WorkOrderOptionsDto? = null): List<DynamicFormAdapter.FormItem> {
        val types = options?.hazardTypes.orEmpty()
        val depts = options?.departments.orEmpty()
        val priorities = options?.priorities.orEmpty()
        val levels = options?.hazardLevels.orEmpty()
        return listOf(
            textField("hiddenDangerName", "工单名称", required = true, placeholder = "请输入工单名称"),
            selector("workOrderType", "类别", required = true, types, "请选择类别（typeCode）"),
            selector("responsibleDepartment", "处理部门", required = true, depts, "请选择处理部门（responsibleDept）"),
            selector(
                "responsiblePerson",
                "处理人",
                required = false,
                listOf(OptionItemDto("", "不指定处理人（公开抢单）")),
                "不指定处理人（公开抢单）"
            ),
            selector("priority", "优先级", required = false, priorities, "请选择优先级（P1/P2/P3）"),
            selector("controlLevel", "隐患等级", required = true, levels, "请选择隐患等级"),
            textField("hiddenDangerDescription", "需求说明", required = true, placeholder = "请详细描述需求（brief）"),
            textField(
                "expectedCompletionTime",
                "期望完成时间",
                required = false,
                placeholder = "yyyy-MM-dd HH:mm:ss"
            ),
            textField("reasonAnalysis", "原因", required = false, placeholder = "reason（选填）"),
            textField("hazardConsequence", "后果", required = false, placeholder = "consequences（选填）"),
            textField("treatmentRequirement", "整改方案", required = false, placeholder = "rectificationScheme（选填）"),
            textField("unitSystem", "设备", required = false, placeholder = "device（选填）")
        )
    }

    private fun textField(
        key: String,
        label: String,
        required: Boolean,
        placeholder: String
    ) = DynamicFormAdapter.FormItem(
        key = key,
        type = DynamicFormAdapter.VIEW_TYPE_INPUT_TEXT,
        label = label,
        isRequired = required,
        placeholder = placeholder
    )

    private fun selector(
        key: String,
        label: String,
        required: Boolean,
        items: List<OptionItemDto>,
        placeholder: String
    ) = DynamicFormAdapter.FormItem(
        key = key,
        type = DynamicFormAdapter.VIEW_TYPE_SELECTOR,
        label = label,
        isRequired = required,
        placeholder = placeholder,
        options = items.map { DynamicFormAdapter.OptionItem(value = it.value, label = it.label) }
    )
}
