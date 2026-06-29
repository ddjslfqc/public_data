package com.fuusy.login.viewmodel

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.base.BaseViewModel
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
                repo.login(userName, password, loginLiveData)
                loadingStatus.postValue(LoadingStatus.Success)
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
                repo.register(userName, password, nickName, deptId, registerLiveData)
                loadingStatus.postValue(LoadingStatus.Success)
            } catch (e: Exception) {
                loadingStatus.postValue(LoadingStatus.Error(e.message ?: "注册失败"))
            }
        }
    }
}
