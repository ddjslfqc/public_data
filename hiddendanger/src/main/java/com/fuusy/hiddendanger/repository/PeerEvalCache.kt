package com.fuusy.hiddendanger.repository

/**
 * 360 互评接口内存缓存，跨 [PeerEvalRepository] 实例共享，降低重复请求。
 */
internal object PeerEvalCache {

    private const val DEFAULT_TTL_MS = 120_000L
    private const val COLLEAGUES_TTL_MS = 300_000L

    private data class Entry<T>(val value: T, val cachedAtMs: Long)

    private var summaryEntry: Entry<*>? = null
    private var summaryPeriod: String? = null

    private var tasksEntry: Entry<*>? = null
    private var tasksPeriod: String? = null

    private var reviewPrepEntry: Entry<*>? = null
    private var reviewPrepPeriod: String? = null

    private var receivedEntry: Entry<*>? = null
    private var receivedPeriod: String? = null

    private var colleaguesEntry: Entry<*>? = null

    @Suppress("UNCHECKED_CAST")
    fun <T> getSummary(period: String, ttlMs: Long = DEFAULT_TTL_MS): T? =
        read(summaryEntry, summaryPeriod, period, ttlMs) as? T

    fun putSummary(period: String, value: Any) {
        summaryPeriod = period
        summaryEntry = Entry(value, System.currentTimeMillis())
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getTasks(period: String, ttlMs: Long = DEFAULT_TTL_MS): T? =
        read(tasksEntry, tasksPeriod, period, ttlMs) as? T

    fun putTasks(period: String, value: Any) {
        tasksPeriod = period
        tasksEntry = Entry(value, System.currentTimeMillis())
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getReviewPrep(period: String, ttlMs: Long = DEFAULT_TTL_MS): T? =
        read(reviewPrepEntry, reviewPrepPeriod, period, ttlMs) as? T

    fun putReviewPrep(period: String, value: Any) {
        reviewPrepPeriod = period
        reviewPrepEntry = Entry(value, System.currentTimeMillis())
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getReceived(period: String, ttlMs: Long = DEFAULT_TTL_MS): T? =
        read(receivedEntry, receivedPeriod, period, ttlMs) as? T

    fun putReceived(period: String, value: Any) {
        receivedPeriod = period
        receivedEntry = Entry(value, System.currentTimeMillis())
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> getColleagues(ttlMs: Long = COLLEAGUES_TTL_MS): T? {
        val entry = colleaguesEntry ?: return null
        if (System.currentTimeMillis() - entry.cachedAtMs > ttlMs) return null
        return entry.value as? T
    }

    fun putColleagues(value: Any) {
        colleaguesEntry = Entry(value, System.currentTimeMillis())
    }

    fun invalidateSummary(period: String) {
        if (summaryPeriod == period) {
            summaryEntry = null
            summaryPeriod = null
        }
    }

    fun invalidateTasks(period: String) {
        if (tasksPeriod == period) {
            tasksEntry = null
            tasksPeriod = null
        }
    }

    fun invalidateReviewPrep(period: String) {
        if (reviewPrepPeriod == period) {
            reviewPrepEntry = null
            reviewPrepPeriod = null
        }
    }

    fun invalidateReceived(period: String) {
        if (receivedPeriod == period) {
            receivedEntry = null
            receivedPeriod = null
        }
    }

    fun invalidateColleagues() {
        colleaguesEntry = null
    }

    fun invalidatePeerEval(period: String) {
        invalidateSummary(period)
        invalidateTasks(period)
        invalidateReviewPrep(period)
        invalidateReceived(period)
    }

    private fun read(entry: Entry<*>?, key: String?, period: String, ttlMs: Long): Any? {
        if (entry == null || key != period) return null
        if (System.currentTimeMillis() - entry.cachedAtMs > ttlMs) return null
        return entry.value
    }
}
