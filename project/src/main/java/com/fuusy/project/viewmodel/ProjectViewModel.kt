package com.fuusy.project.viewmodel

import androidx.lifecycle.viewModelScope
import com.fuusy.common.base.BaseViewModel
import com.fuusy.common.network.net.StateLiveData
import com.fuusy.project.bean.ProjectItem
import com.fuusy.project.repo.ProjectRepo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.fuusy.common.utils.LoadingStatus
import androidx.lifecycle.MutableLiveData

class ProjectViewModel(private val repo: ProjectRepo) : BaseViewModel() {
    val mItemListLiveData = StateLiveData<List<ProjectItem>>()
    
    // 添加loading状态
    val loadingStatus = MutableLiveData<LoadingStatus>()

    /**
     * 获取项目列表
     */
    fun getItemList() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // 设置loading状态
                loadingStatus.postValue(LoadingStatus.Loading)
                
                repo.getItemList(mItemListLiveData)
                
                // 设置成功状态
                loadingStatus.postValue(LoadingStatus.Success)
            } catch (e: Exception) {
                loadingStatus.postValue(LoadingStatus.Error(e.message ?: "获取项目列表失败"))
            }
        }
    }
}