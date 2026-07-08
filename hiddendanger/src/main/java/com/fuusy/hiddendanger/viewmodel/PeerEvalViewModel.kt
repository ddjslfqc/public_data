package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.OkrPeerUser
import com.fuusy.hiddendanger.data.OkrReviewPrep
import com.fuusy.hiddendanger.data.OkrReviewPrepRequest
import com.fuusy.hiddendanger.data.PeerEvalReceivedResponse
import com.fuusy.hiddendanger.data.PeerEvalSummary
import com.fuusy.hiddendanger.data.PeerEvalTask
import com.fuusy.hiddendanger.repository.PeerEvalRepository
import kotlinx.coroutines.launch

class PeerEvalViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PeerEvalRepository(application)

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _summary = MutableLiveData<PeerEvalSummary>()
    val summary: LiveData<PeerEvalSummary> = _summary

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

    var period: String = DEFAULT_PERIOD
        private set

    private val selectedCollaborators = mutableListOf<OkrPeerUser>()

    fun init(period: String?) {
        this.period = period?.takeIf { it.isNotBlank() } ?: DEFAULT_PERIOD
    }

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            loadColleaguesInternal()
            repo.getSummary(period).fold(
                onSuccess = { _summary.value = it },
                onFailure = { _summary.value = PeerEvalSummary(period = period) }
            )
            repo.getReviewPrep(period).fold(
                onSuccess = { prep ->
                    _reviewPrep.value = prep
                    selectedCollaborators.clear()
                    selectedCollaborators.addAll(prep.collaborators.orEmpty())
                },
                onFailure = {
                    _reviewPrep.value = OkrReviewPrep(period = period, phase = "POST_MEETING")
                }
            )
            reloadTasks()
            reloadReceivedInternal()
            _loading.value = false
        }
    }

    /** 打开选人弹窗前刷新同事列表 */
    fun refreshColleagues(onComplete: () -> Unit) {
        viewModelScope.launch {
            loadColleaguesInternal()
            onComplete()
        }
    }

    private suspend fun loadColleaguesInternal() {
        repo.getColleagues().fold(
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
                        reloadTasksInternal()
                        reloadSummaryInternal()
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

    private suspend fun reloadTasksInternal() {
        repo.getTasks(period).fold(
            onSuccess = { _tasks.value = it },
            onFailure = { _tasks.value = emptyList() }
        )
    }

    private suspend fun reloadSummaryInternal() {
        repo.getSummary(period).fold(
            onSuccess = { _summary.value = it },
            onFailure = { }
        )
    }

    private suspend fun reloadReceivedInternal() {
        repo.getReceivedEval(period).fold(
            onSuccess = { _received.value = it },
            onFailure = { _received.value = PeerEvalReceivedResponse(period = period) }
        )
    }

    private fun reloadTasks() {
        viewModelScope.launch { reloadTasksInternal() }
    }

    companion object {
        const val DEFAULT_PERIOD = "quarter-2"
    }
}
