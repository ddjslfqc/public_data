package com.fuusy.hiddendanger.data

import com.fuusy.common.data.WorkOrderOptions
import com.fuusy.hiddendanger.ui.adapter.DynamicFormAdapter
import com.fuusy.project.workorder.OptionItemDto
import com.fuusy.project.workorder.WorkOrderOptionsDto

/** Mock 表单（对齐设计稿；选项可从 /mobile/workorder/options 动态填充） */
object WorkOrderMockForm {

    fun formItems(options: WorkOrderOptionsDto? = null): List<DynamicFormAdapter.FormItem> {
        val types = options?.hazardTypes ?: fallbackTypes()
        val depts = options?.departments ?: fallbackDepartments()
        val priorities = options?.priorities ?: fallbackPriorities()
        return listOf(
            DynamicFormAdapter.FormItem(
                key = "hiddenDangerName",
                type = DynamicFormAdapter.VIEW_TYPE_INPUT_TEXT,
                label = "工单名称",
                isRequired = true,
                placeholder = "请输入工单名称"
            ),
            DynamicFormAdapter.FormItem(
                key = "workOrderType",
                type = DynamicFormAdapter.VIEW_TYPE_SELECTOR,
                label = "类型",
                isRequired = true,
                placeholder = "请选择类型",
                options = types.map { it.toOption() }
            ),
            DynamicFormAdapter.FormItem(
                key = "responsibleDepartment",
                type = DynamicFormAdapter.VIEW_TYPE_SELECTOR,
                label = "处理人部门",
                isRequired = true,
                placeholder = "请选择处理人部门",
                options = depts.map { it.toOption() }
            ),
            DynamicFormAdapter.FormItem(
                key = "responsiblePerson",
                type = DynamicFormAdapter.VIEW_TYPE_SELECTOR,
                label = "处理人",
                isRequired = false,
                placeholder = "不指定处理人（公开抢单）",
                options = listOf(
                    DynamicFormAdapter.OptionItem("", "不指定处理人（公开抢单）")
                )
            ),
            DynamicFormAdapter.FormItem(
                key = "priority",
                type = DynamicFormAdapter.VIEW_TYPE_SELECTOR,
                label = "优先级",
                isRequired = true,
                placeholder = "请选择优先级",
                options = priorities.map { it.toOption() }
            ),
            DynamicFormAdapter.FormItem(
                key = "hiddenDangerDescription",
                type = DynamicFormAdapter.VIEW_TYPE_INPUT_TEXT,
                label = "需求说明",
                isRequired = true,
                placeholder = "请详细描述需求内容..."
            )
        )
    }

    private fun OptionItemDto.toOption() =
        DynamicFormAdapter.OptionItem(value = value, label = label)

    private fun fallbackTypes() = WorkOrderOptions.workOrderTypes.map {
        OptionItemDto(value = it, label = it)
    }

    private fun fallbackDepartments() = WorkOrderOptions.handlerDepartments.mapIndexed { index, name ->
        OptionItemDto(value = (index + 1).toString(), label = name)
    }

    private fun fallbackPriorities() = WorkOrderOptions.priorities.map {
        OptionItemDto(value = WorkOrderOptions.priorityLabelToCode(it), label = it)
    }
}
