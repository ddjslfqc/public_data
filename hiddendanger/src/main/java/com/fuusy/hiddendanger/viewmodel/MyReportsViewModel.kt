package com.fuusy.hiddendanger.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fuusy.common.network.UserIdProvider
import com.fuusy.hiddendanger.data.MyReportType
import com.fuusy.hiddendanger.data.ReportArchiveItem
import com.fuusy.hiddendanger.repository.DailyReportRepository
import kotlinx.coroutines.launch
import java.util.Calendar

class MyReportsViewModel : ViewModel() {

    private val repo = DailyReportRepository()

    private val _type = MutableLiveData(MyReportType.DAILY)
    val type: LiveData<MyReportType> = _type

    private val _year = MutableLiveData(Calendar.getInstance().get(Calendar.YEAR))
    val year: LiveData<Int> = _year

    private val _month = MutableLiveData<Int?>(null)
    val month: LiveData<Int?> = _month

    private val _loading = MutableLiveData(false)
    val loading: LiveData<Boolean> = _loading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _items = MutableLiveData<List<ReportArchiveItem>>(emptyList())
    val items: LiveData<List<ReportArchiveItem>> = _items

    private val _filterCount = MutableLiveData(0)
    val filterCount: LiveData<Int> = _filterCount

    /** 忽略快速切 Tab 时过期的响应，避免空列表闪一下又被旧请求覆盖 */
    private var loadSeq = 0

    /**
     * 当前 type+year 下、已按人员过滤、**未按月份过滤**的缓存。
     * 切「全部月份 / 某月」只走本地 period 筛选，与 Web 人员档案一致，避免：
     * 1) 切月时仍短暂显示其它月份；
     * 2) 周报跨月 overlap 与 period 文案不一致。
     */
    private var yearCache: List<ReportArchiveItem> = emptyList()
    private var yearCacheType: MyReportType? = null
    private var yearCacheYear: Int? = null
    private var yearCacheReady = false

    fun setType(type: MyReportType) {
        if (_type.value == type) return
        _type.value = type
        clearYearCache()
        // 切类型时清空，避免短暂展示上一类数据；加载中空态由 Activity 隐藏
        _items.value = emptyList()
        _filterCount.value = 0
        load()
    }

    fun setYear(year: Int) {
        if (_year.value == year) return
        _year.value = year
        clearYearCache()
        _items.value = emptyList()
        _filterCount.value = 0
        load()
    }

    fun setMonth(month: Int?) {
        if (_month.value == month) return
        _month.value = month
        // 有年缓存时瞬间切换，不必重新打接口
        if (canUseYearCache()) {
            publishFiltered(yearCache)
            return
        }
        load()
    }

    fun load() {
        val type = _type.value ?: MyReportType.DAILY
        val year = _year.value
        val seq = ++loadSeq
        val me = UserIdProvider.current()
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            // 按年拉取（不传 month），月份交给本地 period 过滤，保证「全部」与「某月」口径一致
            repo.getReportArchive(
                type = type.apiValue,
                // 与 Web ReportArchive.vue 一致：「我的报告」一律传本人 userId
                userId = me,
                year = year,
                month = null
            ).onSuccess { data ->
                if (seq != loadSeq) return@onSuccess
                val mine = data.list.orEmpty()
                    .filter { item ->
                        val t = MyReportType.fromApi(item.type)
                        t == null || t == type
                    }
                    .filter { item ->
                        me == null || item.userId == 0L || item.userId == me
                    }
                    .sortedByDescending { it.submittedAt.orEmpty() }
                yearCache = mine
                yearCacheType = type
                yearCacheYear = year
                yearCacheReady = true
                publishFiltered(mine)
                _loading.value = false
            }.onFailure { e ->
                if (seq != loadSeq) return@onFailure
                clearYearCache()
                _items.value = emptyList()
                _filterCount.value = 0
                _loading.value = false
                _error.value = e.message ?: "加载失败"
            }
        }
    }

    private fun clearYearCache() {
        yearCache = emptyList()
        yearCacheType = null
        yearCacheYear = null
        yearCacheReady = false
    }

    private fun canUseYearCache(): Boolean {
        val type = _type.value
        val year = _year.value
        return yearCacheReady &&
            yearCacheType == type &&
            yearCacheYear == year
    }

    private fun publishFiltered(source: List<ReportArchiveItem>) {
        val year = _year.value
        val month = _month.value
        val filtered = source.filter { matchesPeriod(it, year, month) }
        _items.value = filtered
        _filterCount.value = filtered.size
    }

    /**
     * 与 Web ReportArchive.vue 一致：用 period 文案包含匹配。
     * - 有月：`2026年8月`
     * - 全部月份：只要含年份
     */
    private fun matchesPeriod(item: ReportArchiveItem, year: Int?, month: Int?): Boolean {
        if (year == null) return true
        val period = item.period.orEmpty()
        if (period.isBlank()) return false
        return if (month != null) {
            period.contains("${year}年${month}月")
        } else {
            period.contains(year.toString())
        }
    }
}
