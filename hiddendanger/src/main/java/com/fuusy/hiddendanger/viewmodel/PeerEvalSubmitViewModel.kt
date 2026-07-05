package com.fuusy.hiddendanger.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.fuusy.hiddendanger.data.PeerEvalScoreItem
import com.fuusy.hiddendanger.data.PeerEvalSubmitRequest
import com.fuusy.hiddendanger.data.PeerEvalTemplate
import com.fuusy.hiddendanger.repository.PeerEvalRepository
import kotlinx.coroutines.launch

class PeerEvalSubmitViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = PeerEvalRepository(application)

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _submitted = MutableLiveData(false)
    val submitted: LiveData<Boolean> = _submitted

    var period: String = PeerEvalViewModel.DEFAULT_PERIOD
    var targetUserId: Long = 0
    var targetUserName: String = ""

    fun submit(scores: List<PeerEvalScoreItem>, highlight: String, suggestion: String) {
        val requiredIds = PeerEvalTemplate.items.map { it.id }.toSet()
        val scoreMap = scores.associate { it.itemId to it.score }
        val missing = requiredIds.filter { scoreMap[it] !in 1..5 }
        val highlightText = highlight.trim()
        val suggestionText = suggestion.trim()
        when {
            missing.isNotEmpty() -> _error.value = "请完成全部 ${requiredIds.size} 项打分"
            highlightText.isBlank() && suggestionText.isBlank() ->
                _error.value = "请至少填写合作亮点或改进建议其中一项"
            else -> viewModelScope.launch {
                _loading.value = true
                _error.value = null
                repo.submitEval(
                    PeerEvalSubmitRequest(
                        period = period,
                        targetUserId = targetUserId,
                        scores = requiredIds.map { id ->
                            PeerEvalScoreItem(itemId = id, score = scoreMap.getValue(id))
                        },
                        highlight = highlightText.takeIf { it.isNotBlank() },
                        suggestion = suggestionText.takeIf { it.isNotBlank() }
                    )
                ).fold(
                    onSuccess = { _submitted.value = true },
                    onFailure = { _error.value = it.message ?: "提交失败" }
                )
                _loading.value = false
            }
        }
    }

    fun consumeSubmitted() {
        _submitted.value = false
    }
}
