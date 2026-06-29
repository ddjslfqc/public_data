package com.fuusy.project.bean

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ProjectItem::class, VideoCacheEntity::class], version = 4, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun projectItemDao(): ProjectItemDao
    abstract fun videoCacheDao(): VideoCacheDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "app_database"
                )
                .fallbackToDestructiveMigration() // 添加这行，允许数据库结构变化
                .build().also { instance = it }
            }
    }
} 