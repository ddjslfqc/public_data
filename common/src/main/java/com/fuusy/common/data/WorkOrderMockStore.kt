package com.fuusy.common.data

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 内存 Mock 工单仓库（对齐设计稿 layer 17:3403 / PRD workorder_list_new.html）
 */
object WorkOrderMockStore {

    private val orders = mutableListOf<WorkOrderItem>()
    private var idSeq = 100L

    init {
        orders.addAll(defaultOrders())
    }

    fun all(): List<WorkOrderItem> = orders.toList()

    fun findById(id: String): WorkOrderItem? = orders.find { it.id == id }

    fun addOrUpdate(item: WorkOrderItem) {
        val index = orders.indexOfFirst { it.id == item.id }
        if (index >= 0) {
            orders[index] = item
        } else {
            orders.add(0, item)
        }
    }

    fun updateStatus(
        id: String,
        status: WorkOrderStatus,
        patch: WorkOrderItem.() -> WorkOrderItem = { this }
    ): WorkOrderItem? {
        val index = orders.indexOfFirst { it.id == id }
        if (index < 0) return null
        val updated = orders[index].copy(status = status, nodeName = status.displayName).patch()
        orders[index] = updated
        return updated
    }

    fun remove(id: String) {
        orders.removeAll { it.id == id }
    }

    fun nextId(): String {
        idSeq += 1
        val date = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())
        return "WD$date${idSeq.toString().padStart(3, '0')}"
    }

    fun countByStatus(status: WorkOrderStatus): Int = orders.count { it.status == status }

    private fun defaultOrders(): List<WorkOrderItem> = listOf(
        // 待提交
        mock(
            id = "WD202606180001",
            name = "客户门户体验优化需求",
            desc = "客户反馈现有门户操作路径过长，需简化登录后首页到核心功能的跳转流程...",
            type = "软件研发",
            user = "张三",
            dept = "软件研发部",
            handler = "王健",
            time = "2026-06-18 09:30",
            priority = "P1",
            status = WorkOrderStatus.DRAFT,
            project = "A项目",
            expected = "2026-06-20 18:00"
        ),
        mock(
            id = "WD202606130008",
            name = "快捷操作组件开发",
            desc = "首页快捷操作区需支持自定义配置，提升一线人员操作效率。",
            type = "UI建模",
            user = "张三",
            dept = "产品部",
            handler = "公共",
            time = "2026-06-13 11:00",
            priority = "P1",
            status = WorkOrderStatus.DRAFT,
            project = "A项目"
        ),
        // 待认领
        mock(
            id = "WD202606180002",
            name = "服务器磁盘空间告警处理",
            desc = "生产环境数据库磁盘使用率已达85%，需立即清理或扩容避免服务中断...",
            type = "软件研发",
            user = "张三",
            dept = "软件研发部",
            handler = "王健",
            time = "2026-06-18 10:00",
            priority = "P0",
            status = WorkOrderStatus.PENDING,
            project = "A项目",
            expected = "2026-06-18 18:00"
        ),
        // 已提交
        mock(
            id = "WD202606170001",
            name = "移动端App登录页UI重构",
            desc = "按照新版品牌规范重构登录页设计，包括Logo、配色、按钮样式及动效...",
            type = "UI建模",
            user = "张三",
            dept = "产品部",
            handler = "公共",
            time = "2026-06-17 14:20",
            priority = "P1",
            status = WorkOrderStatus.PENDING,
            project = "B项目"
        ),
        // 驳回
        mock(
            id = "WD202606160002",
            name = "API文档缺失Swagger注解",
            desc = "测试反馈部分接口缺少Swagger注解导致文档不完整...",
            type = "软件研发",
            user = "张三",
            dept = "软件研发部",
            handler = "王健",
            time = "2026-06-16 11:00",
            priority = "P2",
            status = WorkOrderStatus.REJECT,
            project = "C项目",
            rejectReason = "需求说明描述过于简略，请补充具体的用户场景和预期效果描述。"
        ),
        mock(
            id = "WD202606160003",
            name = "登录页UI改版",
            desc = "登录页视觉需与新版设计系统保持一致，含暗色模式适配。",
            type = "UI建模",
            user = "张三",
            dept = "产品部",
            time = "2026-06-16 09:00",
            priority = "P2",
            status = WorkOrderStatus.REJECT,
            rejectReason = "请补充各状态（加载/错误）的交互说明。"
        ),
        // 处理中
        mock(
            id = "WD202606150003",
            name = "生产环境数据同步延迟问题",
            desc = "Redis缓存与MySQL主从同步存在3-5秒延迟，需排查原因...",
            type = "软件研发",
            user = "王健",
            dept = "软件研发部",
            handler = "王健",
            time = "2026-06-15 09:00",
            priority = "P0",
            status = WorkOrderStatus.PROCESSING,
            project = "A项目"
        ),
        mock(
            id = "WD202606150004",
            name = "子工单数据库优化",
            desc = "关联子工单查询接口响应超过2s，需优化索引与分页策略。",
            type = "软件研发",
            user = "张三",
            dept = "软件研发部",
            handler = "王健",
            time = "2026-06-15 14:00",
            priority = "P1",
            status = WorkOrderStatus.PROCESSING
        ),
        mock(
            id = "WD202606140005",
            name = "Redis集群扩容",
            desc = "业务峰值期间Redis内存告警，需评估扩容方案并实施。",
            type = "售后运维",
            user = "熊洋",
            dept = "售后部",
            handler = "熊洋",
            time = "2026-06-14 16:00",
            priority = "P0",
            status = WorkOrderStatus.PROCESSING
        ),
        // 待评价
        mock(
            id = "WD202606140004",
            name = "用户权限模块上线",
            desc = "RBAC权限体系已完成开发和测试，部署至UAT环境...",
            type = "软件研发",
            user = "张三",
            dept = "软件研发部",
            handler = "王健",
            time = "2026-06-14 10:00",
            priority = "P1",
            status = WorkOrderStatus.EVAL,
            project = "D项目"
        ),
        // 已完成
        mock(
            id = "WD202606100005",
            name = "官网Banner图更换需求",
            desc = "官网首页Banner需更换为618活动主视觉，已完成上线。",
            type = "营销推广",
            user = "李阳",
            dept = "市场部",
            handler = "张家豪",
            time = "2026-06-10 15:00",
            priority = "P3",
            status = WorkOrderStatus.COMPLETED,
            project = "E项目"
        ),
        mock(
            id = "WD202606080006",
            name = "文档中心改版",
            desc = "帮助文档中心信息架构调整，搜索体验优化，已验收通过。",
            type = "产品设计",
            user = "张三",
            dept = "产品部",
            handler = "公共",
            time = "2026-06-08 11:30",
            priority = "P2",
            status = WorkOrderStatus.COMPLETED,
            project = "F项目"
        )
    )

    private fun mock(
        id: String,
        name: String,
        desc: String,
        type: String,
        user: String,
        dept: String,
        time: String,
        priority: String,
        status: WorkOrderStatus,
        handler: String = "",
        project: String? = null,
        expected: String? = null,
        rejectReason: String? = null
    ) = WorkOrderItem(
        id = id,
        hiddenDangerName = name,
        hiddenDangerDescription = desc,
        workOrderType = type,
        hiddenDangerCategory = type,
        submitUser = user,
        responsibleDepartment = dept,
        responsiblePerson = handler.ifBlank { null },
        submitTime = time,
        priority = priority,
        status = status,
        nodeName = status.displayName,
        projectName = project,
        expectedCompleteTime = expected,
        rejectionReason = rejectReason,
        rejectionUser = if (rejectReason != null) "审核员" else null,
        rejectionTime = if (rejectReason != null) time else null
    )
}
