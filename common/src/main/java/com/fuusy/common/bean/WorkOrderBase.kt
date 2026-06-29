package com.fuusy.common.bean

import java.io.Serializable

/**
 * 工单基础数据类，供各个模块使用
 */
data class WorkOrderBase(
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = "",
    val submitUser: String = "",
    val submitTime: String = "",
    val status: String = ""
) : Serializable
