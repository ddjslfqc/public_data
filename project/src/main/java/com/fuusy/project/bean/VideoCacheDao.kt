package com.fuusy.project.bean

import androidx.room.*

@Dao
interface VideoCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(cache: VideoCacheEntity)

    @Query("SELECT * FROM video_cache WHERE remoteUrl = :remoteUrl LIMIT 1")
    suspend fun getByRemoteUrl(remoteUrl: String): VideoCacheEntity?

    @Query("SELECT * FROM video_cache WHERE downloadStatus = 2")
    suspend fun getAllDownloaded(): List<VideoCacheEntity>

    @Query("DELETE FROM video_cache WHERE remoteUrl = :remoteUrl")
    suspend fun deleteByUrl(remoteUrl: String)

    @Query("DELETE FROM video_cache")
    suspend fun deleteAll()

    @Update
    suspend fun update(cache: VideoCacheEntity)
} 