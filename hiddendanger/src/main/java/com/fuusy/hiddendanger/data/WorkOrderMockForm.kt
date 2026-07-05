package com.fuusy.hiddendanger.data

import com.fuusy.hiddendanger.ui.adapter.DynamicFormAdapter
import com.fuusy.common.data.WorkOrderOptions
import com.fuusy.project.workorder.OptionItemDto
import com.fuusy.project.workorder.WorkOrderOptionsDto

/** 创建工单表单，字段与 workorder-api.md §3.4 create 请求体对齐 */
object WorkOrderMockForm {

    fun formItems(
        options: WorkOrderOptionsDto? = null,
        personOptions: List<OptionItemDto>? = null
    ): List<DynamicFormAdapter.FormItem> {
        val types = options?.types.orEmpty()
        val depts = options?.departments.orEmpty()
        val priorities = options?.priorities.orEmpty()
        val handlers = personOptions ?: listOf(
            OptionItemDto(WorkOrderOptions.PUBLIC_GRAB_PERSON_ID, "不指定处理人（公开抢单）")
        )
        return listOf(
            textField("hiddenDangerName", "工单名称", required = true, placeholder = "请输入工单名称"),
            selector("workOrderType", "类别", required = true, types, "请选择类别"),
            selector("responsibleDepartment", "处理部门", required = true, depts, "请选择处理部门"),
            selector(
                "rectificationPerson",
                "处理人",
                required = false,
                handlers,
                "不指定处理人（公开抢单）"
            ),
            selector("priority", "优先级", required = false, priorities, "请选择优先级"),
            textField("project", "隶属项目", required = false, placeholder = "请先选择项目"),
            textField("hiddenDangerDescription", "需求说明", required = true, placeholder = "请详细描述需求"),
            datetimeField("expectedCompletionTime", "期望完成时间", required = false)
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

    private fun datetimeField(
        key: String,
        label: String,
        required: Boolean
    ) = DynamicFormAdapter.FormItem(
        key = key,
        type = DynamicFormAdapter.VIEW_TYPE_SELECTOR,
        label = label,
        isRequired = required,
        placeholder = "请选择期望完成时间",
        options = emptyList()
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
