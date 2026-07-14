package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.common.auth.DeptRoleHelper
import com.fuusy.hiddendanger.data.MyGoalResponse
import com.fuusy.hiddendanger.data.OkrPeriodHelper
import com.fuusy.hiddendanger.data.OkrPeriodOption
import com.fuusy.hiddendanger.repository.OkrRepository
import com.fuusy.hiddendanger.repository.PeerEvalRepository
import kotlinx.coroutines.launch

class MyGoalsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()
    private val peerEvalRepo = PeerEvalRepository(application)

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _myGoal = MutableLiveData<MyGoalResponse?>()
    val myGoal: LiveData<MyGoalResponse?> = _myGoal

    private val _pendingCount = MutableLiveData(0)
    val pendingCount: LiveData<Int> = _pendingCount

    private val _isDeptLeader = MutableLiveData(DeptRoleHelper.isDeptLeader())
    val isDeptLeader: LiveData<Boolean> = _isDeptLeader

    private val _receivedCommentCount = MutableLiveData(0)
    val receivedCommentCount: LiveData<Int> = _receivedCommentCount

    private val _peerEvalPendingCount = MutableLiveData(0)
    val peerEvalPendingCount: LiveData<Int> = _peerEvalPendingCount

    private val _peerEvalCompletedCount = MutableLiveData(0)
    val peerEvalCompletedCount: LiveData<Int> = _peerEvalCompletedCount

    private val _peerEvalReceivedCount = MutableLiveData(0)
    val peerEvalReceivedCount: LiveData<Int> = _peerEvalReceivedCount

    private val _peerEvalReceivedScore = MutableLiveData<Double?>(null)
    val peerEvalReceivedScore: LiveData<Double?> = _peerEvalReceivedScore

    private var currentPeriod: String? = null

    fun load(periodType: String? = null, includeBadges: Boolean = true) {
        val query = periodType ?: currentPeriod
        currentPeriod = query
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.getMyGoal(query).fold(
                onSuccess = { _myGoal.value = it },
                onFailure = { _error.value = it.message ?: "加载失败" }
            )
            if (includeBadges) {
                DeptRoleHelper.refreshFromLocalUser()
                _isDeptLeader.value = DeptRoleHelper.isDeptLeader()
                refreshBadgesInternal(includePeerEval = shouldLoadPeerEval())
            }
            _loading.value = false
        }
    }

    fun loadGoalsOnly(periodType: String? = null) = load(periodType, includeBadges = false)

    fun refreshBadgesWithoutPeerEval() {
        viewModelScope.launch {
            refreshBadgesInternal(includePeerEval = false)
        }
    }

    fun refreshBadgesAfterPeerEval() {
        viewModelScope.launch {
            refreshKrBadgesInternal()
            if (shouldLoadPeerEval()) {
                refreshPeerEvalTasks(forceRefresh = false)
            }
        }
    }

    fun refreshPeerEvalBadges(includeReceived: Boolean = true) {
        viewModelScope.launch {
            if (!shouldLoadPeerEval()) return@launch
            refreshPeerEvalTasks(forceRefresh = false)
            if (includeReceived) {
                refreshPeerEvalReceived(forceRefresh = false)
            }
        }
    }

    private suspend fun refreshBadgesInternal(includePeerEval: Boolean) {
        refreshKrBadgesInternal()
        if (!includePeerEval) return
        refreshPeerEvalTasks(forceRefresh = false)
        refreshPeerEvalReceived(forceRefresh = false)
    }

    private suspend fun refreshKrBadgesInternal() {
        DeptRoleHelper.refreshFromLocalUser()
        if (!DeptRoleHelper.isDeptLeader()) {
            _pendingCount.value = 0
            _isDeptLeader.value = false
            repo.getReceivedComments().fold(
                onSuccess = { _receivedCommentCount.value = it.size },
                onFailure = { _receivedCommentCount.value = 0 }
            )
            return
        }
        _isDeptLeader.value = true
        var krCount = 0
        var progressCount = 0
        repo.getPendingKrs().fold(
            onSuccess = { krPending ->
                krCount = krPending.count { DeptRoleHelper.isRdDept(it.krOwnerDeptName) }
            },
            onFailure = { krCount = 0 }
        )
        repo.getPendingUpdateRecords().fold(
            onSuccess = { progressPending ->
                progressCount = progressPending.count { DeptRoleHelper.isRdDept(it.submitterDeptName) }
            },
            onFailure = { progressCount = 0 }
        )
        _pendingCount.value = krCount + progressCount
        repo.getReceivedComments().fold(
            onSuccess = { _receivedCommentCount.value = it.size },
            onFailure = { _receivedCommentCount.value = 0 }
        )
    }

    private suspend fun refreshPeerEvalTasks(forceRefresh: Boolean) {
        val evalPeriod = OkrPeriodHelper.peerEvalPeriod()
        peerEvalRepo.getTasks(evalPeriod, forceRefresh).fold(
            onSuccess = { tasks ->
                _peerEvalPendingCount.value = tasks.count { !it.isDone }
                _peerEvalCompletedCount.value = tasks.count { it.isDone }
            },
            onFailure = {
                _peerEvalPendingCount.value = 0
                _peerEvalCompletedCount.value = 0
            }
        )
    }

    private suspend fun refreshPeerEvalReceived(forceRefresh: Boolean) {
        val evalPeriod = OkrPeriodHelper.peerEvalPeriod()
        peerEvalRepo.getReceivedEval(evalPeriod, forceRefresh).fold(
            onSuccess = { received ->
                if (received.evaluatorCount > 0) {
                    _peerEvalReceivedCount.value = received.evaluatorCount
                    _peerEvalReceivedScore.value = received.averageScore
                } else {
                    _peerEvalReceivedCount.value = 0
                    _peerEvalReceivedScore.value = null
                }
            },
            onFailure = {
                _peerEvalReceivedCount.value = 0
                _peerEvalReceivedScore.value = null
            }
        )
    }

    private fun shouldLoadPeerEval(): Boolean {
        val tab = currentPeriod ?: OkrPeriodHelper.currentQuarterValue()
        return OkrPeriodHelper.isPeerEvalVisibleForTab(tab)
    }

    fun activePeriodValue(): String? =
        currentPeriod ?: _myGoal.value?.periods?.firstOrNull { it.active }?.value

    fun periodTabs(): List<OkrPeriodOption> = _myGoal.value?.periods.orEmpty()
}
