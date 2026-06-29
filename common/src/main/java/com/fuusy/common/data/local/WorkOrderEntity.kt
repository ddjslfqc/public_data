package com.fuusy.common.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fuusy.common.data.WorkOrderStatus

@Entity(tableName = "work_order")
data class WorkOrderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val formJson: String, // 表单内容序列化为JSON
    val status: String = WorkOrderStatus.PROCESSING.toString(), // draft/submitted/failed
    val createTime: Long = System.currentTimeMillis()
) 