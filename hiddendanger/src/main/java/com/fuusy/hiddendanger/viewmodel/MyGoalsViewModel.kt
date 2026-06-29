package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.MyGoalResponse
import com.fuusy.hiddendanger.data.OkrPeriodOption
import com.fuusy.hiddendanger.data.PendingKrItem
import com.fuusy.hiddendanger.repository.OkrRepository
import kotlinx.coroutines.launch

class MyGoalsViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = OkrRepository()

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _myGoal = MutableLiveData<MyGoalResponse?>()
    val myGoal: LiveData<MyGoalResponse?> = _myGoal

    private val _pendingCount = MutableLiveData(0)
    val pendingCount: LiveData<Int> = _pendingCount

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
            repo.getPendingKrs().fold(
                onSuccess = { _pendingCount.value = it.size },
                onFailure = { /* 非关键 */ }
            )
            _loading.value = false
        }
    }

    fun activePeriodValue(): String? =
        currentPeriod ?: _myGoal.value?.periods?.firstOrNull { it.active }?.value

    fun periodTabs(): List<OkrPeriodOption> = _myGoal.value?.periods.orEmpty()
}
