package com.fuusy.common.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.fuusy.common.data.local.VideoCacheEntity
import com.fuusy.common.data.local.VideoCacheDao

@Database(entities = [WorkOrderEntity::class, AttachmentEntity::class, VideoCacheEntity::class], version = 2)
abstract class AppDatabase : RoomDatabase() {
    abstract fun workOrderDao(): WorkOrderDao
    abstract fun attachmentDao(): AttachmentDao
    abstract fun videoCacheDao(): VideoCacheDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java, "work_order_db"
                ).build().also { instance = it }
            }
    }
} 