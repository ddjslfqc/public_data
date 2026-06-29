package com.fuusy.common.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "attachment",
    foreignKeys = [ForeignKey(
        entity = WorkOrderEntity::class,
        parentColumns = ["id"],
        childColumns = ["workOrderId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class AttachmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val workOrderId: Long,
    val localPath: String,
    val type: String, // "image" or "video"
    val uploadStatus: String = "pending", // pending/success/failed
    val serverUrl: String? = null // 上传成功后填充
) 