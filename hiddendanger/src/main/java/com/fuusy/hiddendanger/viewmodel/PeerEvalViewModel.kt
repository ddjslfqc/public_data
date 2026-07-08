package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.OkrPeerUser
import com.fuusy.hiddendanger.data.OkrReviewPrep
import com.fuusy.hiddendanger.data.OkrReviewPrepRequest
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.PeerEvalReceivedResponse
import com.fuusy.hiddendanger.data.PeerEvalTask
import com.fuusy.hiddendanger.repository.PeerEvalRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class PeerEvalViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PeerEvalRepository(application)

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _reviewPrep = MutableLiveData<OkrReviewPrep>()
    val reviewPrep: LiveData<OkrReviewPrep> = _reviewPrep

    private val _tasks = MutableLiveData<List<PeerEvalTask>>(emptyList())
    val tasks: LiveData<List<PeerEvalTask>> = _tasks

    private val _received = MutableLiveData<PeerEvalReceivedResponse>()
    val received: LiveData<PeerEvalReceivedResponse> = _received

    private val _userOptions = MutableLiveData<List<OkrPeerUser>>(emptyList())
    val userOptions: LiveData<List<OkrPeerUser>> = _userOptions

    private val _saved = MutableLiveData(false)
    val saved: LiveData<Boolean> = _saved

    var period: String = OkrPeriodHelper.peerEvalPeriod()
        private set

    private val selectedCollaborators = mutableListOf<OkrPeerUser>()
    private var receivedLoaded = false

    fun init(period: String?) {
        // 360 互评固定为「上一已结束季度」，不接受外部传入当前季度
        this.period = OkrPeriodHelper.peerEvalPeriod()
    }

    /** 首次进入：复盘 + 任务；summary/received 懒加载 */
    fun loadInitial() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            val prepDeferred = async { repo.getReviewPrep(period) }
            val tasksDeferred = async { repo.getTasks(period) }

            prepDeferred.await().fold(
                onSuccess = { prep ->
                    _reviewPrep.value = prep
                    selectedCollaborators.clear()
                    selectedCollaborators.addAll(prep.collaborators.orEmpty())
                },
                onFailure = {
                    _reviewPrep.value = OkrReviewPrep(period = period, phase = "POST_MEETING")
                }
            )
            tasksDeferred.await().fold(
                onSuccess = { _tasks.value = it },
                onFailure = { _tasks.value = emptyList() }
            )
            _loading.value = false
        }
    }

    /** 提交评价成功后刷新任务列表 */
    fun refreshTasksAfterSubmit() {
        viewModelScope.launch {
            reloadTasksInternal(forceRefresh = true)
        }
    }

    /** 切换到「收到的评价」Tab 或打开详情前懒加载 received */
    fun ensureReceivedLoaded(onComplete: (() -> Unit)? = null) {
        if (receivedLoaded && _received.value != null) {
            onComplete?.invoke()
            return
        }
        viewModelScope.launch {
            reloadReceivedInternal(forceRefresh = false)
            receivedLoaded = true
            onComplete?.invoke()
        }
    }

    fun receivedSnapshot(): PeerEvalReceivedResponse? = _received.value

    /** 打开选人弹窗时拉同事列表（带缓存） */
    fun refreshColleagues(onComplete: () -> Unit) {
        viewModelScope.launch {
            loadColleaguesInternal(forceRefresh = false)
            onComplete()
        }
    }

    private suspend fun loadColleaguesInternal(forceRefresh: Boolean) {
        repo.getColleagues(forceRefresh).fold(
            onSuccess = { _userOptions.value = it },
            onFailure = {
                _userOptions.value = emptyList()
                _error.value = it.message ?: "加载同事列表失败"
            }
        )
    }

    fun collaboratorsSnapshot(): List<OkrPeerUser> = selectedCollaborators.toList()

    fun addCollaborator(user: OkrPeerUser) {
        if (selectedCollaborators.any { it.userId == user.userId }) return
        selectedCollaborators.add(user)
    }

    fun addCollaborators(users: List<OkrPeerUser>) {
        users.forEach { addCollaborator(it) }
    }

    fun removeCollaborator(userId: Long) {
        selectedCollaborators.removeAll { it.userId == userId }
    }

    fun saveReviewPrep(projectOutput: String, skillGrowth: String) {
        val trimmedProject = projectOutput.trim()
        val trimmedSkill = skillGrowth.trim()
        when {
            trimmedProject.isBlank() -> _error.value = "请填写 Q2 项目/产出"
            selectedCollaborators.isEmpty() -> _error.value = "请至少选择 1 位合作同事"
            else -> viewModelScope.launch {
                _loading.value = true
                _error.value = null
                val request = OkrReviewPrepRequest(
                    period = period,
                    projectOutput = trimmedProject,
                    skillGrowth = trimmedSkill,
                    collaboratorUserIds = selectedCollaborators.map { it.userId }
                )
                repo.saveReviewPrep(request).fold(
                    onSuccess = {
                        _reviewPrep.value = it
                        selectedCollaborators.clear()
                        selectedCollaborators.addAll(it.collaborators.orEmpty())
                        _saved.value = true
                        reloadTasksInternal(forceRefresh = true)
                    },
                    onFailure = { _error.value = it.message ?: "保存失败" }
                )
                _loading.value = false
            }
        }
    }

    fun consumeSaved() {
        _saved.value = false
    }

    private suspend fun reloadTasksInternal(forceRefresh: Boolean) {
        repo.getTasks(period, forceRefresh).fold(
            onSuccess = { _tasks.value = it },
            onFailure = { _tasks.value = emptyList() }
        )
    }

    private suspend fun reloadReceivedInternal(forceRefresh: Boolean) {
        repo.getReceivedEval(period, forceRefresh).fold(
            onSuccess = { _received.value = it },
            onFailure = {
                _received.value = PeerEvalReceivedResponse(period = period)
            }
        )
    }

    companion object {
        /** @deprecated 请使用 [OkrPeriodHelper.peerEvalPeriod] */
        val DEFAULT_PERIOD: String
            get() = OkrPeriodHelper.peerEvalPeriod()
    }
}
