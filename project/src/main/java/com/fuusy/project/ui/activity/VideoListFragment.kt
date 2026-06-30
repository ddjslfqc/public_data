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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fuusy.project.R
import com.fuusy.project.databinding.FragmentVideoListBinding
import com.fuusy.project.repo.ProjectNetRepo
import com.fuusy.project.repo.VideoInfo
import com.fuusy.project.ui.adapter.VideoListAdapter
import kotlinx.coroutines.launch

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
        setupRecyclerView()
        setupSwipeRefresh()
        setupTabs()
        setupSearch()
        setupSettings()
        updateTabStyle()
        loadVideoList()
    }

    private fun setupRecyclerView() {
        adapter = VideoListAdapter(
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
        val selectedBg = R.drawable.bg_tab_selected
        val normalBg = R.drawable.bg_tab_normal
        val alarmNormalBg = R.drawable.bg_tab_alarm
        val alarmSelectedBg = R.drawable.bg_tab_alarm_selected
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.color_ffffff)
        val normalColor = ContextCompat.getColor(requireContext(), R.color.home_text_secondary)
        val alarmColor = ContextCompat.getColor(requireContext(), R.color.home_red)

        binding.tabAll.apply {
            setBackgroundResource(if (currentFilter == FilterType.ALL) selectedBg else normalBg)
            setTextColor(if (currentFilter == FilterType.ALL) selectedColor else normalColor)
        }
        binding.tabOnline.apply {
            setBackgroundResource(if (currentFilter == FilterType.ONLINE) selectedBg else normalBg)
            setTextColor(if (currentFilter == FilterType.ONLINE) selectedColor else normalColor)
        }
        binding.tabAlarm.apply {
            setBackgroundResource(if (currentFilter == FilterType.ALARM) alarmSelectedBg else alarmNormalBg)
            setTextColor(if (currentFilter == FilterType.ALARM) selectedColor else alarmColor)
        }
        binding.tabOffline.apply {
            setBackgroundResource(if (currentFilter == FilterType.OFFLINE) selectedBg else normalBg)
            setTextColor(if (currentFilter == FilterType.OFFLINE) selectedColor else normalColor)
        }
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
            filteredByTab.filter { video ->
                video.show_name?.contains(searchQuery, ignoreCase = true) == true ||
                    video.location?.contains(searchQuery, ignoreCase = true) == true ||
                    video.channel_label?.contains(searchQuery, ignoreCase = true) == true ||
                    video.danger_label?.contains(searchQuery, ignoreCase = true) == true
            }
        }
        Log.d(TAG, "筛选结果: tab=$currentFilter, search=$searchQuery, count=${filtered.size}")
        adapter.submitList(filtered)
        binding.llEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvVideoList.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun loadVideoList(fromRefresh: Boolean = false) {
        if (!fromRefresh) {
            binding.swipeRefresh.isRefreshing = true
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ProjectNetRepo().fetchVideoList()
            binding.swipeRefresh.isRefreshing = false
            allVideos = if (result.isSuccess) {
                result.getOrNull().orEmpty()
            } else {
                showToast("加载失败：${result.exceptionOrNull()?.message ?: "网络异常"}")
                emptyList()
            }
            if (allVideos.isEmpty() && result.isSuccess) {
                showToast("暂无视频数据")
            }
            logVideoList(allVideos)
            updateTabCounts()
            updateTabStyle()
            applyFilter()
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

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun openCloudControl(videoInfo: VideoInfo) {
        Log.d(TAG, "打开视频: name=${videoInfo.show_name}, path=${videoInfo.videoPath}")
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
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val TAG = "VideoList"

        fun newInstance() = VideoListFragment()
    }
}
