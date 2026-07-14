package com.fuusy.common.auth

import com.fuusy.common.utils.SpUtils

/**
 * 部门负责人判定：后端依据 sys_dept.leader 与用户 nick_name / user_name 匹配。
 * App 侧缓存登录/profile 返回的 deptLeader，用于控制「待我审批」入口。
 */
object DeptRoleHelper {

    const val SP_KEY_IS_DEPT_LEADER = "user_is_dept_leader"

    fun isDeptLeader(): Boolean = SpUtils.getBoolean(SP_KEY_IS_DEPT_LEADER, false)

    fun setDeptLeader(isLeader: Boolean) {
        SpUtils.put(SP_KEY_IS_DEPT_LEADER, isLeader)
    }

    fun clear() {
        SpUtils.removeValue(SP_KEY_IS_DEPT_LEADER)
    }
}
