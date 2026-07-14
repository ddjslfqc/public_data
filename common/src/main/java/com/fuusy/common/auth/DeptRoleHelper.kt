package com.fuusy.common.auth

import com.fuusy.common.utils.SpUtils

/**
 * 【临时假逻辑 · 不依赖后台】
 * 仅允许「王健」看到/使用「待我审批」，并用于放开研发部 OKR 审批入口。
 * 后续改走 alignment-tree / 真实部门负责人接口后删除此白名单即可。
 */
object DeptRoleHelper {

    const val SP_KEY_IS_DEPT_LEADER = "user_is_dept_leader"

    /** 可审批的假负责人显示名（登录后 nickName / user_name） */
    private val FAKE_APPROVER_NAMES = setOf("王健")

    /** 可审批的部门名关键词（匹配目标所属 / 负责人部门） */
    private val FAKE_APPROVER_DEPT_KEYWORDS = listOf("研发")

    fun isDeptLeader(): Boolean {
        // 优先按当前登录人名称判定，避免依赖未部署的 profile 接口
        if (matchesFakeApprover(currentDisplayName())) return true
        return SpUtils.getBoolean(SP_KEY_IS_DEPT_LEADER, false)
    }

    /** 登录后或刷新用户信息时调用 */
    fun refreshFromLocalUser() {
        setDeptLeader(matchesFakeApprover(currentDisplayName()))
    }

    fun setDeptLeader(isLeader: Boolean) {
        SpUtils.put(SP_KEY_IS_DEPT_LEADER, isLeader)
    }

    fun clear() {
        SpUtils.removeValue(SP_KEY_IS_DEPT_LEADER)
    }

    /** 是否属于王健可批的研发部范围；部门字段缺失时放行（假数据期兼容） */
    fun isRdDept(deptName: String?): Boolean {
        val name = deptName?.trim().orEmpty()
        if (name.isEmpty()) return true
        return FAKE_APPROVER_DEPT_KEYWORDS.any { name.contains(it) }
    }

    private fun matchesFakeApprover(name: String?): Boolean {
        val n = name?.trim().orEmpty()
        if (n.isEmpty()) return false
        return FAKE_APPROVER_NAMES.any { n == it || n.contains(it) }
    }

    private fun currentDisplayName(): String {
        val fromInfo = SpUtils.getString(com.fuusy.common.support.Constants.SP_KEY_USER_INFO_NAME).orEmpty()
        return SpUtils.getString("user_name").orEmpty()
            .ifBlank { fromInfo }
            .ifBlank { SpUtils.getString("user_username").orEmpty() }
    }
}
