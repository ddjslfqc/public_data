package com.fuusy.project.ui.activity

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
        setupTabs()
        setupSearch()
        setupSettings()
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
        val selected = R.drawable.bg_tab_selected
        val normal = R.drawable.bg_tab_normal
        val alarmBg = R.drawable.bg_tab_alarm
        val selectedColor = ContextCompat.getColor(requireContext(), R.color.color_ffffff)
        val normalColor = ContextCompat.getColor(requireContext(), R.color.home_text_normal)
        val alarmColor = ContextCompat.getColor(requireContext(), R.color.home_red)

        binding.tabAll.apply {
            setBackgroundResource(if (currentFilter == FilterType.ALL) selected else normal)
            setTextColor(if (currentFilter == FilterType.ALL) selectedColor else normalColor)
        }
        binding.tabOnline.apply {
            setBackgroundResource(if (currentFilter == FilterType.ONLINE) selected else normal)
            setTextColor(if (currentFilter == FilterType.ONLINE) selectedColor else normalColor)
        }
        binding.tabAlarm.apply {
            setBackgroundResource(alarmBg)
            setTextColor(alarmColor)
        }
        binding.tabOffline.apply {
            setBackgroundResource(if (currentFilter == FilterType.OFFLINE) selected else normal)
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
        adapter.submitList(filtered)
        binding.llEmpty.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
        binding.rvVideoList.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun loadVideoList() {
        viewLifecycleOwner.lifecycleScope.launch {
            val result = ProjectNetRepo().fetchVideoList()
            allVideos = if (result.isSuccess) {
                val list = result.getOrNull().orEmpty()
                if (list.isNotEmpty()) list else {
                    showToast("暂无视频数据")
                    VideoListMockData.items()
                }
            } else {
                showToast("加载失败：${result.exceptionOrNull()?.message ?: "网络异常"}")
                VideoListMockData.items()
            }
            updateTabCounts()
            updateTabStyle()
            applyFilter()
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
    }

    private fun openCloudControl(videoInfo: VideoInfo) {
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
        fun newInstance() = VideoListFragment()
    }
}

private object VideoListMockData {

    fun items(): List<VideoInfo> {
        val featured = listOf(
            VideoInfo(
                videoPath = "http://example.com/stream1.flv",
                device_id = "device_001",
                channel_id = "ch_001",
                show_name = "1号炉A侧入口",
                type = 0,
                location = "A侧低过三级人孔门",
                danger_label = "入侵检测",
                channel_label = "通道1"
            ),
            VideoInfo(
                videoPath = "http://example.com/stream2.flv",
                device_id = "device_002",
                channel_id = "ch_002",
                show_name = "2号循环泵房",
                type = 2,
                location = "2号机组·3楼",
                channel_label = "通道2"
            ),
            VideoInfo(
                videoPath = "http://example.com/stream3.flv",
                device_id = "device_003",
                channel_id = "ch_003",
                show_name = "1号炉B侧入口",
                type = 1,
                location = "B侧低过三级人孔门",
                channel_label = "通道3"
            )
        )
        val fillers = listOf(
            mockOnline("3号主控室", "主控室一层", "通道4", "ch_004", "device_004"),
            mockOnline("4号脱硫塔", "塔顶平台", "通道5", "ch_005", "device_005"),
            mockOnline("5号变电房", "配电室A区", "通道6", "ch_006", "device_006"),
            mockOnline("6号输煤栈桥", "栈桥中段", "通道7", "ch_007", "device_007"),
            mockOnline("7号冷却塔", "塔基监测点", "通道8", "ch_008", "device_008"),
            mockOnline("8号化学车间", "酸碱储存区", "通道9", "ch_009", "device_009"),
            mockOnline("9号升压站", "升压站入口", "通道10", "ch_010", "device_010"),
            mockAlarm("10号煤仓", "煤仓顶部", "通道11", "ch_011", "device_011"),
            mockOnline("11号检修平台", "平台东侧", "通道12", "ch_012", "device_012"),
        )
        return featured + fillers
    }

    private fun mockOnline(
        name: String,
        location: String,
        channel: String,
        channelId: String,
        deviceId: String
    ) = VideoInfo(
        videoPath = "http://example.com/$channelId.flv",
        device_id = deviceId,
        channel_id = channelId,
        show_name = name,
        type = 0,
        location = location,
        channel_label = channel
    )

    private fun mockAlarm(
        name: String,
        location: String,
        channel: String,
        channelId: String,
        deviceId: String
    ) = VideoInfo(
        videoPath = "http://example.com/$channelId.flv",
        device_id = deviceId,
        channel_id = channelId,
        show_name = name,
        type = 2,
        location = location,
        channel_label = channel
    )
}
