package com.fuusy.project.ui.activity

import android.content.Context
import android.content.Intent
import android.graphics.SurfaceTexture
import android.net.ConnectivityManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.fuusy.common.base.BaseVmFragment
import com.fuusy.common.support.Constants
import com.fuusy.project.BR
import com.fuusy.project.R
import com.fuusy.project.adapter.MonitoringDataAdapter
import com.fuusy.project.bean.WebSocketMessage
import com.fuusy.project.databinding.FragmentProjectDetailBinding
import com.fuusy.project.ui.AlarmRecordActivity
import com.fuusy.project.ui.VLCPlayer
import com.fuusy.project.ui.model.VideoChannelInfo
import com.fuusy.project.viewmodel.ProjectDetailViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ProjectDetailFragment :
    BaseVmFragment<FragmentProjectDetailBinding, ProjectDetailViewModel>() {
    private var vlcPlayers = arrayOfNulls<VLCPlayer>(4)
    private lateinit var channelButtons: Array<TextView>
    private lateinit var monitoringAdapter: MonitoringDataAdapter
    private var projectId: String? = null

    // 新增：播放器状态管理
    private var isPlayersInitialized = false
    private var currentChannels: List<VideoChannelInfo> = emptyList()
    private var isFragmentDestroyed = false

    // 新增：动态布局管理
    private var currentTextureViews: Array<TextureView> = arrayOf()

    // 添加防抖机制
    private var layoutSwitchJob: Job? = null
    private val layoutSwitchDebounceTime = 300L // 300ms防抖时间

    /**
     * 检查Fragment是否处于活跃状态
     */
    private fun isFragmentActive(): Boolean {
        return !isFragmentDestroyed && isAdded && isVisible && !isDetached
    }

    override fun initContentView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): Int {
        return R.layout.fragment_project_detail
    }

    override fun initVariableId(): Int = BR.viewModel

    override fun initViewModel(): ProjectDetailViewModel {
        return ViewModelProvider(this, object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ProjectDetailViewModel(requireContext()) as T
            }
        })[ProjectDetailViewModel::class.java]
    }

    override fun initViewObservable() {
        // 监听视频通道列表
        mViewModel.videoList.observe(viewLifecycleOwner) { channels ->
            currentChannels = channels
            if (isPlayersInitialized) {
                // 播放器已初始化，只更新流地址
                updateVideoStreams(channels)
            } else {
                // 首次初始化播放器
                setupVideoGrid(channels)
            }
            switchVideoLayout(channels.size ?: 0)
            setupChannelTabs()
            refreshChannelTabEnabled()
            if (currentChannels.size > 0) {
                mDataBinding.tvRealTimePersonnel.text = currentChannels?.first()?.person ?: "0"
                mDataBinding.cardVideoContainer.visibility = View.VISIBLE
                mDataBinding.cardGasArea.visibility = View.VISIBLE
            } else {
                mDataBinding.cardVideoContainer.visibility = View.GONE
                mDataBinding.cardGasArea.visibility = View.GONE
            }
        }
        // 监听分屏/单屏模式
        mViewModel.isSingleMode.observe(viewLifecycleOwner) { isSingle ->
            if (isSingle) {
                switchToSingle(mViewModel.getCurrentChannelIndex())
            } else {
                switchToAll()
            }
        }
        // 监听选中通道
        mViewModel.selectedChannel.observe(viewLifecycleOwner) { channel ->
            channel?.let {
                val idx = mViewModel.getCurrentChannelIndex()
                updateTabSelected(idx)
                switchToSingle(idx)
            }
        }

        // 监听 WebSocket 实时监测数据
        mViewModel.monitoringData.observe(viewLifecycleOwner) { data ->
            monitoringAdapter.setData(data)
            mDataBinding.rvMonitoringData.visibility = View.VISIBLE
        }

        // 监听 WebSocket 连接状态
        mViewModel.connectionStatus.observe(viewLifecycleOwner) { isConnected ->
            updateConnectionStatus(isConnected)
        }

        // 监听 WebSocket 错误信息
        mViewModel.errorMessage.observe(viewLifecycleOwner) { error ->
            if (error.isNotEmpty()) {
                showWebSocketError(error)
            }
        }

        // 监听告警信息
        mViewModel.alerts.observe(viewLifecycleOwner) { alerts ->
            updateAlertCount(alerts.size)
        }

        // 监听告警状态（用于角标显示）
        mViewModel.alarmStatus.observe(viewLifecycleOwner) { alarmData ->
            if (alarmData != null) {
                updateAlarmBadge(alarmData)
            }
        }

        // 监听设备信息
        mViewModel.deviceInfo.observe(viewLifecycleOwner) { deviceInfo ->
            updateDeviceInfo(deviceInfo)
        }

        // 监听项目信息
        mViewModel.currentProject.observe(viewLifecycleOwner) { project ->
            if (project != null) {
                Log.d("ProjectSwitch", "收到项目信息更新: ${project.item} - ${project.itemName}")
                mDataBinding.tvWorkContent.text = project.content
                mDataBinding.tvWorkLocation.text = project.address
                Log.d("ProjectSwitch", "UI已更新: 内容=${project.content}, 地点=${project.address}")
                // 你可以根据布局继续补充其它字段
                mViewModel.loadVideoChannels()
            } else {
                Log.w("ProjectSwitch", "项目信息为空")
            }
        }

        // 监听视频加载状态
        mViewModel.videoLoadingStates.observe(viewLifecycleOwner) { loadingStates ->
            Log.d("VideoLoadingStates", "收到loading状态更新: ${loadingStates.toList().joinToString()}")
            // 更新每个视频的独立 loading 状态
            updateVideoLoadingStates(loadingStates)
        }

        // 监听视频错误状态
        mViewModel.videoErrorStates.observe(viewLifecycleOwner) { errorStates ->
            Log.d("VideoErrorStates", "收到错误状态更新: ${errorStates.joinToString()}")
            // 更新每个视频的错误状态和按钮显示
            updateVideoErrorStates(errorStates)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        isFragmentDestroyed = false

        // 获取 project_id
        projectId = arguments?.getString(Constants.KEY_PROJECT_ID)
        // 初始化控件
        channelButtons = arrayOf(
            mDataBinding.detailBtnChannel1,
            mDataBinding.detailBtnChannel2,
            mDataBinding.detailBtnChannel3,
            mDataBinding.detailBtnChannel4
        )
        // 气体监测数据适配器和mock数据
        mDataBinding.rvMonitoringData.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        monitoringAdapter = MonitoringDataAdapter()
        mDataBinding.rvMonitoringData.adapter = monitoringAdapter
        mDataBinding.rvMonitoringData.visibility = View.VISIBLE
        // 事件绑定
        setupSwitchButtons()
        switchProject()
        setupTextureViewListeners()
        // 默认选中通道1并显示Tab容器
        updateTabSelected(0)
        mDataBinding.layoutChannelSwitch.visibility = View.VISIBLE
        // 加载当前项目信息
        projectId?.let { mViewModel.loadProjectDetail(it) }
    }

    /**
     * 动态切换视频布局
     */
    private fun switchVideoLayout(videoCount: Int) {
        Log.d("VLCPlayer", "switchVideoLayout called, videoCount=$videoCount")
        if (videoCount == 0) {
            mDataBinding.cardVideoContainer.visibility = View.GONE
            return
        }
        // 取消之前的切换任务
        layoutSwitchJob?.cancel()

        // 使用防抖机制，避免频繁切换
        layoutSwitchJob = lifecycleScope.launch(Dispatchers.Main) {
            delay(layoutSwitchDebounceTime)

            // 检查Fragment状态
            if (!isFragmentActive()) {
                Log.d("VLCPlayer", "Fragment状态异常，取消布局切换")
                return@launch
            }

            // 有视频数据时，显示视频区域
            mDataBinding.cardVideoContainer.visibility = View.VISIBLE
            // 显示视频控制按钮
            mDataBinding.detailBtnChannel1.visibility = View.VISIBLE
            mDataBinding.detailBtnChannel2.visibility = View.VISIBLE
            mDataBinding.detailBtnChannel3.visibility = View.VISIBLE
            mDataBinding.detailBtnChannel4.visibility = View.VISIBLE

            // 立即调整高度，确保UI正确显示
            adjustVideoContainerHeight(videoCount)

            // 在切换布局前释放当前播放器资源
            releaseCurrentPlayers()

            // 先清除当前容器中的所有视图
            mDataBinding.layoutVideoContainer.removeAllViews()

            // 根据视频数量加载对应的布局
            val layoutResId = when (videoCount) {
                1 -> R.layout.layout_video_1
                2 -> R.layout.layout_video_2
                3 -> R.layout.layout_video_3
                4 -> R.layout.layout_video_4
                else -> R.layout.layout_video_4
            }
            Log.d("VLCPlayer", "Loading layout for $videoCount videos: layoutResId=$layoutResId")

            // 在IO线程中加载布局
            val videoLayout = withContext(Dispatchers.IO) {
                val layoutInflater = LayoutInflater.from(requireContext())
                layoutInflater.inflate(layoutResId, mDataBinding.layoutVideoContainer, false)
            }

            // 在主线程中添加视图
            mDataBinding.layoutVideoContainer.addView(videoLayout)

            // 获取当前布局中的TextureView
            currentTextureViews = when (videoCount) {
                1 -> arrayOf(
                    videoLayout.findViewById(R.id.textureView1)
                )

                2 -> arrayOf(
                    videoLayout.findViewById(R.id.textureView1),
                    videoLayout.findViewById(R.id.textureView2)
                )

                3 -> arrayOf(
                    videoLayout.findViewById(R.id.textureView1),
                    videoLayout.findViewById(R.id.textureView2),
                    videoLayout.findViewById(R.id.textureView3)
                )

                4 -> arrayOf(
                    videoLayout.findViewById(R.id.textureView1),
                    videoLayout.findViewById(R.id.textureView2),
                    videoLayout.findViewById(R.id.textureView3),
                    videoLayout.findViewById(R.id.textureView4)
                )

                else -> arrayOf()
            }

            Log.d("VLCPlayer", "获取到 ${currentTextureViews.size} 个TextureView")

            // 为每个TextureView设置点击进入云台控制页
            val channelsForClick = mViewModel.videoList.value ?: emptyList()
            for (i in currentTextureViews.indices) {
                currentTextureViews[i].setOnClickListener {
                    val channel = channelsForClick.getOrNull(i) ?: return@setOnClickListener
                    val intent = Intent(requireContext(), CloudControlActivity::class.java)
                    intent.putExtra("channel_index", i)
                    intent.putStringArrayListExtra(
                        "stream_urls",
                        java.util.ArrayList(channelsForClick.map { it.streamUrl })
                    )
                    
                    // 传递当前通道的设备信息（兼容性）
                    intent.putExtra("cameraip", channel.cameraip)
                    intent.putExtra("channelId", channel.channelId)
                    intent.putExtra("deviceId", channel.deviceId)
                    intent.putExtra("region", channel.region)
                    
                    // 传递所有通道的设备信息数组
                    val deviceInfoArray = channelsForClick.map { ch ->
                        "${ch.cameraip}|${ch.channelId}|${ch.deviceId}|${ch.region}"
                    }.toTypedArray()
                    intent.putExtra("device_info_array", deviceInfoArray)
                    
                    startActivity(intent)
                }
            }

            // 为每个TextureView设置SurfaceTextureListener
            setupTextureViewListenersForCurrentLayout()

            // 更新视频网格
            val currentChannels = mViewModel.videoList.value ?: emptyList()
            if (currentChannels.isNotEmpty()) {
                setupVideoGrid(currentChannels)
            }

            // 动态调整视频容器高度
            adjustVideoContainerHeight(videoCount)
        }
    }

    /**
     * 释放当前所有播放器资源
     */
    private fun releaseCurrentPlayers() {
        for (i in vlcPlayers.indices) {
            vlcPlayers[i]?.let { player ->
                try {
                    releasePlayerAsync(player, i)
                } catch (e: Exception) {
                    Log.e("VLCPlayer", "releaseCurrentPlayers: 释放播放器[$i]异常: ${e.message}")
                }
            }
            vlcPlayers[i] = null
        }
        isPlayersInitialized = false
    }

    /**
     * 异步释放播放器资源，主线程解绑 Surface，子线程释放 native 资源
     */
    private fun releasePlayerAsync(player: VLCPlayer, index: Int) {
        // 1. 主线程解绑 Surface
        try {
            player.setVideoSurface(null)
        } catch (e: Exception) {
            Log.e("VLCPlayer", "releasePlayerAsync: setVideoSurface null error: ${e.message}")
        }

        // 2. 使用协程在IO线程释放 native 资源
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                player.safeStop()
                player.safeRelease()
                Log.d("VLCPlayer", "播放器[$index] 资源释放完成")
            } catch (e: Exception) {
                Log.e("VLCPlayer", "releasePlayerAsync: 异步释放播放器[$index]异常: ${e.message}")
            }
        }
    }

    private fun adjustVideoContainerHeight(videoCount: Int) {
        val density = resources.displayMetrics.density

        // 调整整个视频容器CardView的高度
        val cardParams = mDataBinding.cardVideoContainer.layoutParams
        cardParams.height =
            if (videoCount <= 2) (150 * density).toInt() else (300 * density).toInt()
        mDataBinding.cardVideoContainer.layoutParams = cardParams

        // 同时调整内部视频布局容器的高度
        val containerParams = mDataBinding.layoutVideoContainer.layoutParams
        containerParams.height = cardParams.height
        mDataBinding.layoutVideoContainer.layoutParams = containerParams

        Log.d(
            "VideoHeight",
            "调整视频容器高度: videoCount=$videoCount, height=${cardParams.height}px (${if (videoCount <= 2) "150dp" else "300dp"})"
        )
    }

    /**
     * 为当前布局设置TextureView监听器
     */
    private fun setupTextureViewListenersForCurrentLayout() {
        Log.d("VLCPlayer", "setupTextureViewListenersForCurrentLayout called")
        for (i in currentTextureViews.indices) {
            setupTextureViewListener(currentTextureViews[i], i)
        }
        mDataBinding.alarm.setOnClickListener {
            val intent = Intent(activity, AlarmRecordActivity::class.java)
            startActivity(intent)
        }
    }

    /**
     * 设置 TextureView 的 SurfaceTextureListener
     */
    private fun setupTextureViewListeners() {
        // 这个方法会在动态布局切换后重新设置
        Log.d("VLCPlayer", "setupTextureViewListeners: 设置TextureView监听器")
    }

    /**
     * 为指定的 TextureView 设置 SurfaceTextureListener
     */
    private fun setupTextureViewListener(textureView: TextureView, index: Int) {
        Log.d("VLCPlayer", "为TextureView[$index]设置SurfaceTextureListener")

        // 移除旧的监听器
        textureView.surfaceTextureListener = null

        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                Log.d("VLCPlayer", "TextureView[$index] Surface可用: ${width}x${height}")

                if (isFragmentDestroyed) {
                    Log.d("VLCPlayer", "Fragment已销毁，不初始化播放器[$index]")
                    return
                }

                // 检查是否有对应的通道数据
                if (index < currentChannels.size) {
                    // 延迟初始化播放器，确保Surface完全准备好
                    lifecycleScope.launch(Dispatchers.Main) {
                        delay(100) // 100ms延迟
                        if (!isFragmentDestroyed && index < currentChannels.size) {
                            initializePlayer(index)
                        }
                    }
                }
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {
                Log.d("VLCPlayer", "TextureView[$index] Surface尺寸变化: ${width}x${height}")
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                Log.d("VLCPlayer", "TextureView[$index] Surface销毁")
                // 返回true表示Surface已被销毁，不需要手动处理
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                // 不需要处理
            }
        }
    }

    private fun isNetworkAvailable(): Boolean {
        try {
            val cm =
                requireContext().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val activeNetwork = cm.activeNetworkInfo
            return activeNetwork != null && activeNetwork.isConnected
        } catch (e: java.lang.Exception) {
            Log.e("VLC", "检查网络状态失败: " + e.message)
            return false
        }
    }

    /**
     * 初始化指定索引的播放器
     */
    private fun initializePlayer(index: Int) {
        Log.d("VLCPlayer", "initializePlayer called for index: $index")

        // 检查Fragment状态
        if (isFragmentDestroyed || !isAdded || !isVisible) {
            Log.d("VLCPlayer", "Fragment状态异常，不初始化播放器[$index]")
            return
        }

        if (index >= currentChannels.size) {
            Log.d("VLCPlayer", "播放器[$index] 索引超出范围")
            return
        }

        // 检查TextureView状态
        if (index >= currentTextureViews.size || !currentTextureViews[index].isAvailable) {
            Log.d("VLCPlayer", "TextureView[$index] 不可用，延迟初始化")
            return
        }

        // 设置该视频为加载中状态
        mViewModel.setVideoLoadingState(index, true)

        val rtsp = currentChannels[index].streamUrl
        Log.d("VLCPlayer", "初始化播放器[$index]，流地址: $rtsp")

        // 检查网络连接
        if (!isNetworkAvailable()) {
            Log.e("VLCPlayer", "网络不可用，无法播放流")
            mViewModel.setVideoLoadingState(index, false)
            return
        }

        // 在后台线程中创建播放器
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // 再次检查Fragment状态
                if (isFragmentDestroyed || !isAdded) {
                    Log.d("VLCPlayer", "Fragment已销毁，取消播放器[$index]初始化")
                    return@launch
                }

                // 创建播放器
                val player = VLCPlayer(requireContext())

                // 在主线程中设置回调和Surface
                withContext(Dispatchers.Main) {
                    // 再次检查Fragment状态
                    if (isFragmentDestroyed || !isAdded) {
                        Log.d("VLCPlayer", "Fragment已销毁，取消播放器[$index]设置")
                        return@withContext
                    }

                    // 设置错误回调
                    player.setCallback(object : VLCPlayer.VLCPlayerCallback {
                        override fun onError() {
                            Log.e("VLCPlayer", "播放器[$index] 播放错误 - 流地址: $rtsp")
                            // 播放错误时，设置该视频加载完成（失败也算完成）并设置错误状态
                            lifecycleScope.launch(Dispatchers.Main) {
                                if (!isFragmentDestroyed) {
                                    Log.d("VLCPlayer", "播放器[$index] 设置错误状态: loading=false, error=true")
                                    // 先设置loading为false，再设置error为true，确保状态正确
                                    mViewModel.setVideoLoadingState(index, false)
                                    // 延迟一点再设置错误状态，确保loading状态先更新
                                    delay(100)
                                    mViewModel.setVideoErrorState(index, true)
                                } else {
                                    Log.w("VLCPlayer", "播放器[$index] Fragment已销毁，不设置错误状态")
                                }
                            }
                        }

                        override fun playing() {
                            Log.d("VLCPlayer", "播放器[$index] 开始播放 - 流地址: $rtsp")
                            // 开始播放时，设置该视频加载完成并清除错误状态
                            lifecycleScope.launch(Dispatchers.Main) {
                                if (!isFragmentDestroyed) {
                                    mViewModel.setVideoLoadingState(index, false)
                                    mViewModel.setVideoErrorState(index, false)
                                }
                            }
                        }

                        override fun onBuffering(bufferPercent: Float) {
                            // 当缓冲达到一定程度时，认为视频开始播放，隐藏loading
                            if (bufferPercent > 98f) {
                                Log.d("VLCPlayer", "播放器[$index] 缓冲进度: $bufferPercent%")
                                lifecycleScope.launch(Dispatchers.Main) {
                                    if (!isFragmentDestroyed) {
                                        mViewModel.setVideoLoadingState(index, false)
                                        mViewModel.setVideoErrorState(index, false)
                                    }
                                }
                            }
                        }

                        override fun onEndReached() {
                            // 不需要处理
                        }

                        override fun onTimeChanged(time: Long) {
                            // 不需要处理
                        }

                        override fun onPositionChanged(position: Float) {
                            // 不需要处理
                        }
                    })

                    vlcPlayers[index] = player

                    // 直接设置Surface和DataSource，因为此时Surface已经可用
                    Log.d("VLCPlayer", "TextureView[$index] 可用，设置Surface和DataSource")
                    player.setVideoSurface(currentTextureViews[index])
                    player.setDataSource(rtsp)

                    // 开始播放
                    if (currentTextureViews[index].isVisible) {
                        Log.d("VLCPlayer", "TextureView[$index] 可见，开始播放")
                        player.play()
                        player.setVolume(0)
                        
//                        // 添加超时机制，确保loading状态能正确更新
//                        lifecycleScope.launch(Dispatchers.Main) {
//                            delay(8000) // 8秒后检查
//                            if (!isFragmentDestroyed && mViewModel.videoLoadingStates.value?.get(index) == true) {
//                                Log.w("VLCPlayer", "播放器[$index] 超时，强制隐藏loading")
//                                mViewModel.setVideoLoadingState(index, false)
//                                mViewModel.setVideoErrorState(index, false)
//                            }
//                        }
                    } else {
                        Log.d("VLCPlayer", "TextureView[$index] 不可见，不播放")
                    }
                }

                isPlayersInitialized = true
            } catch (e: Exception) {
                Log.e("VLCPlayer", "初始化播放器[$index]失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (!isFragmentDestroyed) {
                        mViewModel.setVideoLoadingState(index, false)
                    }
                }
            }
        }
    }

    /**
     * 设置视频网格（首次初始化）
     */
    private fun setupVideoGrid(channels: List<VideoChannelInfo>) {
        Log.d("VLCPlayer", "setupVideoGrid called with ${channels.size} channels")
        currentChannels = channels
        // 播放器会在SurfaceTexture准备好时自动初始化
    }

    /**
     * 更新视频流地址
     */
    private fun updateVideoStreams(channels: List<VideoChannelInfo>) {
        for (i in channels.indices) {
            if (i >= currentTextureViews.size) break

            val rtsp = channels[i].streamUrl
            if (vlcPlayers[i] != null) {
                // 播放器已存在，只更新流地址
                // 设置该视频为加载中状态
                mViewModel.setVideoLoadingState(i, true)
                vlcPlayers[i]?.setDataSource(rtsp)
                // 确保 SurfaceTexture 可用时才播放
                if (currentTextureViews[i].isAvailable && currentTextureViews[i].isVisible) {
                    vlcPlayers[i]?.play()
                    
                    // 为已存在的播放器也添加超时机制
                    lifecycleScope.launch(Dispatchers.Main) {
                        delay(8000) // 8秒后检查
                        if (!isFragmentDestroyed && mViewModel.videoLoadingStates.value?.get(i) == true) {
                            Log.w("VLCPlayer", "播放器[$i] 超时，强制隐藏loading")
                            mViewModel.setVideoLoadingState(i, false)
                            mViewModel.setVideoErrorState(i, false)
                        }
                    }
                }
            } else {
                // 播放器不存在，初始化
                initializePlayer(i)
            }
        }
    }

    private fun switchToAll() {
        // 重新加载所有视频的布局
        val channels = mViewModel.videoList.value ?: emptyList()
        switchVideoLayout(channels.size)

        // 在全屏模式下，恢复所有视频的 loading 状态
        val currentLoadingStates =
            mViewModel.videoLoadingStates.value?.toMutableMap() ?: mutableMapOf()
        // 确保所有视频都有 loading 状态
        for (i in channels.indices) {
            if (!currentLoadingStates.containsKey(i)) {
                currentLoadingStates[i] = false
            }
        }
        mViewModel.updateVideoLoadingStates(currentLoadingStates)

        updateSwitchButtonsUI()
    }

    private fun switchToSingle(index: Int) {
        val channels = mViewModel.videoList.value ?: emptyList()
        if (index >= channels.size) {
            Log.e("VLC", "Selected channel index $index is not available.")
            return
        }

        // 切换到单视频布局
        switchVideoLayout(1)

        // 只播放选中的通道
        if (vlcPlayers[index] != null) {
            vlcPlayers[index]?.play()
        }

        // 在单屏模式下，只显示当前选中视频的 loading 状态
        val currentLoadingStates =
            mViewModel.videoLoadingStates.value?.toMutableMap() ?: mutableMapOf()
        currentLoadingStates.clear()
        currentLoadingStates[index] = currentLoadingStates[index] ?: false
        mViewModel.updateVideoLoadingStates(currentLoadingStates)

        updateSwitchButtonsUI()
    }

    private fun setupSwitchButtons() {
        mDataBinding.btnAll.setOnClickListener {
            mViewModel.switchToAll()
        }
        mDataBinding.btnSingle.setOnClickListener {
            val index = mViewModel.getCurrentChannelIndex()
            mViewModel.switchToSingle(
                mViewModel.videoList.value?.getOrNull(index) ?: return@setOnClickListener
            )
        }
        mDataBinding.tvProjectTitle.text = projectId
    }

    private fun setupChannelTabs() {
        for (i in channelButtons.indices) {
            channelButtons[i].text = "通道${i + 1}"
            channelButtons[i].setOnClickListener {
                // 不足通道数时，不允许点击
                val available = mViewModel.videoList.value?.size ?: 0
                if (i >= available) return@setOnClickListener
                switchChannelByIndex(i)
            }
        }
        // 初始阶段：等待视频数据返回前，先禁用所有按钮避免误触
        channelButtons.forEach { btn ->
            btn.isEnabled = false
            btn.isClickable = false
            btn.alpha = 0.4f
        }
        // 绑定左右滑动到监测列表（如需改为视频容器，替换ID即可）
        attachSwipeTo(mDataBinding.rvMonitoringData)
    }

    private fun refreshChannelTabEnabled() {
        val available = mViewModel.videoList.value?.size ?: 0
        for (i in channelButtons.indices) {
            val enabled = i < available
            val alpha = if (enabled) 1f else 0.4f
            channelButtons[i].isEnabled = enabled
            channelButtons[i].isClickable = enabled
            channelButtons[i].alpha = alpha
        }
    }

    private fun switchChannelByIndex(index: Int) {
        updateTabSelected(index)
        onChannelTabClicked(index)
    }

    private fun updateTabSelected(index: Int) {
        channelButtons.forEachIndexed { i, tv -> tv.isSelected = (i == index) }
    }

    private fun attachSwipeTo(view: View) {
        val detector =
            GestureDetector(view.context, object : GestureDetector.SimpleOnGestureListener() {
                private val threshold = 80
                private val velocity = 120
                override fun onFling(
                    e1: MotionEvent,
                    e2: MotionEvent,
                    velocityX: Float,
                    velocityY: Float
                ): Boolean {
                    val dx = e2.x - e1.x
                    if (kotlin.math.abs(dx) > threshold && kotlin.math.abs(velocityX) > velocity) {
                        if (dx < 0) switchChannelByIndex(getCurrentSelected() + 1) else switchChannelByIndex(
                            getCurrentSelected() - 1
                        )
                        return true
                    }
                    return false
                }
            })
        view.setOnTouchListener { _, ev -> detector.onTouchEvent(ev) }
    }

    private fun getCurrentSelected(): Int {
        return channelButtons.indexOfFirst { it.isSelected }.let { if (it == -1) 0 else it }
    }

    // 统一的通道Tab点击回调：在这里加你的具体业务
    private fun onChannelTabClicked(index: Int) {
        // 示例：打印与轻提示，可按需替换为真实业务（请求气体数据/上报埋点等）
        try {
            android.util.Log.d("ChannelTab", "点击了通道: ${index + 1}")

        } catch (_: Exception) {
        }
        // 若需要提示：
        // Toast.makeText(requireContext(), "切换到通道${index + 1}", Toast.LENGTH_SHORT).show()
    }

    private fun updateSwitchButtonsUI() {
        // 可根据需要高亮当前通道按钮等
    }

    private fun switchProject() {
        mDataBinding.tvViewAll.setOnClickListener {
            mViewModel.currentProject.value?.let { project ->
                val dialog = com.fuusy.project.ui.ProjectInfoBottomSheetDialogFragment().apply {
                    projectName = project.itemName
                    projectCode = project.item // 或者 project.device，根据你的业务逻辑
                    projectCompany = project.unit
                    projectLeader = project.charge
                    projectLocation = project.address
                    projectContent = project.content
                }
                dialog.show(parentFragmentManager, "ProjectInfoBottomSheetDialog")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // 暂停时停止所有播放器
        for (i in vlcPlayers.indices) {
            vlcPlayers[i]?.pause()
        }
    }

    override fun onResume() {
        super.onResume()
        // 恢复时重新播放可见的播放器
        for (i in vlcPlayers.indices) {
            if (i < currentTextureViews.size && currentTextureViews[i].isVisible) {
                vlcPlayers[i]?.play()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        isFragmentDestroyed = true

        // 释放所有播放器资源
        releaseAllPlayers()

        // 取消所有协程任务
        layoutSwitchJob?.cancel()

        // 清理引用
        currentTextureViews = arrayOf()
        currentChannels = emptyList()

        Log.d("VLCPlayer", "ProjectDetailFragment onDestroyView: 资源已释放")
    }

    override fun onDestroy() {
        super.onDestroy()
        // 确保在Fragment完全销毁时释放资源
        releaseAllPlayers()
        Log.d("VLCPlayer", "ProjectDetailFragment onDestroy: 资源已释放")
    }

    /**
     * 释放所有播放器资源
     */
    private fun releaseAllPlayers() {
        Log.d("VLCPlayer", "开始释放所有播放器资源")
        vlcPlayers.forEachIndexed { index, player ->
            player?.let {
                try {
                    Log.d("VLCPlayer", "释放播放器[$index]")
                    it.stop()
                    it.release()
                    vlcPlayers[index] = null
                } catch (e: Exception) {
                    Log.e("VLCPlayer", "释放播放器[$index]失败: ${e.message}")
                }
            }
        }
        isPlayersInitialized = false
        Log.d("VLCPlayer", "所有播放器资源已释放")
    }

    /**
     * 项目切换时的回调方法
     */
    fun onProjectSwitched(newProjectId: String) {
        Log.d("ProjectSwitch", "项目切换: $projectId -> $newProjectId")
        projectId = newProjectId

        // 解析项目标识符，显示更友好的标题
        val displayTitle = if (newProjectId.contains("_")) {
            val parts = newProjectId.split("_", limit = 2)
            "${parts[0]} (${parts[1]})"
        } else {
            newProjectId
        }
        mDataBinding.tvProjectTitle.text = displayTitle

        // 重置视频相关状态
        resetVideoState()

        // 加载新项目详情
        mViewModel.loadProjectDetail(newProjectId)

        Log.d("ProjectSwitch", "Fragment项目ID已更新: $projectId")
        Log.d(
            "ProjectSwitch",
            "Fragment arguments: ${arguments?.getString(Constants.KEY_PROJECT_ID)}"
        )
        Log.d("ProjectSwitch", "显示标题: $displayTitle")
    }

    /**
     * 重置视频相关状态
     */
    private fun resetVideoState() {
        // 释放当前播放器资源
        releaseCurrentPlayers()

        // 清空当前视频列表
        currentChannels = emptyList()
        currentTextureViews = arrayOf()

        // 重置播放器初始化状态
        isPlayersInitialized = false

        // 隐藏视频区域，等待新数据加载
        mDataBinding.cardVideoContainer.visibility = View.GONE

        Log.d("ProjectSwitch", "视频状态已重置")
    }

    /**
     * 更新连接状态显示
     */
    private fun updateConnectionStatus(isConnected: Boolean) {
        if (isConnected) {
            // 连接成功
            Log.d("WebSocket", "连接成功")
            // 可以在这里添加连接成功的UI更新
            // 例如：显示连接状态、启用相关按钮等
        } else {
            // 连接断开
            Log.d("WebSocket", "连接断开")
            // 可以在这里添加连接断开的UI更新
            // 例如：显示重连提示、禁用相关按钮等
        }

        // 更新WebSocket测试区域的连接状态显示
        val statusText = if (isConnected) "已连接" else "未连接"
        val statusColor = if (isConnected) "#4CAF50" else "#FF5722"

        mDataBinding.tvWebsocketStatus.text = statusText
        mDataBinding.tvWebsocketStatus.setTextColor(statusColor.toColorInt())
    }

    /**
     * 显示 WebSocket 错误信息
     */
    private fun showWebSocketError(error: String) {
        // 可以显示 Toast 或者在界面上显示错误信息
        showToast("WebSocket 错误: $error")
    }

    /**
     * 更新告警数量显示
     */
    private fun updateAlertCount(count: Int) {
        mDataBinding.tvAlertCount.text = count.toString()
        mDataBinding.tvAlertCount.visibility = if (count > 0) View.VISIBLE else View.GONE
    }

    /**
     * 更新告警状态（用于角标显示）
     */
    private fun updateAlarmBadge(alarmData: WebSocketMessage.AlarmData) {
        // 计算告警总数
        val totalAlarms = alarmData.co + alarmData.h2s + alarmData.o2 + alarmData.ex + alarmData.tem

        // 更新告警角标
        mDataBinding.tvAlertCount.text = totalAlarms.toString()
        mDataBinding.tvAlertCount.visibility = if (totalAlarms > 0) View.VISIBLE else View.GONE

        // 根据告警状态改变角标颜色
        if (totalAlarms > 0) {
            mDataBinding.tvAlertCount.setBackgroundResource(R.drawable.bg_delete_red)
        } else {
            mDataBinding.tvAlertCount.setBackgroundResource(R.drawable.circle_red_background)
        }

        // 可以在这里添加其他告警相关的UI更新
        // 例如：改变特定气体的显示颜色、显示告警图标等
    }

    /**
     * 更新设备信息
     */
    private fun updateDeviceInfo(deviceInfo: WebSocketMessage.GasData) {
        // 更新设备相关信息
        // 例如：显示设备ID、楼层、区域等信息

        // 更新项目标题（如果需要）
        if (deviceInfo.device.isNotEmpty()) {
            mDataBinding.tvProjectTitle.text = deviceInfo.device
        }

        // 可以在这里添加更多设备信息的显示
        // 例如：显示摄像头ID、楼层、区域等
        println("设备信息更新: 设备=${deviceInfo.device}, 楼层=${deviceInfo.floor}, 区域=${deviceInfo.region}")
    }

    /**
     * 发送测试消息到 WebSocket 服务器
     */
    private fun sendTestMessage() {
        val testMessage = mapOf(
            "type" to "test",
            "message" to "Hello from Android client",
            "timestamp" to System.currentTimeMillis()
        )
        mViewModel.sendWebSocketJsonMessage(testMessage)
    }

    /**
     * 设置 WebSocket 测试按钮
     */
    private fun setupWebSocketTestButtons() {
        // 推送模拟数据按钮
        mDataBinding.btnPushMockData.setOnClickListener {
            mViewModel.pushMockDataOnce()
            Toast.makeText(requireContext(), "已推送模拟数据", Toast.LENGTH_SHORT).show()
        }

        // 切换自动推送按钮
        var isAutoPushEnabled = true // 默认开启
        mDataBinding.btnToggleMockData.setOnClickListener {
            isAutoPushEnabled = !isAutoPushEnabled
            mViewModel.setMockDataEnabled(isAutoPushEnabled)

            val buttonText = if (isAutoPushEnabled) "关闭自动推送" else "开启自动推送"
            val toastText = if (isAutoPushEnabled) "已开启自动推送" else "已关闭自动推送"

            mDataBinding.btnToggleMockData.text = buttonText
            Toast.makeText(requireContext(), toastText, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * 重置新布局的 loading 状态
     */
    private fun resetLoadingStatesForNewLayout(videoCount: Int) {
        // 根据视频数量显示对应的 loading 容器
        for (i in 1..4) {
            val containerId = when (i) {
                1 -> R.id.loadingContainer1
                2 -> R.id.loadingContainer2
                3 -> R.id.loadingContainer3
                4 -> R.id.loadingContainer4
                else -> null
            }

            if (containerId != null) {
                val container = mDataBinding.layoutVideoContainer.findViewById<View>(containerId)
                if (i <= videoCount) {
                    // 显示对应数量的loading容器
                    container?.visibility = View.VISIBLE
                } else {
                    // 隐藏多余的loading容器
                    container?.visibility = View.GONE
                }
            }
        }

        Log.d("LoadingStates", "Reset loading states for new layout with $videoCount videos")
    }

    /**
     * 更新每个视频的独立 loading 状态
     */
    private fun updateVideoLoadingStates(loadingStates: Map<Int, Boolean>) {
        // 获取当前布局中的 loading 容器
        val loadingContainers = mutableListOf<View?>()

        // 根据当前视频数量获取对应的 loading 容器
        val videoCount = loadingStates.size
        for (i in 0 until videoCount) {
            val containerId = when (i) {
                0 -> R.id.loadingContainer1
                1 -> R.id.loadingContainer2
                2 -> R.id.loadingContainer3
                3 -> R.id.loadingContainer4
                else -> null
            }

            if (containerId != null) {
                // 每次都重新查找，确保获取到当前布局中的容器
                val container = mDataBinding.layoutVideoContainer.findViewById<View>(containerId)
                loadingContainers.add(container)

                // 调试日志
                Log.d("LoadingStates", "Video $i: container found = ${container != null}")
            }
        }

        // 更新每个 loading 容器的显示状态
        loadingStates.forEach { (index, isLoading) ->
            if (index < loadingContainers.size) {
                val container = loadingContainers[index]
                
                if (isLoading) {
                    // 显示loading时，显示loading相关的视图
                    val progressBarId = when (index) {
                        0 -> R.id.progressBar1
                        1 -> R.id.progressBar2
                        2 -> R.id.progressBar3
                        3 -> R.id.progressBar4
                        else -> null
                    }
                    val loadingTextId = when (index) {
                        0 -> R.id.tvLoading1
                        1 -> R.id.tvLoading2
                        2 -> R.id.tvLoading3
                        3 -> R.id.tvLoading4
                        else -> null
                    }

                    val progressBar = progressBarId?.let { container?.findViewById<ProgressBar>(it) }
                    val loadingText = loadingTextId?.let { container?.findViewById<TextView>(it) }

                    progressBar?.visibility = View.VISIBLE
                    loadingText?.visibility = View.VISIBLE
                    container?.visibility = View.VISIBLE
                } else {
                    // 隐藏loading时，隐藏整个容器（包括半透明黑色背景）
                    container?.visibility = View.GONE
                }

                Log.d(
                    "LoadingStates",
                    "Video $index: loading = $isLoading, container = ${container != null}, container visibility = ${container?.visibility}"
                )
            }
        }

        // 隐藏所有不在当前视频列表中的 loading 容器
        for (i in videoCount..3) {
            val containerId = when (i) {
                1 -> R.id.loadingContainer2
                2 -> R.id.loadingContainer3
                3 -> R.id.loadingContainer4
                else -> null
            }

            if (containerId != null) {
                val container = mDataBinding.layoutVideoContainer.findViewById<View>(containerId)
                container?.visibility = View.GONE
            }
        }
    }

    /**
     * 更新每个视频的错误状态和按钮显示
     */
    private fun updateVideoErrorStates(errorStates: List<Boolean>) {
        // 检查当前布局是否已经加载
        if (mDataBinding.layoutVideoContainer.childCount == 0) {
            Log.w("VideoErrorStates", "Layout not loaded yet, skipping updateVideoErrorStates")
            return
        }

        // 获取当前布局中的按钮
        val retryButtons = mutableListOf<TextView?>()

        // 根据当前视频数量获取对应的按钮
        val videoCount = errorStates.size
        Log.d("VideoErrorStates", "updateVideoErrorStates called with videoCount=$videoCount")

        for (i in 0 until videoCount) {
            val retryButtonId = when (i) {
                0 -> R.id.btnRetry1
                1 -> R.id.btnRetry2
                2 -> R.id.btnRetry3
                3 -> R.id.btnRetry4
                else -> null
            }

            if (retryButtonId != null) {
                // 每次都重新查找，确保获取到当前布局中的按钮
                val retryButton =
                    mDataBinding.layoutVideoContainer.findViewById<TextView>(retryButtonId)
                // 添加空值检查，避免NullPointerException
                if (retryButton != null) {
                    retryButtons.add(retryButton)
                    Log.d("VideoErrorStates", "Found button for video $i: $retryButtonId")

                    // 设置重试按钮的点击事件
                    setupRetryButtonClick(retryButton, i)
                    retryButton.visibility = View.VISIBLE
                } else {
                    Log.w(
                        "VideoErrorStates",
                        "Button with id $retryButtonId not found in current layout"
                    )
                }
            }
        }

        // 获取当前的loading状态
        val loadingStates = mViewModel.videoLoadingStates.value ?: emptyMap()
        Log.d("VideoErrorStates", "当前loading状态: ${loadingStates.toList().joinToString()}")
        Log.d("VideoErrorStates", "当前error状态: ${errorStates.joinToString()}")

        // 更新每个按钮的显示状态
        errorStates.forEachIndexed { index, hasError ->
            if (index < retryButtons.size && index < retryButtons.size) {
                val retryButton = retryButtons[index]
                val isLoading = loadingStates[index] ?: false
                if (isLoading) {
                    // 加载中时，两个按钮都隐藏
                    retryButton?.visibility = View.GONE
                    Log.d("ErrorStates", "Video $index: loading, hiding both buttons")
                } else if (hasError) {
                    // 有错误时显示重试按钮，隐藏加载按钮
                    retryButton?.visibility = View.VISIBLE
                    Log.d("ErrorStates", "Video $index: showing retry button, hiding load button")
                    Log.d("ErrorStates", "Video $index: retryButton visibility set to VISIBLE")

                    // 确保父容器（loadingContainer）可见，这样重试按钮才能显示
                    val parentContainer = retryButton?.parent as? View
                    parentContainer?.visibility = View.VISIBLE
                    Log.d(
                        "ErrorStates",
                        "Video $index: parent container visibility = ${parentContainer?.visibility}"
                    )
                } else {
                    // 无错误时显示加载按钮，隐藏重试按钮
                    retryButton?.visibility = View.GONE
                    Log.d("ErrorStates", "Video $index: showing load button, hiding retry button")
                }
            }
        }
    }

    /**
     * 设置重试按钮的点击事件
     */
    private fun setupRetryButtonClick(retryButton: TextView, videoIndex: Int) {
        // 移除之前的点击事件监听器，避免重复设置
        retryButton.setOnClickListener(null)

        retryButton.setOnClickListener {
            Log.d("VideoRetry", "重试按钮被点击，视频索引: $videoIndex")

            // 隐藏重试按钮，显示loading状态
            retryButton.visibility = View.GONE

            // 设置该视频为加载中状态
            mViewModel.setVideoLoadingState(videoIndex, true)
            mViewModel.setVideoErrorState(videoIndex, false)

            // 重新初始化播放器
            retryVideoStream(videoIndex)
        }
    }

    /**
     * 重试指定索引的视频流
     */
    private fun retryVideoStream(videoIndex: Int) {
        if (videoIndex >= currentChannels.size) {
            Log.w("VideoRetry", "视频索引超出范围: $videoIndex")
            return
        }

        // 检查网络连接
        if (!isNetworkAvailable()) {
            Log.e("VideoRetry", "网络不可用，无法重试视频流")
            mViewModel.setVideoLoadingState(videoIndex, false)
            mViewModel.setVideoErrorState(videoIndex, true)
            return
        }

        // 释放旧的播放器
        vlcPlayers[videoIndex]?.let { oldPlayer ->
            try {
                oldPlayer.safeStop()
                oldPlayer.safeRelease()
            } catch (e: Exception) {
                Log.e("VideoRetry", "释放旧播放器失败: ${e.message}")
            }
        }
        vlcPlayers[videoIndex] = null

        // 重新初始化播放器
        initializePlayer(videoIndex)
    }

    companion object {
        fun newInstance(): ProjectDetailFragment {
            return ProjectDetailFragment()
        }

        fun newInstance(projectId: String): ProjectDetailFragment {
            val fragment = ProjectDetailFragment()
            val args = Bundle()
            args.putString("project_id", projectId)
            fragment.arguments = args
            return fragment
        }
    }
}