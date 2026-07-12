package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.OkrDepartment
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OkrReviewPrep
import com.fuusy.hiddendanger.data.PeerEvalOrgMapper
import com.fuusy.hiddendanger.data.PeerEvalOrgOverviewResponse
import com.fuusy.hiddendanger.data.PeerEvalOrgUserItem
import com.fuusy.hiddendanger.repository.OkrRepository
import kotlinx.coroutines.launch

class PeerEvalOrgViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _detailLoading = MutableLiveData(false)
    val detailLoading: LiveData<Boolean> = _detailLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _overview = MutableLiveData<PeerEvalOrgOverviewResponse?>()
    val overview: LiveData<PeerEvalOrgOverviewResponse?> = _overview

    private val _users = MutableLiveData<List<PeerEvalOrgUserItem>>(emptyList())
    val users: LiveData<List<PeerEvalOrgUserItem>> = _users

    private val _departments = MutableLiveData<List<OkrDepartment>>(emptyList())
    val departments: LiveData<List<OkrDepartment>> = _departments

    private val _reviewPrep = MutableLiveData<OkrReviewPrep?>()
    val reviewPrep: LiveData<OkrReviewPrep?> = _reviewPrep

    private val _searchKeyword = MutableLiveData("")
    val searchKeyword: LiveData<String> = _searchKeyword

    private var currentPeriod: String = OkrPeriodHelper.peerEvalPeriod()
    private var deptId: Long? = null
    private var allUsers: List<PeerEvalOrgUserItem> = emptyList()

    fun load(period: String? = null, filterDeptId: Long? = deptId) {
        val query = period ?: currentPeriod
        currentPeriod = query
        deptId = filterDeptId
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.getPeerEvalOrgOverview(query, filterDeptId).fold(
                onSuccess = { data ->
                    _overview.value = data
                    allUsers = data.users
                    _departments.value = PeerEvalOrgMapper.departmentsFrom(data.users)
                    publishFilteredUsers()
                },
                onFailure = {
                    _overview.value = null
                    allUsers = emptyList()
                    _users.value = emptyList()
                    _departments.value = emptyList()
                    _error.value = it.message ?: "加载失败"
                }
            )
            _loading.value = false
        }
    }

    fun loadReviewPrep(userId: Long) {
        viewModelScope.launch {
            _detailLoading.value = true
            _error.value = null
            repo.getPeerEvalOrgReviewPrep(currentPeriod, userId).fold(
                onSuccess = { prep -> _reviewPrep.value = prep },
                onFailure = {
                    _reviewPrep.value = null
                    _error.value = it.message ?: "加载复盘失败"
                }
            )
            _detailLoading.value = false
        }
    }

    fun clearReviewPrep() {
        _reviewPrep.value = null
    }

    fun setSearchKeyword(keyword: String) {
        _searchKeyword.value = keyword
        publishFilteredUsers()
    }

    fun setDeptFilter(filterDeptId: Long?) {
        deptId = filterDeptId
        load(currentPeriod, filterDeptId)
    }

    fun deptFilter(): Long? = deptId

    fun currentPeriod(): String = currentPeriod

    private fun publishFilteredUsers() {
        val keyword = _searchKeyword.value.orEmpty()
        _users.value = PeerEvalOrgMapper.filterByKeyword(allUsers, keyword)
    }
}
