package com.fuusy.common.data.local

import androidx.room.*

@Dao
interface VideoCacheDao {
    @Query("SELECT * FROM video_cache WHERE remoteUrl = :remoteUrl LIMIT 1")
    suspend fun getByRemoteUrl(remoteUrl: String): VideoCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: VideoCacheEntity)

    @Update
    suspend fun update(entity: VideoCacheEntity)
} 