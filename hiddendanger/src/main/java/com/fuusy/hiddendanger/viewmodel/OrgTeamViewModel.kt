package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.OkrDepartment
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OkrReviewPrep
import com.fuusy.hiddendanger.data.OrgOkrMapper
import com.fuusy.hiddendanger.data.OrgTeamMapper
import com.fuusy.hiddendanger.data.OrgTeamMemberItem
import com.fuusy.hiddendanger.repository.OkrRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class OrgTeamViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _detailLoading = MutableLiveData(false)
    val detailLoading: LiveData<Boolean> = _detailLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _members = MutableLiveData<List<OrgTeamMemberItem>>(emptyList())
    val members: LiveData<List<OrgTeamMemberItem>> = _members

    private val _departments = MutableLiveData<List<OkrDepartment>>(emptyList())
    val departments: LiveData<List<OkrDepartment>> = _departments

    private val _memberCount = MutableLiveData(0)
    val memberCount: LiveData<Int> = _memberCount

    private val _okrMemberCount = MutableLiveData(0)
    val okrMemberCount: LiveData<Int> = _okrMemberCount

    private val _reviewCompletedCount = MutableLiveData(0)
    val reviewCompletedCount: LiveData<Int> = _reviewCompletedCount

    private val _reviewPrep = MutableLiveData<OkrReviewPrep?>()
    val reviewPrep: LiveData<OkrReviewPrep?> = _reviewPrep

    private val _searchKeyword = MutableLiveData("")
    val searchKeyword: LiveData<String> = _searchKeyword

    private var currentPeriod: String = OkrPeriodHelper.currentQuarterValue()
    private var deptId: Long? = null
    private var allMembers: List<OrgTeamMemberItem> = emptyList()
    private var directoryNames: Map<Long, String> = emptyMap()

    fun load(period: String? = null, filterDeptId: Long? = deptId) {
        val query = period ?: currentPeriod
        currentPeriod = query
        deptId = filterDeptId
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            if (directoryNames.isEmpty()) {
                repo.getColleagueDirectory().onSuccess { directoryNames = it }
            }
            val overviewDeferred = async { repo.getPeerEvalOrgOverview(query, filterDeptId) }
            val treeDeferred = async { repo.getAlignmentTree(query, filterDeptId) }
            val overviewResult = overviewDeferred.await()
            val treeResult = treeDeferred.await()

            overviewResult.fold(
                onSuccess = { overview ->
                    val okrUsers = treeResult.getOrNull()?.let { tree ->
                        OrgOkrMapper.groupByUser(tree.objectives, directoryNames)
                    }.orEmpty()
                    allMembers = OrgTeamMapper.merge(overview.users, okrUsers)
                    _memberCount.value = allMembers.size
                    _okrMemberCount.value = allMembers.count { it.objectiveCount > 0 }
                    _reviewCompletedCount.value = allMembers.count { it.reviewPrepCompleted }
                    _departments.value = OrgTeamMapper.departmentsFrom(allMembers)
                    publishFilteredMembers()
                    if (treeResult.isFailure) {
                        _error.value = treeResult.exceptionOrNull()?.message
                    }
                },
                onFailure = {
                    allMembers = emptyList()
                    _members.value = emptyList()
                    _departments.value = emptyList()
                    _memberCount.value = 0
                    _okrMemberCount.value = 0
                    _reviewCompletedCount.value = 0
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
        publishFilteredMembers()
    }

    fun setDeptFilter(filterDeptId: Long?) {
        deptId = filterDeptId
        load(currentPeriod, filterDeptId)
    }

    fun deptFilter(): Long? = deptId

    fun currentPeriod(): String = currentPeriod

    private fun publishFilteredMembers() {
        val keyword = _searchKeyword.value.orEmpty()
        _members.value = OrgTeamMapper.filterByKeyword(allMembers, keyword)
    }
}
