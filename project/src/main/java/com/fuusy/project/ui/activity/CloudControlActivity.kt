package com.fuusy.project.ui.activity

import android.content.Intent
import android.graphics.SurfaceTexture
import com.fuusy.project.R
import com.fuusy.project.databinding.ActivityCloudControlBinding
import com.fuusy.project.viewmodel.CloudControlViewModel
import androidx.lifecycle.ViewModelProvider
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.common.support.StatusBar
import com.fuusy.common.utils.IntercomController
import com.fuusy.common.utils.PtzHelper
import com.fuusy.common.utils.FileUtils
import com.fuusy.project.ui.VLCPlayer
import com.fuusy.project.ui.ExoLivePlayer
import com.fuusy.project.ui.StreamUrlResolver
import com.fuusy.project.VLCScreenRecorder
import com.fuusy.project.VLCRecordingMonitor
import com.fuusy.project.utils.MediaStoreUtils
import com.fuusy.project.utils.LoadingDialogUtils
import java.io.File
import android.util.Log
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.fuusy.common.network.ServerConfig
import androidx.activity.OnBackPressedCallback
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Job


class CloudControlActivity : BaseVmActivity<ActivityCloudControlBinding>() {
    private lateinit var textureView: android.view.TextureView
    private lateinit var viewModel: CloudControlViewModel
    private lateinit var intercomController: IntercomController
    private var isTalking = false
    private var isRecording = false
    private var player: VLCPlayer? = null
    private var exoLivePlayer: ExoLivePlayer? = null
    private var isMuted = true

    private lateinit var vlcScreenRecorder: VLCScreenRecorder
    private lateinit var recordingMonitor: VLCRecordingMonitor

    private var streamUrl: String? = null
    private var deviceInfo: DeviceInfo? = null

    private var currentPlayJob: Job? = null
    private var playbackCandidates: List<StreamUrlResolver.PlaybackTarget> = emptyList()
    private var playbackCandidateIndex = 0
    private var openedFullScreen = false
    private var isExiting = false
    private var vibrator: android.os.Vibrator? = null

    data class DeviceInfo(
        val cameraip: String,
        val channelId: String,
        val deviceId: String,
        val region: String
    )

    override fun getLayoutId(): Int = R.layout.activity_cloud_control

    override fun initData() {

        viewModel = ViewModelProvider(
            this, ViewModelProvider.AndroidViewModelFactory.getInstance(application)
        ).get(CloudControlViewModel::class.java)

        vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator

        streamUrl = intent.getStringExtra("streamUrl")
            ?: intent.getStringArrayListExtra("stream_urls")?.firstOrNull()
            ?: intent.getStringExtra("videoPath")

        val cameraip = intent.getStringExtra("cameraip") ?: ""
        val channelId = intent.getStringExtra("channelId") ?: ""
        val deviceId = intent.getStringExtra("deviceId") ?: ""
        val region = intent.getStringExtra("region") ?: ""
        deviceInfo = DeviceInfo(cameraip, channelId, deviceId, region)

        viewModel.setVideoInfo(cameraip, channelId, deviceId, region)
        streamUrl?.let { viewModel.setChannelUrls(listOf(it)) }

        applyStatusBarPadding()
        textureView = mBinding.textureView
        setupHeader()
        setupBackHandler()

        Log.d("VideoControl", "CloudControlActivity初始化: streamUrl=$streamUrl")
        vlcScreenRecorder = VLCScreenRecorder(this)
        vlcScreenRecorder.setCallback(object : VLCScreenRecorder.VLCRecorderCallback {
            override fun onRecordingStarted() {
                updateRecordingUi(true)
                showToast("开始录屏...")
                recordingMonitor.startMonitoring()
            }

            override fun onRecordingStopped(videoPath: String?, success: Boolean) {
                updateRecordingUi(false)
                recordingMonitor.stopMonitoring()

                // 保存录像文件到系统相册
                if (success && !videoPath.isNullOrEmpty()) {
                    Log.d("CloudControl", "录像完成，文件路径: $videoPath")
                    val videoFile = File(videoPath)
                    if (videoFile.exists()) {
                        Log.d("CloudControl", "录像文件存在，大小: ${videoFile.length()} bytes")
                        LoadingDialogUtils.showLoading(this@CloudControlActivity, "保存录像中...")
                        // 在后台线程处理保存
                        Thread {
                            val fileName =
                                MediaStoreUtils.generateFileName("视频录像", "mp4")
                            Log.d("CloudControl", "开始保存录像到相册: $fileName")
                            val saveSuccess = MediaStoreUtils.saveVideoToAlbum(
                                this@CloudControlActivity,
                                videoFile,
                                fileName
                            )
                            runOnUiThread {
                                LoadingDialogUtils.hideLoading()
                                if (saveSuccess) {
                                    showToast("录制成功，已保存到相册")
                                } else {
                                    showToast("录制成功，但保存到相册失败")
                                }
                            }
                        }.start()
                    } else {
                        Log.e("CloudControl", "录像文件不存在: ${videoFile.absolutePath}")
                        showToast("录制成功，但文件不存在")
                    }
                } else {
                    Log.e(
                        "CloudControl",
                        "录像失败或路径为空: success=$success, videoPath=$videoPath"
                    )
                    showToast("录制成功")
                }
            }

            override fun onTimeUpdate(timeString: String) {
                // 可以在这里更新录屏时间显示
                android.util.Log.d("VLCRecorder", "录屏时间: $timeString")
            }

            override fun onError(error: String) {
                updateRecordingUi(false)
                recordingMonitor.stopMonitoring()
            }
        })

        // 初始化录制状态监控器
        recordingMonitor = VLCRecordingMonitor(this)
        recordingMonitor.setRecordDirectory(FileUtils.getAppPath())
        recordingMonitor.setCallback(object : VLCRecordingMonitor.RecordingMonitorCallback {
            override fun onRecordingStarted(recordFile: File) {
                android.util.Log.d("RecordingMonitor", "检测到录制开始: ${recordFile.name}")
                showToast("录制文件已创建: ${recordFile.name}")
            }

            override fun onRecordingProgress(recordFile: File, currentSize: Long, duration: Long) {
                val sizeMB = currentSize / (1024 * 1024)
                val durationSec = duration / 1000
                android.util.Log.d(
                    "RecordingMonitor",
                    "录制进行中: ${recordFile.name}, 大小: ${sizeMB}MB, 时长: ${durationSec}秒"
                )

                // 可以在这里更新UI显示录制进度
                updateRecordingProgress(sizeMB, durationSec)
            }

            override fun onRecordingStopped(recordFile: File, finalSize: Long) {
                val sizeMB = finalSize / (1024 * 1024)
                android.util.Log.d(
                    "RecordingMonitor",
                    "录制已停止: ${recordFile.name}, 最终大小: ${sizeMB}MB"
                )
                showToast("录制完成: ${recordFile.name}, 大小: ${sizeMB}MB")
            }

            override fun onRecordingError(error: String) {
                android.util.Log.e("RecordingMonitor", "录制监控错误: $error")
                showToast("录制监控错误: $error")
            }
        })

        initializeRetryButton()

        textureView.surfaceTextureListener =
            object : android.view.TextureView.SurfaceTextureListener {
                override fun onSurfaceTextureAvailable(
                    surface: SurfaceTexture, width: Int, height: Int
                ) {
                    Log.d("CloudControl", "onSurfaceTextureAvailable: w=$width, h=$height, visibility=${textureView.visibility}")
                    playCurrentChannel()
                }

                override fun onSurfaceTextureSizeChanged(
                    surface: SurfaceTexture, width: Int, height: Int
                ) {
                    Log.d("CloudControl", "onSurfaceTextureSizeChanged: w=$width, h=$height")
                }

                override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                    Log.d("CloudControl", "onSurfaceTextureDestroyed. Releasing player.")
                    releaseExoPlayer()
                    player?.safeRelease()
                    player = null
                    return true
                }

                override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
                    // 通常不需要处理
                }
            }
        if (textureView.isAvailable) {
            playCurrentChannel()
        }

        mBinding.btnCapture.setOnClickListener {
            captureSnapshot()
        }
        mBinding.btnFullscreen.setOnClickListener { openFullScreen() }
        textureView.setOnClickListener { openFullScreen() }
        mBinding.btnTalk.setOnClickListener {
            vibrator?.vibrate(30)
            intercomController.toggleIntercom()
        }
        mBinding.btnBack.setOnClickListener { exitPage() }
        mBinding.btnPlayback.setOnClickListener {
            showToast("回放功能即将上线")
        }
        mBinding.btnRecord.setOnClickListener {
            vibrator?.vibrate(30)
            if (!isRecording) {
                vlcScreenRecorder.startRecording(1, textureView)
            } else {
                vlcScreenRecorder.stopRecording()
            }
        }
        mBinding.btnPtzLock.setOnClickListener {
            showToast("PTZ 已锁定")
        }
        // ====== 云台控制真实参数（从设备信息获取） ======
        // 使用从Intent获取的设备信息，而不是重新声明
        val accessToken =
            "eyJhbGciOiJSUzI1NiIsImtpZCI6IjNlNzk2NDZjNGRiYzQwODM4M2E5ZWVkMDlmMmI4NWFlIn0.eyJqdGkiOiJjdkdhNHZmMGlBc3NOdzNxNEhDTEFRIiwiaWF0IjoxNzUxMzQ5MjExLCJleHAiOjE3NTM5NDEyMTEsIm5iZiI6MTc1MTM0OTIxMSwic3ViIjoibG9naW4iLCJhdWQiOiJBdWRpZW5jZSIsInVzZXJOYW1lIjoiYWRtaW4ifQ.Es4_i_xao2Bv5EJlWlnBmvjmK0OHUKoTU6QfvBcyxbxi8v60PzEg34CcEgnx6iyryBLt054dD20gzV9A6y73bzXHsVU2aCqzliohpm34yAvVbufpS5gDhsO5pRuwA4QuVC8s4Dwrew1xgPc7f9kezOthYfArUmiHHTYMw2l90H0KoOZiA2kcUimDw0EhzlohylP5Mf1kvL9YniMxmqOJxg5o9hFdOcJfO0zWzUaqtKLf0fctEdgH0wK9Zey1N05_FhZ4jJfgczw1miWXBDWFoq54sCNxNVFBuJL_iq70oWVVoLsH-5_Vd-gBZkHM5tDTIBYGEkmt3mVOzmuQPgEhkw" // 可配置
        mBinding.btnUp.setOnTouchListener(
            PtzHelper.getPtzTouchListener(
                onControlPtz = { command, onResult ->
                    viewModel.controlPtz(
                        viewModel.getCurrentCameraip(), viewModel.getCurrentChannelId(), command, accessToken = accessToken, onResult = onResult
                    )
                },
                command = "up",
                showToast = ::showToast,
                buttonView = mBinding.btnUp,
                pressedResId = R.drawable.bg_ptz_btn_pressed,
                normalResId = R.drawable.bg_ptz_btn,
                vibrator = vibrator
            )
        )
        mBinding.btnDown.setOnTouchListener(
            PtzHelper.getPtzTouchListener(
                onControlPtz = { command, onResult ->
                    viewModel.controlPtz(
                        viewModel.getCurrentCameraip(), viewModel.getCurrentChannelId(), command, accessToken = accessToken, onResult = onResult
                    )
                },
                command = "down",
                showToast = ::showToast,
                buttonView = mBinding.btnDown,
                pressedResId = R.drawable.bg_ptz_btn_pressed,
                normalResId = R.drawable.bg_ptz_btn,
                vibrator = vibrator
            )
        )
        mBinding.btnLeft.setOnTouchListener(
            PtzHelper.getPtzTouchListener(
                onControlPtz = { command, onResult ->
                    viewModel.controlPtz(
                        viewModel.getCurrentCameraip(), viewModel.getCurrentChannelId(), command, accessToken = accessToken, onResult = onResult
                    )
                },
                command = "left",
                showToast = ::showToast,
                buttonView = mBinding.btnLeft,
                pressedResId = R.drawable.bg_ptz_btn_pressed,
                normalResId = R.drawable.bg_ptz_btn,
                vibrator = vibrator
            )
        )
        mBinding.btnRight.setOnTouchListener(
            PtzHelper.getPtzTouchListener(
                onControlPtz = { command, onResult ->
                    viewModel.controlPtz(
                        viewModel.getCurrentCameraip(), viewModel.getCurrentChannelId(), command, accessToken = accessToken, onResult = onResult
                    )
                },
                command = "right",
                showToast = ::showToast,
                buttonView = mBinding.btnRight,
                pressedResId = R.drawable.bg_ptz_btn_pressed,
                normalResId = R.drawable.bg_ptz_btn,
                vibrator = vibrator
            )
        )

        viewModel.currentChannel.observe(this) {
            playCurrentChannel()
        }

        // ====== 对讲控制器初始化 ======
        intercomController = IntercomController(
            baseUrlAndPort= ServerConfig.getYunBaseUrl(),
            activity = this,
            sn = viewModel.getCurrentCameraip(), // 使用cameraip作为sn
            chid = viewModel.getCurrentChannelId(), // 使用channelId作为chid
            pushKey = "admin123",
            onStateChanged = { isPushing ->
                isTalking = isPushing
                if (isPushing) {
                    showToast("对讲已开启")
                } else {
                    showToast("对讲已关闭")
                }
            },
            onLog = { msg -> android.util.Log.e("Intercom", msg) })
    }

    private fun setupBackHandler() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                exitPage()
            }
        })
    }

    private fun isPageActive(): Boolean =
        !isExiting && !isFinishing && !isDestroyed

    private fun exitPage() {
        if (isExiting) return
        isExiting = true
        Log.d("CloudControl", "用户退出，停止播放并关闭页面")
        stopPlayback()
        finish()
    }

    private fun stopPlayback() {
        currentPlayJob?.cancel()
        currentPlayJob = null
        mBinding.loadingContainer.visibility = View.GONE
        releaseExoPlayer()
        releaseVlcPlayer()
    }

    /** 设计稿：状态栏 62px 后标题栏从 y=65 起，预留系统状态栏 + 3dp */
    private fun applyStatusBarPadding() {
        StatusBar().lightStatusBar(this, false)
        val extraTopPx = (3 * resources.displayMetrics.density).toInt()
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.cloudContent) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarTop + extraTopPx)
            insets
        }
        ViewCompat.requestApplyInsets(mBinding.cloudContent)
    }

    private fun openFullScreen() {
        vibrator?.vibrate(30)
        openedFullScreen = true
        currentPlayJob?.cancel()
        releaseExoPlayer()
        releaseVlcPlayer()

        val currentUrl = streamUrl ?: viewModel.getCurrentUrl()
        val intent = Intent(this, VideoFullScreenActivity::class.java).apply {
            putExtra("streamUrl", currentUrl)
            putExtra("cameraip", viewModel.getCurrentCameraip())
            putExtra("channelId", viewModel.getCurrentChannelId())
            putExtra("deviceId", viewModel.getCurrentDeviceId())
            putExtra("region", viewModel.getCurrentRegion())
            putExtra("channelIndex", 0)
            putExtra("channelCount", 1)
            putExtra("channelName", mBinding.tvTitle.text.toString())
        }
        startActivity(intent)
    }

    override fun onPause() {
        super.onPause()
        if (isExiting || isFinishing) return
        if (!openedFullScreen) {
            stopPlayback()
        }
    }

    override fun onResume() {
        super.onResume()
        if (isExiting || isFinishing) return
        if (openedFullScreen) {
            openedFullScreen = false
        }
        if (textureView.isAvailable) {
            playCurrentChannel()
        }
    }

    /**
     * 初始化重试按钮的点击事件
     */
    private fun initializeRetryButton() {
        mBinding.btnRetry?.let { retryButton ->
            retryButton.setOnClickListener {
                Log.d("CloudControl", "重试按钮被点击")
                // 隐藏重试按钮，显示loading
                retryButton.visibility = View.INVISIBLE
                val linearLayout = mBinding.loadingContainer.getChildAt(0) as? android.widget.LinearLayout
                linearLayout?.let { layout ->
                    val progressBar = layout.getChildAt(0) as? android.widget.ProgressBar
                    val loadingText = layout.getChildAt(1) as? android.widget.TextView
                    progressBar?.visibility = View.VISIBLE
                    loadingText?.visibility = View.VISIBLE
                }
                // 重新播放当前通道
                playCurrentChannel()
            }
            Log.d("CloudControl", "重试按钮点击事件已设置")
        } ?: run {
            Log.w("CloudControl", "重试按钮未找到")
        }
    }

    /**
     * 异步释放播放器资源，主线程解绑 Surface，子线程释放 native 资源
     */
    private fun releasePlayerAsync(player: VLCPlayer) {
        // 1. 主线程解绑 Surface，判空
        runOnUiThread {
            try {
                if (textureView != null && textureView.isAvailable) {
                    player.setVideoSurface(null)
                }
            } catch (e: Exception) {
                Log.e("VLCPlayer", "releasePlayerAsync: setVideoSurface null error: ${e.message}")
            }
        }
        // 2. 子线程释放 native 资源
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                withTimeout(5000) { // 5秒超时
                    player.safeStop()
                    player.safeRelease()
                }
            } catch (e: Exception) {
                Log.e("VLCPlayer", "releasePlayerAsync: 异步释放异常: ${e.message}")
            }
        }
    }

    private fun playCurrentChannel() {
        if (!isPageActive()) return
        val url = streamUrl ?: viewModel.getCurrentUrl()
        Log.d("CloudControl", "Current URL to play: $url")

        if (url.isNullOrEmpty()) {
            Log.w("CloudControl", "URL is null or empty, cannot play.")
            mBinding.loadingContainer.visibility = View.GONE
            return
        }

        if (!textureView.isAvailable) {
            Log.w("CloudControl", "TextureView is NOT available, waiting for surface.")
            mBinding.loadingContainer.visibility = View.VISIBLE
            return
        }

        playbackCandidates = StreamUrlResolver.fallbackTargets(url)
        if (playbackCandidates.isEmpty()) {
            Log.w("CloudControl", "无法解析播放地址")
            showPlayError()
            return
        }

        currentPlayJob?.cancel()
        showPlayLoading()
        playbackCandidateIndex = 0
        playNextCandidate()
    }

    private fun playNextCandidate() {
        if (!isPageActive()) return
        if (playbackCandidateIndex >= playbackCandidates.size) {
            Log.e("CloudControl", "所有播放候选均失败，共 ${playbackCandidates.size} 个")
            showPlayError()
            return
        }
        val target = playbackCandidates[playbackCandidateIndex++]
        Log.d(
            "CloudControl",
            "尝试播放 ($playbackCandidateIndex/${playbackCandidates.size}) ${target.label}: ${target.url}"
        )
        if (target.useExo) {
            playWithExo(target.url, ::onCandidateFailed)
        } else {
            playWithVlc(target.url, ::onCandidateFailed)
        }
    }

    private fun onCandidateFailed() {
        if (!isPageActive()) return
        if (playbackCandidateIndex < playbackCandidates.size) {
            Log.w("CloudControl", "当前候选失败，尝试下一个")
            playNextCandidate()
        } else {
            showPlayError()
        }
    }

    private fun showPlayLoading() {
        mBinding.loadingContainer.visibility = View.VISIBLE
        val linearLayout = mBinding.loadingContainer.getChildAt(0) as? android.widget.LinearLayout
        linearLayout?.let { layout ->
            (layout.getChildAt(0) as? android.widget.ProgressBar)?.visibility = View.VISIBLE
            (layout.getChildAt(1) as? android.widget.TextView)?.visibility = View.VISIBLE
        }
        mBinding.btnRetry?.visibility = View.INVISIBLE
    }

    private fun showPlayError() {
        val linearLayout = mBinding.loadingContainer.getChildAt(0) as? android.widget.LinearLayout
        linearLayout?.let { layout ->
            (layout.getChildAt(0) as? android.widget.ProgressBar)?.visibility = View.GONE
            (layout.getChildAt(1) as? android.widget.TextView)?.visibility = View.GONE
        }
        mBinding.btnRetry?.visibility = View.VISIBLE
    }

    private fun onPlaySuccess() {
        if (!isPageActive()) return
        mBinding.loadingContainer.visibility = View.GONE
        mBinding.btnRetry?.visibility = View.INVISIBLE
    }

    private fun releaseExoPlayer() {
        exoLivePlayer?.release()
        exoLivePlayer = null
    }

    private fun releaseVlcPlayer() {
        player?.let { oldPlayer ->
            try {
                oldPlayer.safeStop()
                oldPlayer.safeRelease()
            } catch (e: Exception) {
                Log.e("CloudControl", "释放 VLC 失败: ${e.message}")
            }
        }
        player = null
        viewModel.setVlcPlayer(null)
    }

    private fun playWithExo(playbackUrl: String, onFail: () -> Unit) {
        releaseVlcPlayer()
        releaseExoPlayer()
        exoLivePlayer = ExoLivePlayer(this, textureView).also { exo ->
            exo.setCallback(object : ExoLivePlayer.Callback {
                override fun onPlaying() {
                    if (!isPageActive()) return
                    Log.d("CloudControl", "ExoPlayer 开始播放")
                    onPlaySuccess()
                }

                override fun onError(message: String?) {
                    if (!isPageActive()) return
                    Log.e("CloudControl", "ExoPlayer 播放错误: $message")
                    releaseExoPlayer()
                    onFail()
                }
            })
            exo.play(playbackUrl)
        }
    }

    private fun playWithVlc(url: String, onFail: () -> Unit) {
        releaseExoPlayer()
        currentPlayJob = lifecycleScope.launch {
            try {
                withTimeout(15_000) {
                    withContext(Dispatchers.Main) {
                        if (!isPageActive()) return@withContext
                        Log.d("CloudControl", "开始初始化 VLC 播放器")
                        releaseVlcPlayer()

                        val newPlayer = VLCPlayer(this@CloudControlActivity)
                        newPlayer.setVideoSurface(textureView)
                        newPlayer.setVolume(if (isMuted) 0 else 100)
                        newPlayer.setCallback(object : VLCPlayer.VLCPlayerCallback {
                            override fun onError() {
                                if (!isPageActive()) return
                                Log.e("CloudControl", "VLC 播放错误: $url")
                                releaseVlcPlayer()
                                onFail()
                            }

                            override fun playing() {
                                if (!isPageActive()) return
                                Log.d("CloudControl", "播放器开始播放")
                                onPlaySuccess()
                            }

                            override fun onBuffering(bufferPercent: Float) {
                                if (!isPageActive()) return
                                if (bufferPercent > 95f) {
                                    onPlaySuccess()
                                }
                            }

                            override fun onEndReached() {
                                Log.d("CloudControl", "播放器播放结束")
                            }

                            override fun onTimeChanged(time: Long) = Unit

                            override fun onPositionChanged(position: Float) = Unit
                        })
                        newPlayer.setDataSource(url)
                        player = newPlayer
                        viewModel.setVlcPlayer(player)
                        player?.let { vlcScreenRecorder.setVlcPlayer(it) }
                        player?.play()
                        Log.d("CloudControl", "VLC play() 已调用")
                    }
                }
            } catch (e: Exception) {
                if (!isPageActive()) return@launch
                Log.e("CloudControl", "播放器初始化失败: ${e.message}")
                withContext(Dispatchers.Main) {
                    releaseVlcPlayer()
                    if (e is kotlinx.coroutines.TimeoutCancellationException) {
                        Log.w("CloudControl", "VLC 加载超时: $url")
                    }
                    onFail()
                }
            }
        }
    }
    private fun setupHeader() {
        val title = intent.getStringExtra("show_name")
            ?: intent.getStringExtra("channelName")
            ?: "1号炉A侧入口"
        val subtitle = intent.getStringExtra("location")
            ?: intent.getStringExtra("region")
            ?: "A侧低过三级人孔门"
        mBinding.tvTitle.text = title
        mBinding.tvSubtitle.text = subtitle
        mBinding.tvGasData.text = "20.9 %\n8.5 ppm\n3.2 ppm"
    }

    private fun updateRecordingUi(recording: Boolean) {
        isRecording = recording
        mBinding.viewRecordDot.visibility = if (recording) View.VISIBLE else View.GONE
        mBinding.tvRecordLabel.text = if (recording) "录制中" else "录制"
    }

    private fun captureSnapshot() {
        val bitmap = mBinding.textureView.bitmap
        val name = mBinding.tvTitle.text.toString().ifBlank { "视频截图" }
        if (bitmap != null) {
            LoadingDialogUtils.showLoading(this, "保存照片中...")
            Thread {
                val fileName = MediaStoreUtils.generateFileName(name, "jpg")
                val success = MediaStoreUtils.savePhotoToAlbum(this@CloudControlActivity, bitmap, fileName)
                runOnUiThread {
                    LoadingDialogUtils.hideLoading()
                    showToast(if (success) "截图成功，已保存到相册" else "截图失败，保存文件时出错")
                }
            }.start()
        } else {
            showToast("截图失败，未获取到画面")
        }
    }

    override fun onDestroy() {
        isExiting = true
        super.onDestroy()
        Log.d("CloudControl", "onDestroy called. Releasing player.")
        stopPlayback()
        LoadingDialogUtils.hideLoading()
    }
    /**
     * 更新录制进度显示
     */
    private fun updateRecordingProgress(sizeMB: Long, durationSec: Long) {
        // 在主线程更新UI
        runOnUiThread {
            // 可以在这里更新录制进度UI
            // 例如：显示录制大小和时长
            val progressText = "录制中: ${sizeMB}MB / ${durationSec}秒"
            android.util.Log.d("RecordingProgress", progressText)

            // 如果有录制进度显示控件，可以在这里更新
            // mBinding.tvRecordingProgress.text = progressText
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                intercomController.startIntercom()
            } else {
                showToast("麦克风权限被拒绝，无法对讲")
            }
        }
    }
} 