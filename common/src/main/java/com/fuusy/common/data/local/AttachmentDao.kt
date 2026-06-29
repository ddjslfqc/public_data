package com.fuusy.common.data.local

import androidx.room.*

@Dao
interface AttachmentDao {
    @Insert
    suspend fun insertAll(list: List<AttachmentEntity>)

    @Update
    suspend fun update(attachment: AttachmentEntity)

    @Query("SELECT * FROM attachment WHERE workOrderId = :workOrderId")
    suspend fun getByWorkOrderId(workOrderId: Long): List<AttachmentEntity>

    @Query("DELETE FROM attachment WHERE workOrderId = :workOrderId")
    suspend fun deleteByWorkOrderId(workOrderId: Long)
} 