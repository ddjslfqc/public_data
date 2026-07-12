package com.fuusy.project.workorder

/** 与后端 GET /mobile/workorder/list?scope= 一致 */
object WorkOrderListScope {
    /** 组织内全部工单（底部工单 Tab） */
    const val ALL = "all"

    /** 我提报的 + 指派我处理的（首页、我相关的任务） */
    const val RELATED = "related"

    /** 我作为处理人完成的工单（完成任务页） */
    const val HANDLED_COMPLETED = "handled_completed"
}
