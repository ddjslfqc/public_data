package com.fuusy.project.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.ViewOutlineProvider
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fuusy.project.R
import com.fuusy.project.ui.FlvThumbnailLoader
import com.fuusy.project.databinding.FragmentVideoListBinding
import com.fuusy.project.repo.ProjectNetRepo
import com.fuusy.project.repo.VideoInfo
import com.fuusy.project.ui.adapter.VideoListAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class VideoListFragment : Fragment() {

    private var _binding: FragmentVideoListBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: VideoListAdapter
    private var allVideos: List<VideoInfo> = emptyList()
    private var currentFilter: FilterType = FilterType.ALL
    private var searchQuery: String = ""

    enum class FilterType {
        ALL, ONLINE, ALARM, OFFLINE
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentVideoListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyStatusBarPadding()
        setupRecyclerView()
        setupSwipeRefresh()
        setupTabs()
        setupSearch()
        setupSettings()
        setEmptyState("暂无视频", "当前项目暂未接入摄像头")
        updateTabStyle()
        loadVideoList()
    }

    override fun onResume() {
        super.onResume()
        if (!isHidden) {
            FlvThumbnailLoader.setPaused(false)
        }
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        FlvThumbnailLoader.setPaused(hidden)
        if (hidden) {
            FlvThumbnailLoader.cancelAll()
        }
    }

    override fun onPause() {
        FlvThumbnailLoader.setPaused(true)
        super.onPause()
    }

    private fun setupRecyclerView() {
        adapter = VideoListAdapter(
            scope = viewLifecycleOwner.lifecycleScope,
            onItemClick = { videoInfo -> openCloudControl(videoInfo) },
            onActionClick = { videoInfo ->
                when (videoInfo.type) {
                    1 -> showToast("正在重连 ${videoInfo.show_name}…")
                    else -> openCloudControl(videoInfo)
                }
            }
        )
        binding.rvVideoList.layoutManager = LinearLayoutManager(requireContext())
        binding.rvVideoList.adapter = adapter
        binding.rvVideoList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                FlvThumbnailLoader.setPaused(newState != RecyclerView.SCROLL_STATE_IDLE)
            }
        })
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefresh.setColorSchemeResources(R.color.home_blue)
        binding.swipeRefresh.setOnRefreshListener { loadVideoList(fromRefresh = true) }
    }

    private fun setupTabs() {
        binding.tabAll.setOnClickListener { switchTab(FilterType.ALL) }
        binding.tabOnline.setOnClickListener { switchTab(FilterType.ONLINE) }
        binding.tabAlarm.setOnClickListener { switchTab(FilterType.ALARM) }
        binding.tabOffline.setOnClickListener { switchTab(FilterType.OFFLINE) }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString()?.trim().orEmpty()
                applyFilter()
            }
        })
    }

    private fun setupSettings() {
        binding.ivSettings.setOnClickListener {
            showToast("视频设置即将上线")
        }
    }

    private fun switchTab(filter: FilterType) {
        currentFilter = filter
        updateTabStyle()
        applyFilter()
    }

    private fun updateTabCounts() {
        val total = allVideos.size
        val online = allVideos.count { it.type == 0 }
        val alarm = allVideos.count { it.type == 2 }
        val offline = allVideos.count { it.type == 1 }
        binding.tabAll.text = "全部 $total"
        binding.tabOnline.text = "在线 $online"
        binding.tabAlarm.text = "报警 $alarm"
        binding.tabOffline.text = "离线 $offline"
    }

    private fun updateTabStyle() {
        val normalBgColor = Color.parseColor("#F3F4F6")
        val normalTextColor = ContextCompat.getColor(requireContext(), R.color.home_text_normal)
        val selectedTextColor = ContextCompat.getColor(requireContext(), R.color.color_ffffff)
        val cornerRadiusPx = resources.getDimension(R.dimen.wo_filter_tab_radius)
        val alarmUnselectedTextColor = ContextCompat.getColor(requireContext(), R.color.home_red)

        val tabs = listOf(
            binding.tabAll to FilterType.ALL,
            binding.tabOnline to FilterType.ONLINE,
            binding.tabAlarm to FilterType.ALARM,
            binding.tabOffline to FilterType.OFFLINE
        )
        tabs.forEach { (tab, type) ->
            val selected = currentFilter == type
            val bgColor = if (selected) {
                selectedTabColor(type)
            } else {
                if (type == FilterType.ALARM) Color.parseColor("#14EB1919") else normalBgColor
            }
            applyTabBackground(tab, bgColor, cornerRadiusPx)
            tab.setTextColor(
                when {
                    selected -> selectedTextColor
                    type == FilterType.ALARM -> alarmUnselectedTextColor
                    else -> normalTextColor
                }
            )
        }
    }

    private fun applyTabBackground(tab: TextView, color: Int, cornerRadiusPx: Float) {
        tab.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = cornerRadiusPx
            setColor(color)
        }
        tab.clipToOutline = true
        tab.outlineProvider = ViewOutlineProvider.BACKGROUND
    }

    private fun selectedTabColor(type: FilterType): Int = when (type) {
        FilterType.ALL -> ContextCompat.getColor(requireContext(), R.color.home_blue)
        FilterType.ONLINE -> Color.parseColor("#00AA60")
        FilterType.ALARM -> ContextCompat.getColor(requireContext(), R.color.home_red)
        FilterType.OFFLINE -> Color.parseColor("#898FA0")
    }

    private fun applyFilter() {
        val filteredByTab = when (currentFilter) {
            FilterType.ALL -> allVideos
            FilterType.ONLINE -> allVideos.filter { it.type == 0 }
            FilterType.ALARM -> allVideos.filter { it.type == 2 }
            FilterType.OFFLINE -> allVideos.filter { it.type == 1 }
        }
        val filtered = if (searchQuery.isEmpty()) {
            filteredByTab
        } else {
            filteredByTab.filter { it.matchesSearch(searchQuery) }
        }
        Log.d(TAG, "筛选结果: tab=$currentFilter, search=$searchQuery, count=${filtered.size}")
        adapter.submitList(filtered)
        val isEmpty = filtered.isEmpty()
        binding.layoutEmpty.root.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvVideoList.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty) {
            updateEmptyState()
        }
    }

    private fun updateEmptyState() {
        when {
            searchQuery.isNotBlank() -> setEmptyState(
                "未找到匹配的摄像头",
                "试试其他关键词或切换筛选条件"
            )
            currentFilter == FilterType.ONLINE -> setEmptyState("暂无在线摄像头", "当前没有在线状态的摄像头")
            currentFilter == FilterType.ALARM -> setEmptyState("暂无报警摄像头", "当前没有处于报警状态的摄像头")
            currentFilter == FilterType.OFFLINE -> setEmptyState("暂无离线摄像头", "当前没有离线状态的摄像头")
            else -> setEmptyState("暂无视频", "当前项目暂未接入摄像头")
        }
    }

    private fun setEmptyState(title: String, subtitle: String) {
        binding.layoutEmpty.tvEmptyTitle.text = title
        binding.layoutEmpty.tvEmptySubtitle.text = subtitle
    }

    private fun loadVideoList(fromRefresh: Boolean = false) {
        if (!fromRefresh) {
            binding.swipeRefresh.isRefreshing = true
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                ProjectNetRepo().fetchVideoList()
            }
            binding.swipeRefresh.isRefreshing = false
            allVideos = if (result.isSuccess) {
                result.getOrNull().orEmpty()
            } else {
                showToast("加载失败：${result.exceptionOrNull()?.message ?: "网络异常"}")
                emptyList()
            }
            updateTabCounts()
            updateTabStyle()
            applyFilter()
            launch(Dispatchers.Default) { logVideoList(allVideos) }
        }
    }

    private fun logVideoList(list: List<VideoInfo>) {
        Log.d(TAG, "视频列表共 ${list.size} 条")
        list.forEachIndexed { index, video ->
            Log.d(
                TAG,
                "[$index] name=${video.show_name}, type=${video.type}, device=${video.device_id}, " +
                    "channel=${video.channel_id}, path=${video.videoPath}, location=${video.location}, " +
                    "danger=${video.danger_label}, channelLabel=${video.channel_label}"
            )
        }
    }

    private fun applyStatusBarPadding() {
        val extraTopPx = (8 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(binding.topArea) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarTop + extraTopPx)
            insets
        }
        ViewCompat.requestApplyInsets(binding.topArea)
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun openCloudControl(videoInfo: VideoInfo) {
        Log.d(TAG, "打开视频: name=${videoInfo.show_name}, path=${videoInfo.videoPath}")
        FlvThumbnailLoader.setPaused(true)
        FlvThumbnailLoader.cancelAll()
        val intent = Intent(requireContext(), CloudControlActivity::class.java).apply {
            putExtra("streamUrl", videoInfo.videoPath)
            putExtra("videoPath", videoInfo.videoPath)
            putExtra("deviceId", videoInfo.device_id)
            putExtra("channelId", videoInfo.channel_id)
            putExtra("show_name", videoInfo.show_name)
            putExtra("location", videoInfo.location)
        }
        startActivity(intent)
    }

    override fun onDestroyView() {
        FlvThumbnailLoader.cancelAll()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "VideoList"

        fun newInstance() = VideoListFragment()
    }
}

/** 按摄像头名称、位置、通道名搜索（不含隐患类型标签） */
private fun VideoInfo.matchesSearch(query: String): Boolean {
    val fields = listOfNotNull(show_name, location, channel_label)
    return fields.any { it.contains(query, ignoreCase = true) }
}
