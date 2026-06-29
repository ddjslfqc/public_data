package com.fuusy.project.utils

import android.content.Context
import android.util.Log
import com.fuusy.project.bean.VideoCacheEntity
import com.fuusy.project.bean.VideoCacheDao
import com.fuusy.project.bean.AppDatabase
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException

class VideoDownloadManager(private val context: Context) {
    
    private val videoCacheDao: VideoCacheDao by lazy {
        AppDatabase.getInstance(context).videoCacheDao()
    }
    
    private val downloadJobs = mutableMapOf<String, Job>()
    private val client = OkHttpClient()
    
    interface DownloadCallback {
        fun onProgress(url: String, progress: Int)
        fun onSuccess(url: String, localPath: String)
        fun onError(url: String, error: String)
    }
    
    /**
     * 开始下载视频
     */
    fun startDownload(
        url: String, 
        fileName: String? = null,
        callback: DownloadCallback? = null,
        force: Boolean = false
    ) {
        if (downloadJobs[url]?.isActive == true && !force) {
            Log.d("VideoDownload", "下载任务已存在: $url")
            return
        }
        
        downloadJobs[url]?.cancel()
        downloadJobs[url] = CoroutineScope(Dispatchers.IO).launch {
            try {
                // 检查是否已下载
                val existingCache = videoCacheDao.getByRemoteUrl(url)
                if (existingCache?.downloadStatus == 2 && File(existingCache.localPath).exists()) {
                    Log.d("VideoDownload", "视频已缓存: $url")
                    callback?.onSuccess(url, existingCache.localPath)
                    return@launch
                }
                
                // 创建下载记录
                val cacheFileName = fileName ?: generateFileName(url)
                val cacheEntity = VideoCacheEntity(
                    remoteUrl = url,
                    localPath = "",
                    fileName = cacheFileName,
                    downloadStatus = 1,
                    downloadProgress = 0
                )
                videoCacheDao.insert(cacheEntity)
                
                // 开始下载
                Log.d("VideoDownload", "开始下载: $url")
                val response = client.newCall(Request.Builder().url(url).build()).execute()
                
                if (!response.isSuccessful) {
                    throw IOException("下载失败: ${response.code}")
                }
                
                val contentLength = response.body?.contentLength() ?: -1
                val cacheFile = File(context.cacheDir, "video_${System.currentTimeMillis()}_$cacheFileName")
                
                response.body?.byteStream()?.use { inputStream ->
                    cacheFile.outputStream().use { outputStream ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalBytesRead = 0L
                        
                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                            outputStream.write(buffer, 0, bytesRead)
                            totalBytesRead += bytesRead
                            
                            // 计算进度
                            if (contentLength > 0) {
                                val progress = ((totalBytesRead * 100) / contentLength).toInt()
                                callback?.onProgress(url, progress)
                                
                                // 更新数据库进度
                                videoCacheDao.update(cacheEntity.copy(
                                    downloadProgress = progress,
                                    fileSize = contentLength
                                ))
                            }
                        }
                    }
                }
                
                // 下载完成，更新数据库
                val finalCache = cacheEntity.copy(
                    localPath = cacheFile.absolutePath,
                    downloadStatus = 2,
                    downloadProgress = 100,
                    fileSize = contentLength,
                    updateTime = System.currentTimeMillis()
                )
                videoCacheDao.insert(finalCache)
                
                Log.d("VideoDownload", "下载完成: $url -> ${cacheFile.absolutePath}")
                callback?.onSuccess(url, cacheFile.absolutePath)
                
            } catch (e: Exception) {
                Log.e("VideoDownload", "下载失败: $url", e)
                
                // 更新失败状态
                videoCacheDao.update(VideoCacheEntity(
                    remoteUrl = url,
                    localPath = "",
                    fileName = fileName ?: generateFileName(url),
                    downloadStatus = 3,
                    updateTime = System.currentTimeMillis()
                ))
                
                // 提供更详细的错误信息
                val errorMessage = when (e) {
                    is java.net.UnknownHostException -> "网络连接失败，请检查网络设置"
                    is java.net.SocketTimeoutException -> "网络超时，请检查网络连接"
                    is java.io.IOException -> "文件下载失败，请重试"
                    is java.lang.SecurityException -> "权限不足，无法保存文件"
                    else -> e.message ?: "下载失败，请重试"
                }
                
                callback?.onError(url, errorMessage)
            }
        }
    }
    
    /**
     * 获取视频缓存状态
     */
    suspend fun getCacheStatus(url: String): VideoCacheEntity? {
        return withContext(Dispatchers.IO) {
            videoCacheDao.getByRemoteUrl(url)
        }
    }
    
    /**
     * 检查视频是否已缓存
     */
    suspend fun isVideoCached(url: String): Boolean {
        return withContext(Dispatchers.IO) {
            val cache = videoCacheDao.getByRemoteUrl(url)
            cache?.downloadStatus == 2 && File(cache.localPath).exists()
        }
    }
    
    /**
     * 获取本地缓存路径
     */
    suspend fun getLocalPath(url: String): String? {
        return withContext(Dispatchers.IO) {
            val cache = videoCacheDao.getByRemoteUrl(url)
            if (cache?.downloadStatus == 2 && File(cache.localPath).exists()) {
                cache.localPath
            } else null
        }
    }
    
    /**
     * 取消下载
     */
    fun cancelDownload(url: String) {
        downloadJobs[url]?.cancel()
        downloadJobs.remove(url)
    }
    
    /**
     * 清理所有下载任务
     */
    fun cancelAllDownloads() {
        downloadJobs.values.forEach { it.cancel() }
        downloadJobs.clear()
    }
    
    /**
     * 生成文件名
     */
    private fun generateFileName(url: String): String {
        val fileName = url.substringAfterLast("/", "video")
        return if (fileName.contains(".")) fileName else "$fileName.mp4"
    }
} 