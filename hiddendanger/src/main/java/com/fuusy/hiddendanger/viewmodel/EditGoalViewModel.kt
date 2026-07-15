package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.network.UserIdProvider
import com.fuusy.hiddendanger.data.AlignOptionsResponse
import com.fuusy.hiddendanger.data.AlignableKr
import com.fuusy.hiddendanger.data.CreateKrRequest
import com.fuusy.hiddendanger.data.CreateObjectiveRequest
import com.fuusy.hiddendanger.data.OkrDepartment
import com.fuusy.hiddendanger.data.OkrObjective
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OkrUser
import com.fuusy.hiddendanger.repository.OkrRepository
import com.fuusy.hiddendanger.ui.model.GoalAlignType
import com.fuusy.hiddendanger.ui.model.GoalKrEditItem
import kotlinx.coroutines.launch

class EditGoalViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _created = MutableLiveData<Long?>()
    val created: LiveData<Long?> = _created

    private val _alignOptions = MutableLiveData<AlignOptionsResponse?>()
    val alignOptions: LiveData<AlignOptionsResponse?> = _alignOptions

    private val _alignableKrs = MutableLiveData<List<AlignableKr>>(emptyList())
    val alignableKrs: LiveData<List<AlignableKr>> = _alignableKrs

    private val _deptOptions = MutableLiveData<List<OkrDepartment>>(emptyList())
    val deptOptions: LiveData<List<OkrDepartment>> = _deptOptions

    var alignType: GoalAlignType = GoalAlignType.SUPERVISOR
    var selectedDept: OkrDepartment? = null
    var selectedUser: OkrUser? = null
    var selectedParentKr: AlignableKr? = null
    var ownDept: OkrDepartment? = null

    fun loadAlignOptions() {
        viewModelScope.launch {
            _loading.value = true
            repo.getDeptOptions().fold(
                onSuccess = { depts ->
                    _deptOptions.value = depts
                    if (ownDept == null) {
                        val loginDeptId = com.fuusy.common.utils.SpUtils.getLong("user_dept_id", 0L)
                        ownDept = depts.find { it.id == loginDeptId }
                            ?: depts.firstOrNull()
                    }
                },
                onFailure = { _error.value = it.message }
            )
            repo.getAlignOptions().fold(
                onSuccess = { options ->
                    _alignOptions.value = options
                    if (selectedUser == null) {
                        selectedUser = options.users?.firstOrNull()
                    }
                    refreshAlignableKrs()
                },
                onFailure = { _error.value = it.message }
            )
            _loading.value = false
        }
    }

    fun refreshAlignableKrs() {
        viewModelScope.launch {
            val userId = selectedUser?.id ?: return@launch
            repo.getAlignableKrs(userId).fold(
                onSuccess = { applyAlignableList(it) },
                onFailure = { _error.value = it.message }
            )
        }
    }

    private fun applyAlignableList(list: List<AlignableKr>) {
        _alignableKrs.value = list
        if (selectedParentKr != null && list.none { it.id == selectedParentKr?.id }) {
            selectedParentKr = null
        }
    }

    fun createObjective(
        periodQueryValue: String,
        title: String,
        description: String?,
        alignEnabled: Boolean,
        krItems: List<GoalKrEditItem>
    ) {
        val dept = ownDept
        if (dept == null) {
            _error.value = "请选择所属部门"
            return
        }
        val filled = krItems.filter { it.title.isNotBlank() }
        if (filled.isEmpty()) {
            _error.value = "请至少填写一条关键结果"
            return
        }
        val currentUserId = UserIdProvider.userId
        val krRequests = filled.mapIndexed { index, item ->
            CreateKrRequest(
                title = item.title.trim(),
                targetValue = item.targetPercent.toDouble(),
                weight = item.weight,
                unit = GoalKrEditItem.UNIT_PERCENT,
                sortOrder = index,
                userId = item.assigneeUserId ?: currentUserId
            )
        }
        val (start, end) = OkrPeriodHelper.dateRange(periodQueryValue)
        val body = CreateObjectiveRequest(
            title = title.trim(),
            description = description?.trim()?.ifBlank { null },
            periodType = OkrPeriodHelper.createPeriodType(periodQueryValue),
            startDate = start,
            endDate = end,
            deptId = dept.id,
            parentKrId = if (alignEnabled) selectedParentKr?.id else null,
            objectiveType = 1,
            keyResults = krRequests
        )
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.createObjective(body).fold(
                onSuccess = { _created.value = it },
                onFailure = { _error.value = it.message ?: "创建失败" }
            )
            _loading.value = false
        }
    }
}
