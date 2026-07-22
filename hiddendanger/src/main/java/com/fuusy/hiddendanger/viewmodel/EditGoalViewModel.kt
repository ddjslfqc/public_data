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
import com.fuusy.hiddendanger.data.OkrAlignContext
import com.fuusy.hiddendanger.data.OkrDepartment
import com.fuusy.hiddendanger.data.OkrObjective
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OkrUser
import com.fuusy.hiddendanger.data.UpdateKrRequest
import com.fuusy.hiddendanger.data.UpdateObjectiveRequest
import com.fuusy.hiddendanger.repository.OkrRepository
import com.fuusy.hiddendanger.ui.model.GoalAlignType
import com.fuusy.hiddendanger.ui.model.GoalKrEditItem
import com.fuusy.hiddendanger.ui.model.GoalKrWeightHelper
import kotlinx.coroutines.launch

class EditGoalViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _created = MutableLiveData<Long?>()
    val created: LiveData<Long?> = _created

    private val _updated = MutableLiveData(false)
    val updated: LiveData<Boolean> = _updated

    /** 编辑模式表单就绪（Activity 据此回填 UI） */
    private val _editForm = MutableLiveData<EditGoalForm?>()
    val editForm: LiveData<EditGoalForm?> = _editForm

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

    var editingObjectiveId: Long? = null
        private set

    val isEditMode: Boolean get() = editingObjectiveId != null

    data class EditGoalForm(
        val title: String,
        val description: String?,
        val periodValue: String,
        val alignEnabled: Boolean,
        val krItems: List<GoalKrEditItem>
    )

    fun loadAlignOptions() {
        viewModelScope.launch {
            _loading.value = true
            loadOptionsInternal()
            _loading.value = false
        }
    }

    fun loadForEdit(objectiveId: Long) {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            loadOptionsInternal()
            repo.getObjectiveEdit(objectiveId).fold(
                onSuccess = { ctx ->
                    val objective = ctx.objective
                    if (objective == null) {
                        _error.value = "目标不存在"
                    } else {
                        editingObjectiveId = objectiveId
                        applyEditContext(objective, ctx.alignment)
                    }
                },
                onFailure = { _error.value = it.message ?: "加载失败" }
            )
            _loading.value = false
        }
    }

    private suspend fun loadOptionsInternal() {
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
                refreshAlignableKrsSync()
            },
            onFailure = { _error.value = it.message }
        )
    }

    private fun applyEditContext(objective: OkrObjective, alignment: OkrAlignContext?) {
        val depts = _deptOptions.value.orEmpty()
        ownDept = objective.deptId?.let { id ->
            depts.find { it.id == id }
                ?: OkrDepartment(id, alignment?.deptName?.takeIf { it.isNotBlank() } ?: "部门$id")
        } ?: ownDept

        val alignKrs = alignment?.alignableKrs.orEmpty()
        if (alignKrs.isNotEmpty()) {
            _alignableKrs.value = alignKrs
        }

        val targetUserId = alignment?.selectedTargetUserId
        if (targetUserId != null) {
            val fromOptions = _alignOptions.value?.users?.find { it.id == targetUserId }
            val fromPersons = alignment.personOptions?.find { it.userId == targetUserId }
            selectedUser = fromOptions
                ?: fromPersons?.let {
                    OkrUser(id = it.userId, name = it.name)
                }
                ?: selectedUser
        }

        val parentKrId = alignment?.selectedParentKrId ?: objective.parentKrId
        selectedParentKr = if (parentKrId != null) {
            alignKrs.find { it.id == parentKrId }
                ?: objective.parentKr?.let { pk ->
                    AlignableKr(
                        id = pk.id,
                        title = pk.title,
                        objective = pk.objective
                    )
                }
        } else {
            null
        }

        val currentUserId = UserIdProvider.userId
        val users = _alignOptions.value?.users.orEmpty()
        val krItems = objective.keyResults.orEmpty().map { kr ->
            val assigneeId = kr.userId ?: currentUserId
            val assigneeName = when {
                assigneeId == currentUserId -> "本人"
                else -> users.find { it.id == assigneeId }?.displayName ?: "用户$assigneeId"
            }
            val weight = kr.weight ?: GoalKrWeightHelper.TOTAL
            GoalKrEditItem(
                id = kr.id,
                serverKrId = kr.id,
                title = kr.title,
                targetPercent = kr.targetValue.toInt().coerceIn(0, 100),
                weight = weight,
                assigneeUserId = assigneeId,
                assigneeName = assigneeName,
                currentValue = kr.currentValue,
                status = kr.status
            )
        }.ifEmpty { listOf(GoalKrEditItem()) }

        // 单 KR 时权重补齐为 100
        if (krItems.size == 1 && krItems[0].weight <= 0) {
            krItems[0].weight = GoalKrWeightHelper.TOTAL
        }

        _editForm.value = EditGoalForm(
            title = objective.title,
            description = objective.description,
            periodValue = OkrPeriodHelper.periodValueFromDates(objective.startDate, objective.endDate),
            alignEnabled = parentKrId != null,
            krItems = krItems
        )
    }

    fun refreshAlignableKrs() {
        viewModelScope.launch { refreshAlignableKrsSync() }
    }

    private suspend fun refreshAlignableKrsSync() {
        val userId = selectedUser?.id ?: return
        repo.getAlignableKrs(userId).fold(
            onSuccess = { applyAlignableList(it) },
            onFailure = { _error.value = it.message }
        )
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

    fun updateObjective(
        periodQueryValue: String,
        title: String,
        description: String?,
        alignEnabled: Boolean,
        krItems: List<GoalKrEditItem>
    ) {
        val objectiveId = editingObjectiveId
        if (objectiveId == null) {
            _error.value = "缺少目标 ID"
            return
        }
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
            UpdateKrRequest(
                id = item.serverKrId,
                title = item.title.trim(),
                targetValue = item.targetPercent.toDouble(),
                weight = item.weight,
                currentValue = item.currentValue,
                unit = GoalKrEditItem.UNIT_PERCENT,
                sortOrder = index,
                status = item.status,
                userId = item.assigneeUserId ?: currentUserId
            )
        }
        val (start, end) = OkrPeriodHelper.dateRange(periodQueryValue)
        val body = UpdateObjectiveRequest(
            id = objectiveId,
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
            repo.updateObjective(body).fold(
                onSuccess = { _updated.value = true },
                onFailure = { _error.value = it.message ?: "保存失败" }
            )
            _loading.value = false
        }
    }
}
