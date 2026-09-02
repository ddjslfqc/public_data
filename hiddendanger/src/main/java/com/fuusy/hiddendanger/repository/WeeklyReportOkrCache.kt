package com.fuusy.hiddendanger.repository

import android.content.Context
import com.fuusy.hiddendanger.data.ReportOkrUpdateItem
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 周报 OKR 更新本地快照（后端 report-archive 尚未返回 okrUpdates 时使用）。
 * 提交周报成功后写入；详情页按 archiveId 读取。
 */
object WeeklyReportOkrCache {

    private const val PREFS = "weekly_report_okr_cache_v1"
    private const val KEY_BY_ID = "by_archive_id"
    private val gson = Gson()

    data class Snapshot(
        val archiveId: Long,
        val weekStart: String? = null,
        val weekEnd: String? = null,
        val okrUpdates: List<ReportOkrUpdateItem> = emptyList()
    )

    fun save(
        context: Context,
        archiveId: Long,
        weekStart: String?,
        weekEnd: String?,
        okrUpdates: List<ReportOkrUpdateItem>
    ) {
        if (archiveId <= 0L || okrUpdates.isEmpty()) return
        val map = loadMap(context).toMutableMap()
        map[archiveId.toString()] = Snapshot(archiveId, weekStart, weekEnd, okrUpdates)
        persist(context, map)
    }

    fun load(context: Context, archiveId: Long): Snapshot? {
        if (archiveId <= 0L) return null
        return loadMap(context)[archiveId.toString()]
    }

    private fun loadMap(context: Context): Map<String, Snapshot> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BY_ID, null)
            ?: return emptyMap()
        return runCatching {
            gson.fromJson<Map<String, Snapshot>>(
                raw,
                object : TypeToken<Map<String, Snapshot>>() {}.type
            )
        }.getOrNull().orEmpty()
    }

    private fun persist(context: Context, map: Map<String, Snapshot>) {
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_BY_ID, gson.toJson(map))
            .apply()
    }
}
