package com.fuusy.hiddendanger.repository

import android.content.Context
import com.fuusy.hiddendanger.data.OkrPeerUser
import com.fuusy.hiddendanger.data.OkrReviewPrep
import com.fuusy.hiddendanger.data.OkrReviewPrepRequest
import com.fuusy.hiddendanger.data.PeerEvalSubmitRequest
import com.fuusy.hiddendanger.data.PeerEvalMockData
import com.fuusy.hiddendanger.data.PeerEvalSummary
import com.fuusy.hiddendanger.data.PeerEvalTask
import com.google.gson.Gson

/**
 * 360 互评本地缓存：后端接口未就绪时保证 App 流程可跑通。
 */
class PeerEvalLocalStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val gson = Gson()

    fun getReviewPrep(period: String): OkrReviewPrep? {
        val json = prefs.getString(keyPrep(period), null) ?: return null
        return runCatching { gson.fromJson(json, OkrReviewPrep::class.java) }.getOrNull()
    }

    fun saveReviewPrep(prep: OkrReviewPrep) {
        prefs.edit()
            .putString(keyPrep(prep.period), gson.toJson(prep))
            .apply()
    }

    fun getTasks(period: String): List<PeerEvalTask> {
        val prep = getReviewPrep(period) ?: return emptyList()
        val doneIds = getCompletedIds(period)
        return prep.collaborators.orEmpty().map { user ->
            PeerEvalTask(
                targetUserId = user.userId,
                targetUserName = user.displayName,
                deptName = user.deptName,
                period = period,
                status = if (doneIds.contains(user.userId)) "DONE" else "PENDING"
            )
        }
    }

    fun markSubmitted(period: String, targetUserId: Long, submission: PeerEvalSubmitRequest) {
        val done = getCompletedIds(period).toMutableSet()
        done.add(targetUserId)
        prefs.edit()
            .putStringSet(keyDone(period), done.map { it.toString() }.toSet())
            .putString(keySubmission(period, targetUserId), gson.toJson(submission))
            .apply()
    }

    fun getSubmission(period: String, targetUserId: Long): PeerEvalSubmitRequest? {
        val json = prefs.getString(keySubmission(period, targetUserId), null) ?: return null
        return runCatching { gson.fromJson(json, PeerEvalSubmitRequest::class.java) }.getOrNull()
    }

    fun getSummary(period: String): PeerEvalSummary {
        val prep = getReviewPrep(period)
        val tasks = getTasks(period)
        val pending = tasks.count { !it.isDone }
        val completed = tasks.count { it.isDone }
        val prepDone = !prep?.projectOutput.isNullOrBlank() == true &&
            !prep?.collaborators.isNullOrEmpty()
        val received = PeerEvalMockData.received(period)
        return PeerEvalSummary(
            period = period,
            phase = prep?.phase ?: "POST_MEETING",
            pendingCount = pending,
            completedCount = completed,
            reviewPrepCompleted = prepDone,
            receivedEvaluatorCount = received.evaluatorCount,
            receivedAverageScore = received.averageScore
        )
    }

    private fun getCompletedIds(period: String): Set<Long> =
        prefs.getStringSet(keyDone(period), emptySet())
            .orEmpty()
            .mapNotNull { it.toLongOrNull() }
            .toSet()

    fun getCompletedTargetIds(period: String): Set<Long> = getCompletedIds(period)

    fun buildPrepFromRequest(request: OkrReviewPrepRequest, users: List<OkrPeerUser>): OkrReviewPrep {
        val idSet = request.collaboratorUserIds.toSet()
        val collaborators = users.filter { it.userId in idSet }.ifEmpty {
            request.collaboratorUserIds.map { id ->
                OkrPeerUser(userId = id, nickName = "用户$id")
            }
        }
        return OkrReviewPrep(
            period = request.period,
            projectOutput = request.projectOutput,
            skillGrowth = request.skillGrowth,
            collaborators = collaborators,
            phase = "POST_MEETING"
        )
    }

    companion object {
        private const val PREFS = "okr_peer_eval_local"

        private fun keyPrep(period: String) = "review_prep_$period"
        private fun keyDone(period: String) = "eval_done_$period"
        private fun keySubmission(period: String, userId: Long) = "eval_sub_${period}_$userId"
    }
}
