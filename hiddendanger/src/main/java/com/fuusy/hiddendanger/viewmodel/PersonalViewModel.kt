package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import android.content.Context
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.data.WorkOrderStatus
import com.fuusy.common.data.local.AppDatabase
import com.fuusy.common.data.local.WorkOrderRepository
import com.fuusy.common.auth.AuthRepository
import com.fuusy.hiddendanger.util.SessionHelper
import com.fuusy.hiddendanger.ui.album.AlbumMediaItem
import com.fuusy.project.workorder.MobileWorkOrderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.fuusy.common.utils.LoadingStatus
import com.fuusy.common.utils.SpUtils
import com.fuusy.service.repo.DbHelper

data class UserInfo(
    val name: String,
    val department: String,
    val role: String = "研发工程师",
    val employeeId: String = "EMP001",
    val avatarUrl: String
) {
    fun subtitle(): String = "$department 丨$role 丨 $employeeId"
}

class PersonalViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getInstance(application)
    private val repository = WorkOrderRepository(db)
    private val workOrderRepo = MobileWorkOrderRepository()

    private val _userInfo = MutableLiveData<UserInfo>()
    val userInfo: LiveData<UserInfo> = _userInfo

    private val _completedTaskCount = MutableLiveData<Int>()
    val completedTaskCount: LiveData<Int> = _completedTaskCount

    private val _averageRating = MutableLiveData<String>()
    val averageRating: LiveData<String> = _averageRating

    private val _logout = MutableLiveData<Boolean>()
    val logout: LiveData<Boolean> = _logout

    private val _draftCount = MutableLiveData<Int>()
    val draftCount: LiveData<Int> = _draftCount

    private val _albumList = MutableLiveData<List<AlbumMediaItem>>()
    val albumList: LiveData<List<AlbumMediaItem>> = _albumList

    // 新增：最近6条媒体缓存
    private val _recentMediaCache = MutableLiveData<List<AlbumMediaItem>>(emptyList())
    val recentMediaCache: LiveData<List<AlbumMediaItem>> = _recentMediaCache
    
    // 添加loading状态
    val loadingStatus = MutableLiveData<LoadingStatus>()

    companion object {
        private var globalRecentMedia: List<AlbumMediaItem>? = null
        fun preloadRecentMedia(context: Application, maxCount: Int = 6) {
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
                        val id = it.getString(idCol)
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
                        list.add(AlbumMediaItem(id, path, type, date, aiTag, duration))
                    }
                }
                globalRecentMedia = list.take(maxCount)
            }
        }
        fun getPreloadedMedia(): List<AlbumMediaItem> {
            return globalRecentMedia ?: emptyList()
        }
    }

    fun loadData() {
        viewModelScope.launch {
            try {
                // 设置loading状态
                loadingStatus.postValue(LoadingStatus.Loading)
                
                // 从数据库或SharedPreferences获取真实用户信息
                loadUserInfo()

                workOrderRepo.dashboard().onSuccess { dashboard ->
                    _completedTaskCount.postValue(dashboard.completedCount)
                    val rating = dashboard.averageRating
                    _averageRating.postValue(
                        if (rating > 0) String.format("%.1f", rating) else "--"
                    )
                }.onFailure {
                    val completedCount = workOrderRepo.list(WorkOrderStatus.COMPLETED)
                        .getOrNull()?.size ?: 0
                    _completedTaskCount.postValue(completedCount)
                    _averageRating.postValue("--")
                }
                val draftOrders = repository.getWorkOrdersByStatus(WorkOrderStatus.DRAFT)
                _draftCount.postValue(draftOrders.size)
                
                // 优先用全局缓存
                _albumList.value = getPreloadedMedia()
                // 异步刷新
                refreshRecentMedia(getApplication())
                
                // 设置成功状态
                loadingStatus.postValue(LoadingStatus.Success)
            } catch (e: Exception) {
                loadingStatus.postValue(LoadingStatus.Error(e.message ?: "加载数据失败"))
            }
        }
    }

    /**
     * 加载用户信息：优先从数据库获取，其次从SharedPreferences获取
     */
    private suspend fun loadUserInfo() {
        withContext(Dispatchers.IO) {
            try {
                // 1. 优先从数据库获取
                val dbUser = DbHelper.getUserInfo(getApplication())
                if (dbUser != null) {
                    _userInfo.postValue(UserInfo(
                        name = dbUser.displayName(),
                        department = dbUser.department.ifBlank { "软件研发部" },
                        role = "研发工程师",
                        employeeId = formatEmployeeId(dbUser.username),
                        avatarUrl = "https://img.rongyuejiaoyu.com/uploads/20240728/02511242750.jpeg"
                    ))
                    return@withContext
                }

                val username = SpUtils.getString("user_name") ?: ""
                val department = SpUtils.getString("user_department") ?: ""

                if (username.isNotEmpty()) {
                    _userInfo.postValue(UserInfo(
                        name = username,
                        department = department.ifBlank { "软件研发部" },
                        role = "研发工程师",
                        employeeId = formatEmployeeId(username),
                        avatarUrl = "https://img.rongyuejiaoyu.com/uploads/20240728/02511242750.jpeg"
                    ))
                    return@withContext
                }

                _userInfo.postValue(UserInfo(
                    name = "常伟思",
                    department = "软件研发部",
                    role = "研发工程师",
                    employeeId = "EMP001",
                    avatarUrl = "https://img.rongyuejiaoyu.com/uploads/20240728/02511242750.jpeg"
                ))
                
            } catch (e: Exception) {
                _userInfo.postValue(UserInfo(
                    name = "常伟思",
                    department = "软件研发部",
                    role = "研发工程师",
                    employeeId = "EMP001",
                    avatarUrl = "https://img.rongyuejiaoyu.com/uploads/20240728/02511242750.jpeg"
                ))
            }
        }
    }

    private fun formatEmployeeId(username: String): String {
        val suffix = username.filter { it.isDigit() }.takeLast(3).padStart(3, '0')
        return if (suffix == "000") "EMP001" else "EMP$suffix"
    }

    fun loadRecentMedia(context: Application) {
        viewModelScope.launch(Dispatchers.IO) {
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
                    val id = it.getString(idCol)
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
                    list.add(AlbumMediaItem(id, path, type, date, aiTag, duration))
                }
            }
            val sorted = list.sortedByDescending { it.date }.take(6)
            withContext(Dispatchers.Main) {
                _recentMediaCache.value = sorted
                _albumList.value = sorted
            }
        }
    }

    // 录制/保存/选择后，主动添加到缓存
    fun addRecentMedia(item: AlbumMediaItem) {
        val current = _recentMediaCache.value?.toMutableList() ?: mutableListOf()
        // 去重，最新的在前
        val filtered = current.filter { it.id != item.id }
        val newList = listOf(item) + filtered
        _recentMediaCache.postValue(newList.take(6))
        // 同步到UI
        _albumList.postValue(newList.take(6))
    }

    // 页面resume时，异步刷新MediaStore
    fun refreshRecentMedia(context: Application) {
        viewModelScope.launch(Dispatchers.IO) {
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
                    val id = it.getString(idCol)
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
                    list.add(AlbumMediaItem(id, path, type, date, aiTag, duration))
                }
            }
            val sorted = list.sortedByDescending { it.date }.take(6)
            withContext(Dispatchers.Main) {
                _recentMediaCache.value = sorted
                _albumList.value = sorted
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            loadingStatus.postValue(LoadingStatus.Loading)
            AuthRepository.logoutRemote()
            SessionHelper.clearLocalSession(getApplication())
            _logout.postValue(true)
            loadingStatus.postValue(LoadingStatus.Success)
        }
    }
}