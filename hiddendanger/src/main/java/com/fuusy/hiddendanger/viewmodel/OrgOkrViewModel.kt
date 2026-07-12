package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.OkrAlignmentTreeResponse
import com.fuusy.hiddendanger.data.OkrDepartment
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OrgOkrAlignmentItem
import com.fuusy.hiddendanger.data.OrgOkrMapper
import com.fuusy.hiddendanger.data.OrgOkrUserSummary
import com.fuusy.hiddendanger.repository.OkrRepository
import kotlinx.coroutines.launch

class OrgOkrViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _alignmentTree = MutableLiveData<OkrAlignmentTreeResponse?>()
    val alignmentTree: LiveData<OkrAlignmentTreeResponse?> = _alignmentTree

    private val _orgUsers = MutableLiveData<List<OrgOkrUserSummary>>(emptyList())
    val orgUsers: LiveData<List<OrgOkrUserSummary>> = _orgUsers

    private val _orgDepartments = MutableLiveData<List<OkrDepartment>>(emptyList())
    val orgDepartments: LiveData<List<OkrDepartment>> = _orgDepartments

    private val _memberCount = MutableLiveData(0)
    val memberCount: LiveData<Int> = _memberCount

    private val _objectiveCount = MutableLiveData(0)
    val objectiveCount: LiveData<Int> = _objectiveCount

    private val _searchKeyword = MutableLiveData("")
    val searchKeyword: LiveData<String> = _searchKeyword

    private val _alignmentItems = MutableLiveData<List<OrgOkrAlignmentItem>>(emptyList())
    val alignmentItems: LiveData<List<OrgOkrAlignmentItem>> = _alignmentItems

    private val _rootChainCount = MutableLiveData(0)
    val rootChainCount: LiveData<Int> = _rootChainCount

    private var currentPeriod: String = OkrPeriodHelper.currentQuarterValue()
    private var orgDeptId: Long? = null
    private var allUsers: List<OrgOkrUserSummary> = emptyList()
    private var rawObjectives: List<com.fuusy.hiddendanger.data.OkrAlignmentObjective> = emptyList()
    private var directoryNames: Map<Long, String> = emptyMap()

    fun load(periodType: String? = null, deptId: Long? = orgDeptId) {
        val query = periodType ?: currentPeriod
        currentPeriod = query
        orgDeptId = deptId
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            if (directoryNames.isEmpty()) {
                repo.getColleagueDirectory().onSuccess { directoryNames = it }
            }
            repo.getAlignmentTree(query, deptId).fold(
                onSuccess = { tree ->
                    _alignmentTree.value = tree
                    _orgDepartments.value = tree.departments.orEmpty()
                    rawObjectives = tree.objectives
                    allUsers = OrgOkrMapper.groupByUser(tree.objectives, directoryNames)
                    _memberCount.value = allUsers.size
                    _objectiveCount.value = tree.objectives.size
                    _rootChainCount.value = tree.stats?.rootChainCount ?: 0
                    publishFilteredResults()
                },
                onFailure = {
                    _alignmentTree.value = null
                    rawObjectives = emptyList()
                    allUsers = emptyList()
                    _memberCount.value = 0
                    _objectiveCount.value = 0
                    _rootChainCount.value = 0
                    _orgUsers.value = emptyList()
                    _alignmentItems.value = emptyList()
                    _error.value = it.message ?: "加载失败"
                }
            )
            _loading.value = false
        }
    }

    fun setSearchKeyword(keyword: String) {
        _searchKeyword.value = keyword
        publishFilteredResults()
    }

    fun setDeptFilter(deptId: Long?) {
        orgDeptId = deptId
        load(currentPeriod, deptId)
    }

    fun deptFilter(): Long? = orgDeptId

    fun currentPeriod(): String = currentPeriod

    fun findUser(userId: Long): OrgOkrUserSummary? =
        allUsers.firstOrNull { it.userId == userId }

    private fun publishFilteredResults() {
        val keyword = _searchKeyword.value.orEmpty()
        _orgUsers.value = OrgOkrMapper.filterByKeyword(allUsers, keyword)
        _alignmentItems.value = OrgOkrMapper.buildAlignmentRows(
            rawObjectives,
            directoryNames,
            keyword
        )
    }
}
