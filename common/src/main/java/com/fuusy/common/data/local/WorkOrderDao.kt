package com.fuusy.common.data.local

import androidx.room.*

@Dao
interface WorkOrderDao {
    @Insert
    suspend fun insert(order: WorkOrderEntity): Long

    @Update
    suspend fun update(order: WorkOrderEntity)

    @Query("SELECT * FROM work_order ORDER BY createTime DESC")
    suspend fun getAll(): List<WorkOrderEntity>

    @Query("SELECT * FROM work_order WHERE id = :id")
    suspend fun get(id: Long): WorkOrderEntity?

    @Delete
    suspend fun delete(order: WorkOrderEntity)

    @Query("DELETE FROM work_order WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT * FROM work_order WHERE status = :status")
    suspend fun getByStatus(status: String): List<WorkOrderEntity>

    @Query("SELECT * FROM work_order WHERE status IN (:statusList)")
    suspend fun getByStatusList(statusList: List<String>): List<WorkOrderEntity>
} 