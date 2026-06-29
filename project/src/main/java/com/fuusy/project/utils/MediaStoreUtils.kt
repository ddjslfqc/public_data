package com.fuusy.project.utils

import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object MediaStoreUtils {
    
    private const val TAG = "MediaStoreUtils"
    
    /**
     * 保存拍照图片到系统相册
     */
    fun savePhotoToAlbum(context: Context, bitmap: Bitmap, fileName: String): Boolean {
        return try {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Camera")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                resolver.openOutputStream(uri)?.use { outputStream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                }
                
                // 完成保存
                contentValues.clear()
                contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
                
                Log.d(TAG, "照片保存成功: $fileName")
                true
            } else {
                Log.e(TAG, "创建MediaStore URI失败")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存照片失败: ${e.message}")
            false
        }
    }
    
    /**
     * 保存录像文件到系统相册
     */
    fun saveVideoToAlbum(context: Context, videoFile: File, fileName: String): Boolean {
        return try {
            Log.d(TAG, "开始保存视频: ${videoFile.absolutePath}, 文件大小: ${videoFile.length()} bytes")
            
            // 检查权限
            if (!checkStoragePermission(context)) {
                Log.e(TAG, "没有存储权限")
                return false
            }
            
            if (!videoFile.exists()) {
                Log.e(TAG, "视频文件不存在: ${videoFile.absolutePath}")
                return false
            }
            
            if (videoFile.length() == 0L) {
                Log.e(TAG, "视频文件大小为0: ${videoFile.absolutePath}")
                return false
            }
            
            // 根据文件扩展名确定MIME类型
            val mimeType = when {
                videoFile.name.lowercase().endsWith(".mp4") -> "video/mp4"
                videoFile.name.lowercase().endsWith(".ts") -> "video/mp2t"
                videoFile.name.lowercase().endsWith(".avi") -> "video/x-msvideo"
                else -> "video/mp4" // 默认
            }
            
            Log.d(TAG, "使用MIME类型: $mimeType, 文件名: $fileName")
            
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, mimeType)
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Camera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            
            val resolver = context.contentResolver
            Log.d(TAG, "插入MediaStore记录...")
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
            
            if (uri != null) {
                Log.d(TAG, "MediaStore URI创建成功: $uri")
                resolver.openOutputStream(uri)?.use { outputStream ->
                    FileInputStream(videoFile).use { inputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytes = 0L
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytes += bytesRead
                        }
                        outputStream.flush()
                        Log.d(TAG, "文件复制完成，总字节数: $totalBytes")
                    }
                }
                
                // 完成保存
                contentValues.clear()
                contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
                val updateResult = resolver.update(uri, contentValues, null, null)
                Log.d(TAG, "MediaStore更新结果: $updateResult")
                
                Log.d(TAG, "视频保存成功: $fileName, 原始文件: ${videoFile.name}")
                true
            } else {
                Log.e(TAG, "创建MediaStore URI失败")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "保存视频失败: ${e.message}", e)
            false
        }
    }
    
    /**
     * 生成带时间戳的文件名
     */
    fun generateFileName(prefix: String, extension: String): String {
        return "${prefix}_${System.currentTimeMillis()}.$extension"
    }
    
    /**
     * 检查存储权限
     */
    private fun checkStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用 MediaStore，不需要特殊权限
            true
        } else {
            // Android 9 及以下需要存储权限
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }
} 