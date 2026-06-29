package com.fuusy.hiddendanger.ui.album

import android.app.Activity
import android.app.Application
import android.content.Context
import android.provider.MediaStore
import android.content.ContentUris
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import com.fuusy.common.utils.ToastUtil
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.media.MediaMetadataRetriever
import android.util.Log

class CustomAlbumViewModel(application: Application) : AndroidViewModel(application) {
    val allItems = MutableLiveData<List<AlbumMediaItem>>()
    val showItems = MutableLiveData<List<AlbumMediaItem>>()
    val selected = MutableLiveData<MutableSet<String>>(mutableSetOf())
    val tab = MutableLiveData("ALL")
    val MAX_SELECTION_COUNT = 9

    // 每个Tab的滚动位置
    private val tabScrollPositionMap = mutableMapOf(
        "ALL" to 0,
        "AI" to 0,
        "ALBUM" to 0
    )

    // 每个Tab是否用户操作过
    private val tabUserOperatedMap = mutableMapOf(
        "ALL" to false,
        "AI" to false,
        "ALBUM" to false
    )

    // 全局缓存
    companion object {
        private var globalAlbumCache: List<AlbumMediaItem>? = null
        fun preloadAlbum(context: Context, maxCount: Int = 100) {
            GlobalScope.launch(Dispatchers.IO) {
                val list = mutableListOf<AlbumMediaItem>()
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DATA,
                    MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.Files.FileColumns.DATE_ADDED,
                    MediaStore.Video.VideoColumns.DURATION
                )
                val selection =
                    (MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + " OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
                val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
                val cursor = context.contentResolver.query(
                    MediaStore.Files.getContentUri("external"), projection, selection, null, sortOrder
                )
                cursor?.use {
                    val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val dataCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                    val typeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                    val dateCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                    val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                    while (it.moveToNext()) {
                        val idLong = it.getLong(idCol)
                        val path = it.getString(dataCol)
                        val type = when (it.getInt(typeCol)) {
                            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> AlbumMediaItem.MediaType.IMAGE
                            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> AlbumMediaItem.MediaType.VIDEO
                            else -> continue
                        }
                        val date = java.text.SimpleDateFormat("yyyy年MM月dd日")
                            .format(java.util.Date(it.getLong(dateCol) * 1000))
                        val aiTag = path.contains("ai", ignoreCase = true)
                        val duration =
                            if (type == AlbumMediaItem.MediaType.VIDEO) it.getLong(durationCol) else 0L
                        val contentUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), idLong)
                        list.add(AlbumMediaItem(contentUri.toString(), path, type, date, aiTag, duration))
                    }
                }
                globalAlbumCache = list.take(maxCount)
            }
        }
        fun getPreloadedAlbum(): List<AlbumMediaItem> {
            return globalAlbumCache ?: emptyList()
        }
    }

    init {
        allItems.observeForever {
            updateShowItems(tab.value ?: "ALL")
        }
    }

    /**
     * 说明：
     * 1. updateShowItems(tab, isBackground = true) 只能在子线程（如协程IO）调用，内部自动用postValue，保证线程安全。
     *    updateShowItems(tab) 或 updateShowItems(tab, isBackground = false) 只能在主线程调用。
     * 2. loadMedia方法会自动区分缓存和异步刷新，异步部分所有LiveData赋值都用postValue，绝无线程崩溃风险。
     * 3. 推荐页面只需调用loadMedia(context)，无需关心线程和缓存细节。
     */
    fun loadMedia(context: Application, useCache: Boolean = true, maxCount: Int = 100) {
        val startTime = System.currentTimeMillis()
        android.util.Log.d("CustomAlbumViewModel", "loadMedia: start, useCache=$useCache")
        if (useCache) {
            allItems.value = getPreloadedAlbum()
            updateShowItems(tab.value ?: "ALL")
        }
        // 分批遍历Cursor，首屏优先 - 优化：减少处理数量，避免ANR
        GlobalScope.launch(Dispatchers.IO) {
            val allList = mutableListOf<AlbumMediaItem>()
            val firstPage = mutableListOf<AlbumMediaItem>()
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
                MediaStore.Files.FileColumns.DATE_ADDED,
                MediaStore.Video.VideoColumns.DURATION
            )
            val selection =
                (MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE + " OR " + MediaStore.Files.FileColumns.MEDIA_TYPE + "=" + MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            val sortOrder = MediaStore.Files.FileColumns.DATE_ADDED + " DESC"
            val pageSize = 20 // 减少首屏数量，避免ANR
            val queryStart = System.currentTimeMillis()
            android.util.Log.d("CustomAlbumViewModel", "query start: ${queryStart - startTime}ms after loadMedia start")
            val cursor = context.contentResolver.query(
                MediaStore.Files.getContentUri("external"), projection, selection, null, sortOrder
            )
            var index = 0
            var processedCount = 0
            val maxProcessCount = 200 // 限制处理文件数量，避免ANR
            cursor?.use {
                val idCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val dataCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)
                val typeCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val dateCol = it.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val durationCol = it.getColumnIndexOrThrow(MediaStore.Video.VideoColumns.DURATION)
                while (it.moveToNext() && processedCount < maxProcessCount) {
                    processedCount++
                    val idLong = it.getLong(idCol)
                    val path = it.getString(dataCol)
                    val type = when (it.getInt(typeCol)) {
                        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> AlbumMediaItem.MediaType.IMAGE
                        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> AlbumMediaItem.MediaType.VIDEO
                        else -> continue
                    }
                    val date = java.text.SimpleDateFormat("yyyy年MM月dd日")
                        .format(java.util.Date(it.getLong(dateCol) * 1000))
                    val aiTag = path.contains("ai", ignoreCase = true)
                    val duration =
                        if (type == AlbumMediaItem.MediaType.VIDEO) it.getLong(durationCol) else 0L
                    val contentUri = ContentUris.withAppendedId(MediaStore.Files.getContentUri("external"), idLong)
                    // 过滤无效/损坏/不支持的视频 - 优化：减少MediaMetadataRetriever调用
                    if (type == AlbumMediaItem.MediaType.VIDEO) {
                        val file = java.io.File(path)
                        if (!file.exists() || duration <= 0L) continue
                        // 只在必要时才用MediaMetadataRetriever校验，避免ANR
                        if (duration <= 0L) {
                            try {
                                val retriever = MediaMetadataRetriever()
                                retriever.setDataSource(path)
                                val realDuration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                                retriever.release()
                                if (realDuration <= 0L) continue
                            } catch (e: Exception) {
                                continue
                            }
                        }
                    }
                    if(aiTag){
                        Log.e("looklook", "aiTag\t ${aiTag} \tpath:\t ${path}")
                    }
                    val item = AlbumMediaItem(contentUri.toString(), path, type, date, aiTag, duration)
                    allList.add(item)
                    if (index < pageSize) {
                        firstPage.add(item)
                        index++
                    }
                }
            }
            val afterQuery = System.currentTimeMillis()
            android.util.Log.d("CustomAlbumViewModel", "query finished, cost: ${afterQuery - queryStart}ms, total: ${afterQuery - startTime}ms, totalItems=${allList.size}")
            // 先展示首屏
            allItems.postValue(firstPage)
            updateShowItems(tab.value ?: "ALL")
            val afterFirstPage = System.currentTimeMillis()
            android.util.Log.d("CustomAlbumViewModel", "first page posted, cost: ${afterFirstPage - afterQuery}ms, total: ${afterFirstPage - startTime}ms, firstPageSize=${firstPage.size}")
            // 再异步补全全部
            if (allList.size > pageSize) {
                kotlinx.coroutines.delay(200)
                allItems.postValue(allList)
                updateShowItems(tab.value ?: "ALL")
                val afterAll = System.currentTimeMillis()
                android.util.Log.d("CustomAlbumViewModel", "all items posted, cost: ${afterAll - afterFirstPage}ms, total: ${afterAll - startTime}ms, allSize=${allList.size}")
            }
            globalAlbumCache = allList.take(maxCount)
        }
    }

    fun updateShowItems(tab: String) {
        // 过滤掉已删除的文件
        val validItems = allItems.value?.filter { 
            val file = java.io.File(it.path)
            file.exists() 
        } ?: emptyList()
        
        val filtered = when (tab) {
            "ALL" -> validItems.toList()
            "AI" -> validItems.filter { it.aiTag }
            "ALBUM" -> validItems.toList()
            else -> validItems.toList()
        }
        // 统一按文件修改时间倒序（最新在前）
        val sorted = filtered.sortedByDescending { java.io.File(it.path).lastModified() }
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            showItems.value = sorted
        } else {
            showItems.postValue(sorted)
        }
    }

    fun toggleSelect(item: AlbumMediaItem, context: Activity) {
        val set = selected.value ?: mutableSetOf()
        if (set.contains(item.id)) {
            set.remove(item.id)
        } else {
            if (set.size < MAX_SELECTION_COUNT) {
                set.add(item.id)
            } else {
                ToastUtil.showCustomToast(context, "最多只能选择${MAX_SELECTION_COUNT}个文件")
            }
        }
        selected.value = set
    }

    fun saveTabScrollPosition(tab: String, position: Int) {
        tabScrollPositionMap[tab] = position
    }

    fun getTabScrollPosition(tab: String): Int = tabScrollPositionMap[tab] ?: 0

    fun setTabUserOperated(tab: String) {
        tabUserOperatedMap[tab] = true
    }

    fun isTabUserOperated(tab: String): Boolean = tabUserOperatedMap[tab] ?: false
} 