package com.fuusy.project.bean

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.io.Serializable

@Entity(tableName = "project_items")
data class ProjectItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0, // 自增主键
    val item: String, // 项目编号（不再是主键）
    val itemName: String,      // 项目名称
    val unit: String?,         // 项目单位 (API返回的是unit字段)
    val address: String,       // 地址/作业地点
    val charge: String,        // 负责人
    val phone: String,         // 电话
    val device: String,        // 设备编号 (UI图上显示的可能是这个，如 WSD0005)
    val content: String?,      // 作业内容 (API返回的是content字段，字符串类型)
    val connect: Int = 0,     // 连接状态 (1: 已连接, 0: 未连接)，默认为0
    var isSelected: Boolean = false  // 选中状态
) : Serializable {
    
    // 获取清理后的项目名称（去除多余空格）
    val cleanItemName: String
        get() = itemName.trim()
    
    // 获取清理后的作业内容（去除多余空格）
    val cleanContent: String?
        get() = content?.trim()
}

// API 响应数据类
data class ProjectItemResponse(
    val data: List<ProjectItem>,
    val errorCode: Int,
    val errorMsg: String
)