package com.fuusy.login.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.base.BaseViewModel
import com.fuusy.common.network.DataState
import com.fuusy.common.network.net.StateLiveData
import com.fuusy.login.repo.LoginRepo
import com.fuusy.service.repo.LoginResp
import kotlinx.coroutines.launch
import com.fuusy.common.utils.LoadingStatus

class LoginViewModel(private val repo: LoginRepo) : BaseViewModel() {
    val loginLiveData = StateLiveData<LoginResp>()
    val registerLiveData = StateLiveData<Boolean>()

    val loadingStatus = MutableLiveData<LoadingStatus>()

    /** 部门 ID → 显示名 */
    val deptOptions = MutableLiveData<List<Pair<Long, String>>>(emptyList())

    fun login(userName: String, password: String) {
        viewModelScope.launch {
            try {
                loadingStatus.postValue(LoadingStatus.Loading)
                val resp = repo.login(userName, password, loginLiveData)
                if (resp.dataState == DataState.STATE_SUCCESS) {
                    loadingStatus.postValue(LoadingStatus.Success)
                } else {
                    loadingStatus.postValue(
                        LoadingStatus.Error(resp.errorMsg ?: resp.error?.message ?: "登录失败")
                    )
                }
            } catch (e: Exception) {
                loadingStatus.postValue(LoadingStatus.Error(e.message ?: "登录失败"))
            }
        }
    }

    fun loadDeptList() {
        viewModelScope.launch {
            repo.loadDeptList().fold(
                onSuccess = { deptOptions.postValue(it) },
                onFailure = { /* 注册页自行提示 */ }
            )
        }
    }

    fun register(
        userName: String,
        password: String,
        rePassword: String,
        nickName: String,
        deptId: Long
    ) {
        viewModelScope.launch {
            try {
                loadingStatus.postValue(LoadingStatus.Loading)
                val resp = repo.register(userName, password, nickName, deptId, registerLiveData)
                if (resp.dataState == DataState.STATE_SUCCESS) {
                    loadingStatus.postValue(LoadingStatus.Success)
                } else {
                    loadingStatus.postValue(
                        LoadingStatus.Error(resp.errorMsg ?: resp.error?.message ?: "注册失败")
                    )
                }
            } catch (e: Exception) {
                loadingStatus.postValue(LoadingStatus.Error(e.message ?: "注册失败"))
            }
        }
    }
}
