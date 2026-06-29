package com.fuusy.project.bean

import androidx.room.*

@Dao
interface ProjectItemDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(items: List<ProjectItem>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(items: List<ProjectItem>)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertOrAbort(items: List<ProjectItem>)

    @Query("DELETE FROM project_items")
    suspend fun deleteAll()

    @Query("SELECT * FROM project_items")
    suspend fun getAll(): List<ProjectItem>

    @Query("SELECT * FROM project_items WHERE isSelected = 1")
    suspend fun getSelected(): List<ProjectItem>

    @Query("SELECT * FROM project_items WHERE item = :itemId AND device = :deviceId LIMIT 1")
    suspend fun getById(itemId: String, deviceId: String): ProjectItem?

    @Query("SELECT COUNT(*) FROM project_items")
    suspend fun getCount(): Int

    @Query("UPDATE project_items SET isSelected = 0")
    suspend fun clearAllSelection()

    @Query("UPDATE project_items SET isSelected = 1 WHERE item = :itemId AND device = :deviceId")
    suspend fun setSelected(itemId: String, deviceId: String)

    @Query("UPDATE project_items SET isSelected = 0 WHERE NOT (item = :itemId AND device = :deviceId)")
    suspend fun setOthersUnselected(itemId: String, deviceId: String)
} 