package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.MyGoalResponse
import com.fuusy.hiddendanger.data.OkrPeriodOption
import com.fuusy.hiddendanger.data.PeerEvalSummary
import com.fuusy.hiddendanger.repository.OkrRepository
import com.fuusy.hiddendanger.repository.PeerEvalRepository
import com.fuusy.hiddendanger.viewmodel.PeerEvalViewModel
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

    private val _receivedCommentCount = MutableLiveData(0)
    val receivedCommentCount: LiveData<Int> = _receivedCommentCount

    private val _peerEvalPendingCount = MutableLiveData(0)
    val peerEvalPendingCount: LiveData<Int> = _peerEvalPendingCount

    private val _peerEvalCompletedCount = MutableLiveData(0)
    val peerEvalCompletedCount: LiveData<Int> = _peerEvalCompletedCount

    private val _peerEvalSummary = MutableLiveData<PeerEvalSummary?>()
    val peerEvalSummary: LiveData<PeerEvalSummary?> = _peerEvalSummary

    private var currentPeriod: String? = null

    fun load(periodType: String? = null) {
        val query = periodType ?: currentPeriod
        currentPeriod = query
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            repo.getMyGoal(query).fold(
                onSuccess = { _myGoal.value = it },
                onFailure = { _error.value = it.message ?: "加载失败" }
            )
            var krCount = 0
            var progressCount = 0
            repo.getPendingKrs().fold(
                onSuccess = { krPending -> krCount = krPending.size },
                onFailure = { krCount = 0 }
            )
            repo.getPendingUpdateRecords().fold(
                onSuccess = { progressPending -> progressCount = progressPending.size },
                onFailure = { progressCount = 0 }
            )
            _pendingCount.value = krCount + progressCount
            repo.getReceivedComments().fold(
                onSuccess = { _receivedCommentCount.value = it.size },
                onFailure = { _receivedCommentCount.value = 0 }
            )
            val evalPeriod = PeerEvalViewModel.DEFAULT_PERIOD
            peerEvalRepo.getSummary(evalPeriod).fold(
                onSuccess = { summary ->
                    _peerEvalSummary.value = summary
                    _peerEvalPendingCount.value = summary.pendingCount
                    _peerEvalCompletedCount.value = summary.completedCount
                },
                onFailure = {
                    _peerEvalSummary.value = null
                    _peerEvalPendingCount.value = 0
                    _peerEvalCompletedCount.value = 0
                }
            )
            _loading.value = false
        }
    }

    fun activePeriodValue(): String? =
        currentPeriod ?: _myGoal.value?.periods?.firstOrNull { it.active }?.value

    fun periodTabs(): List<OkrPeriodOption> = _myGoal.value?.periods.orEmpty()
}
