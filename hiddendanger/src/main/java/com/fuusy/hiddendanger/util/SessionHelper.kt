package com.fuusy.hiddendanger.util

import android.content.Context
import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.UserIdProvider
import com.fuusy.common.support.Constants
import com.fuusy.common.utils.SpUtils
import com.fuusy.common.auth.DeptRoleHelper
import com.fuusy.service.repo.DbHelper

object SessionHelper {

    fun clearLocalSession(context: Context) {
        UserIdProvider.clear()
        RetrofitManager.clearSessionCookies()
        SpUtils.put("is_logged_in", false)
        SpUtils.removeValue(Constants.SP_KEY_USER_ID)
        SpUtils.removeValue(Constants.SP_KEY_USER_INFO_NAME)
        SpUtils.removeValue("user_name")
        SpUtils.removeValue("user_username")
        SpUtils.removeValue("user_company")
        SpUtils.removeValue("user_department")
        SpUtils.removeValue("user_dept_id")
        DeptRoleHelper.clear()
        SpUtils.removeValue("selected_project")
        DbHelper.deleteUserInfo(context)
        // 悦城助手本地缓存（聊天 / 报告草稿 / 资料）不按用户隔离，登出必须清掉
        context.applicationContext
            .getSharedPreferences("qianwen_chat", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        context.applicationContext
            .getSharedPreferences("weekly_report_okr_cache_v1", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
