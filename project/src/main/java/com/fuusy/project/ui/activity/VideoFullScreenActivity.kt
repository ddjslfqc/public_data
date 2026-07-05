package com.fuusy.project.ui.activity

import android.content.ContentValues
import android.content.pm.ActivityInfo
import android.graphics.SurfaceTexture
import android.os.Build
import android.os.Bundle
import android.view.TextureView
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.activity.viewModels
import com.fuusy.project.R
import com.fuusy.project.databinding.ActivityVideoFullScreenBinding
import com.fuusy.project.viewmodel.VideoFullScreenViewModel
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.common.utils.ToastUtil
import com.fuusy.project.ui.VLCPlayer
import com.fuusy.project.ui.ExoLivePlayer
import com.fuusy.project.ui.StreamUrlResolver
import android.graphics.Bitmap
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.app.Activity
import android.media.projection.MediaProjectionManager
import android.util.Log
import com.fuusy.common.utils.IntercomController
import com.fuusy.common.network.ServerConfig
import com.fuusy.common.utils.PtzHelper
import com.fuusy.common.utils.FileUtils
import com.fuusy.project.VLCScreenRecorder
import com.fuusy.project.VLCRecordingMonitor
import com.fuusy.project.utils.MediaStoreUtils
import com.fuusy.project.utils.LoadingDialogUtils
import java.io.File
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.Job


class VideoFullScreenActivity : BaseVmActivity<ActivityVideoFullScreenBinding>() {
    private var vlcPlayer: VLCPlayer? = null
    private var exoLivePlayer: ExoLivePlayer? = null
    private lateinit var textureView: TextureView
    private val viewModel: VideoFullScreenViewModel by viewModels()
    private var isMuted = true
    private var isRecording = false
    private var videoPath: String? = null
    private lateinit var intercomController: IntercomController
    private var tvChannelName: TextView? = null
    private var isTalking = false
    
    // 通道信息
    private var channelIndex: Int = 0
    private var channelCount: Int = 1
    private var channelName: String = "通道1"

    // VLC 原生录屏工具
    private lateinit var vlcScreenRecorder: VLCScreenRecorder

    // 录制状态监控器
    private lateinit var recordingMonitor: VLCRecordingMonitor

    // 播放任务管理，用于取消之前的播放任务
    private var currentPlayJob: Job? = null
    private var playbackCandidates: List<StreamUrlResolver.PlaybackTarget> = emptyList()
    private var playbackCandidateIndex = 0

    override fun getLayoutId(): Int = R.layout.activity_video_full_screen

    override fun onCreate(savedInstanceState: Bundle?) {
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        super.onCreate(savedInstanceState)
    }

    override fun initData() {
        // 初始化震动器
        val vibrator = getSystemService(android.content.Context.VIBRATOR_SERVICE) as? android.os.Vibrator
        
        vlcScreenRecorder = VLCScreenRecorder(this)
        vlcScreenRecorder.setCallback(object : VLCScreenRecorder.VLCRecorderCallback {
            override fun onRecordingStarted() {
                isRecording = true
                mBinding.btnRecord.setImageResource(R.mipmap.bg_video)
                showToast("开始录屏...")

                // 显示计时器并重置
                mBinding.tvRecordTimer.visibility = View.VISIBLE
                mBinding.tvRecordTimer.text = "00:00:00"
                Log.d("VLCRecorder", "计时器已显示")

                // 开始监控录制状态
                recordingMonitor.startMonitoring()
            }

            override fun onRecordingStopped(videoPath: String?, success: Boolean) {
                isRecording = false
                mBinding.btnRecord.setImageResource(R.mipmap.bg_video)

                // 隐藏计时器
                mBinding.tvRecordTimer.visibility = View.GONE
                Log.d("VLCRecorder", "计时器已隐藏")

                // 停止监控录制状态
                recordingMonitor.stopMonitoring()

                // 保存录像文件到系统相册
                if (success && !videoPath.isNullOrEmpty()) {
                    Log.d("VideoFullScreen", "录像完成，文件路径: $videoPath")
                    val videoFile = File(videoPath)
                    if (videoFile.exists()) {
                        Log.d("VideoFullScreen", "录像文件存在，大小: ${videoFile.length()} bytes")
                        LoadingDialogUtils.showLoading(
                            this@VideoFullScreenActivity,
                            "保存录像中..."
                        )
                        
                        // 在后台线程处理保存
                        Thread {
                            val fileName = MediaStoreUtils.generateFileName("${channelName}_录像", "mp4")
                            Log.d("VideoFullScreen", "开始保存录像到相册: $fileName")
                            val saveSuccess = MediaStoreUtils.saveVideoToAlbum(
                                this@VideoFullScreenActivity,
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
                        Log.e("VideoFullScreen", "录像文件不存在: ${videoFile.absolutePath}")
                        showToast("录制成功，但文件不存在")
                    }
                } else {
                    Log.e(
                        "VideoFullScreen",
                        "录像失败或路径为空: success=$success, videoPath=$videoPath"
                    )
                    showToast("录制成功")
                }
            }

            override fun onTimeUpdate(timeString: String) {
                // 每秒刷新计时器
                mBinding.tvRecordTimer.text = timeString
                Log.d("VLCRecorder", "录屏时间: $timeString")
            }

            override fun onError(error: String) {
                isRecording = false
                mBinding.btnRecord.setImageResource(R.mipmap.bg_video)

                // 隐藏计时器
                mBinding.tvRecordTimer.visibility = View.GONE
                Log.d("VLCRecorder", "计时器已隐藏(出错)")

                // 停止监控录制状态
                recordingMonitor.stopMonitoring()

                showToast(error)
            }
        })

        // 初始化录制状态监控器
        recordingMonitor = VLCRecordingMonitor(this)
        recordingMonitor.setRecordDirectory(FileUtils.getAppPath())
        recordingMonitor.setCallback(object : VLCRecordingMonitor.RecordingMonitorCallback {
            override fun onRecordingStarted(recordFile: File) {
                Log.d("RecordingMonitor", "检测到录制开始: ${recordFile.name}")
                showToast("录制文件已创建: ${recordFile.name}")
            }

            override fun onRecordingProgress(recordFile: File, currentSize: Long, duration: Long) {
                val sizeMB = currentSize / (1024 * 1024)
                val durationSec = duration / 1000
                Log.d(
                    "RecordingMonitor",
                    "录制进行中: ${recordFile.name}, 大小: ${sizeMB}MB, 时长: ${durationSec}秒"
                )

                // 可以在这里更新UI显示录制进度
                updateRecordingProgress(sizeMB, durationSec)
            }

            override fun onRecordingStopped(recordFile: File, finalSize: Long) {
                val sizeMB = finalSize / (1024 * 1024)
                Log.d("RecordingMonitor", "录制已停止: ${recordFile.name}, 最终大小: ${sizeMB}MB")
                showToast("录制完成: ${recordFile.name}, 大小: ${sizeMB}MB")
            }

            override fun onRecordingError(error: String) {
                Log.e("RecordingMonitor", "录制监控错误: $error")
                showToast("录制监控错误: $error")
            }
        })

        // 设置沉浸式全屏
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            window.insetsController?.systemBarsBehavior =
                WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            @Suppress("DEPRECATION") window.decorView.systemUiVisibility =
                (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
        }

        val streamUrl = intent.getStringExtra("streamUrl")?.trim().orEmpty()
        viewModel.streamUrl.value = streamUrl
        
        // 获取设备信息
        val cameraip = intent.getStringExtra("cameraip") ?: ""
        val channelId = intent.getStringExtra("channelId") ?: ""
        val deviceId = intent.getStringExtra("deviceId") ?: ""
        val region = intent.getStringExtra("region") ?: ""
        
        Log.d("VideoControl", "VideoFullScreenActivity初始化: streamUrl=$streamUrl")
        Log.d("VideoControl", "VideoFullScreenActivity设备信息: cameraip=$cameraip, channelId=$channelId, deviceId=$deviceId, region=$region")
        
        // 设置设备信息到ViewModel
        viewModel.setVideoInfo(cameraip, channelId, deviceId, region)
        
        // 获取通道信息
        channelIndex = intent.getIntExtra("channelIndex", 0)
        channelCount = intent.getIntExtra("channelCount", 1)
        channelName = intent.getStringExtra("channelName") ?: "通道${channelIndex + 1}"
        
        Log.d("VideoControl", "VideoFullScreenActivity通道信息: channelIndex=$channelIndex, channelCount=$channelCount, channelName=$channelName")
        
        textureView = mBinding.textureView
        tvChannelName = mBinding.tvChannelName
        
        // 设置通道名称显示
        tvChannelName?.text = channelName
        mBinding.btnBack.setOnClickListener { finish() }
        mBinding.btnReverse.setOnClickListener {
            // 添加震动反馈
            vibrator?.vibrate(30)
            PtzHelper.reverseVideo(mBinding.textureView)
            showToast("画面反转")
        }
        mBinding.btnMute.setImageResource(
            if (isMuted) R.mipmap.ic_mute else R.mipmap.mute_up
        )
        mBinding.btnMute.setOnClickListener {
            // 添加震动反馈
            vibrator?.vibrate(30)
            isMuted = !isMuted
            if (isMuted) {
                vlcPlayer?.setVolume(0)
                mBinding.btnMute.setImageResource(R.mipmap.ic_mute)
            } else {
                vlcPlayer?.setVolume(100)
                mBinding.btnMute.setImageResource(R.mipmap.mute_up)
            }
        }
        mBinding.btnTalk.setImageResource(
            if (isTalking) R.mipmap.duijiang_open else R.mipmap.duijiang_close
        )
        mBinding.btnTalk.setOnClickListener {
            // 添加震动反馈
            vibrator?.vibrate(30)
            intercomController.toggleIntercom()
        }
        mBinding.btnCapture.setOnClickListener {
            // 添加震动反馈
            vibrator?.vibrate(30)
            val bitmap = mBinding.textureView.bitmap
            if (bitmap != null) {
                LoadingDialogUtils.showLoading(this, "保存照片中...")
                // 在后台线程处理保存
                Thread {
                    val fileName = MediaStoreUtils.generateFileName(channelName, "jpg")
                    val success = MediaStoreUtils.savePhotoToAlbum(
                        this@VideoFullScreenActivity,
                        bitmap,
                        fileName
                    )
                    runOnUiThread {
                        LoadingDialogUtils.hideLoading()
                        if (success) {
                            showToast("拍照成功，已保存到相册")
                        } else {
                            showToast("拍照失败，保存文件时出错")
                        }
                    }
                }.start()
            } else {
                showToast("拍照失败，未获取到画面")
            }
        }
        mBinding.btnRecord.setOnClickListener {
            // 添加震动反馈
            vibrator?.vibrate(30)
            if (!isRecording) {
                // 直接开始 VLC 原生录制，无需权限请求
                startScreenRecording()
            } else {
                stopScreenRecording()
            }
        }

        // 控件显示/隐藏切换逻辑
        var controlsVisible = true
        val topBar = mBinding.topBarLayout
        val rightBar = mBinding.rightBarLayout
        val bottomBar = mBinding.bottomBarLayout
        val ptzControl = mBinding.ptzControl

        textureView.setOnClickListener {
            controlsVisible = !controlsVisible
            val visibility = if (controlsVisible) View.VISIBLE else View.GONE
            topBar.visibility = visibility
            rightBar.visibility = visibility
            bottomBar.visibility = visibility
            ptzControl.visibility = visibility
        }
        
        // 使用从设备信息中获取的参数
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
                buttonView = mBinding.ptzControl,
                normalResId = R.mipmap.bg_player_contorl_full,
                pressedResId = R.mipmap.full_top,
                vibrator = vibrator
            )
        )
        mBinding.btnDown.setOnTouchListener(
            PtzHelper.getPtzTouchListener(
                onControlPtz = { command, onResult ->
                    viewModel.controlPtz(
                        viewModel.getCurrentCameraip(), viewModel.getCurrentChannelId(), command, accessToken = accessToken, onResult = onResult
                    )
                }, command = "down", showToast = ::showToast,
                buttonView = mBinding.ptzControl,
                normalResId = R.mipmap.bg_player_contorl_full,
                pressedResId = R.mipmap.full_bottom,
                vibrator = vibrator
            )
        )
        mBinding.btnLeft.setOnTouchListener(
            PtzHelper.getPtzTouchListener(
                onControlPtz = { command, onResult ->
                    viewModel.controlPtz(
                        viewModel.getCurrentCameraip(), viewModel.getCurrentChannelId(), command, accessToken = accessToken, onResult = onResult
                    )
                }, command = "left", showToast = ::showToast,
                buttonView = mBinding.ptzControl,
                pressedResId = R.mipmap.full_left,
                normalResId = R.mipmap.bg_player_contorl_full,
                vibrator = vibrator
            )
        )
        mBinding.btnRight.setOnTouchListener(
            PtzHelper.getPtzTouchListener(
                onControlPtz = { command, onResult ->
                    viewModel.controlPtz(
                        viewModel.getCurrentCameraip(), viewModel.getCurrentChannelId(), command, accessToken = accessToken, onResult = onResult
                    )
                }, command = "right", showToast = ::showToast,
                buttonView = mBinding.ptzControl,
                normalResId = R.mipmap.bg_player_contorl_full,
                pressedResId = R.mipmap.full_right,
                vibrator = vibrator
            )
        )
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture, width: Int, height: Int
            ) {
                playStream()
            }

            override fun onSurfaceTextureSizeChanged(
                surface: SurfaceTexture, width: Int, height: Int
            ) {
            }

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releaseExoPlayer()
                vlcPlayer?.safeRelease()
                vlcPlayer = null
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
        if (textureView.isAvailable) {
            playStream()
        }

        // ====== 对讲控制器初始化 ======
        intercomController = IntercomController(
            baseUrlAndPort = ServerConfig.getYunBaseUrl(),
            activity = this,
            sn = viewModel.getCurrentCameraip(), // 使用cameraip作为sn
            chid = viewModel.getCurrentChannelId(), // 使用channelId作为chid
            pushKey = "admin123",
            onStateChanged = { isPushing ->
                isTalking = isPushing
                if (isPushing) {
                    mBinding.btnTalk.setImageResource(R.mipmap.duijiang_open)
                    showToast("对讲已开启")
                } else {
                    mBinding.btnTalk.setImageResource(R.mipmap.duijiang_close)
                    showToast("对讲已关闭")
                }
            },
            onLog = { msg -> android.util.Log.e("Intercom", msg) })

        // 初始化重试按钮点击事件
        initializeRetryButton()
    }

    /**
     * 初始化重试按钮的点击事件
     */
    private fun initializeRetryButton() {
        mBinding.btnRetry?.let { retryButton ->
            retryButton.setOnClickListener {
                Log.d("VideoFullScreen", "重试按钮被点击")
                // 隐藏重试按钮，显示loading
                retryButton.visibility = View.INVISIBLE
                val linearLayout = mBinding.loadingContainer.getChildAt(0) as? android.widget.LinearLayout
                linearLayout?.let { layout ->
                    val progressBar = layout.getChildAt(0) as? android.widget.ProgressBar
                    val loadingText = layout.getChildAt(1) as? android.widget.TextView
                    progressBar?.visibility = View.VISIBLE
                    loadingText?.visibility = View.VISIBLE
                }
                // 重新播放流
                playStream()
            }
            Log.d("VideoFullScreen", "重试按钮点击事件已设置")
        } ?: run {
            Log.w("VideoFullScreen", "重试按钮未找到")
        }
    }

    // 获取域名
    fun getDomain(): String {
        val url = viewModel.streamUrl.value ?: ""
        val pattern = """(?:https?://|rtsp://)?([^:/]+)(?::(\d+))?""".toRegex()
        val matchResult = pattern.find(url)
        return matchResult?.groupValues?.get(1) ?: ""
    }

    // 获取端口
    fun getPort(): Int {
        val url = viewModel.streamUrl.value ?: ""
        val pattern = """(?:https?://|rtsp://)?[^:/]+:(\d+)""".toRegex()
        val matchResult = pattern.find(url)
        return matchResult?.groupValues?.get(1)?.toIntOrNull() ?: -1
    }

    private fun playStream() {
        val url = viewModel.streamUrl.value ?: ""
        if (url.isEmpty()) {
            ToastUtil.showCustomToast(this, "无效的流地址")
            return
        }

        if (!textureView.isAvailable) {
            return
        }

        playbackCandidates = StreamUrlResolver.fallbackTargets(url)
        if (playbackCandidates.isEmpty()) return

        currentPlayJob?.cancel()
        showPlayLoading()
        playbackCandidateIndex = 0
        playNextCandidate()
    }

    private fun playNextCandidate() {
        if (playbackCandidateIndex >= playbackCandidates.size) {
            Log.e("VideoFullScreen", "所有播放候选均失败，共 ${playbackCandidates.size} 个")
            showPlayError()
            return
        }
        val target = playbackCandidates[playbackCandidateIndex++]
        Log.d(
            "VideoFullScreen",
            "尝试播放 ($playbackCandidateIndex/${playbackCandidates.size}) ${target.label}: ${target.url}"
        )
        if (target.useExo) {
            playWithExo(target.url, ::onCandidateFailed)
        } else {
            playWithVlc(target.url, ::onCandidateFailed)
        }
    }

    private fun onCandidateFailed() {
        if (playbackCandidateIndex < playbackCandidates.size) {
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
        mBinding.loadingContainer.visibility = View.GONE
        mBinding.btnRetry?.visibility = View.INVISIBLE
    }

    private fun releaseExoPlayer() {
        exoLivePlayer?.release()
        exoLivePlayer = null
    }

    private fun releaseVlcPlayer() {
        vlcPlayer?.let { oldPlayer ->
            try {
                oldPlayer.safeStop()
                oldPlayer.safeRelease()
            } catch (e: Exception) {
                Log.e("VideoFullScreen", "释放 VLC 失败: ${e.message}")
            }
        }
        vlcPlayer = null
    }

    private fun playWithExo(playbackUrl: String, onFail: () -> Unit) {
        releaseVlcPlayer()
        releaseExoPlayer()
        exoLivePlayer = ExoLivePlayer(this, textureView).also { exo ->
            exo.setCallback(object : ExoLivePlayer.Callback {
                override fun onPlaying() {
                    onPlaySuccess()
                }

                override fun onError(message: String?) {
                    Log.e("VideoFullScreen", "ExoPlayer 播放错误: $message")
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
                        releaseVlcPlayer()
                        val newPlayer = VLCPlayer(this@VideoFullScreenActivity)
                        newPlayer.setVideoSurface(textureView)
                        newPlayer.setVolume(0)
                        newPlayer.setCallback(object : VLCPlayer.VLCPlayerCallback {
                            override fun onError() {
                                Log.e("VideoFullScreen", "VLC 播放错误: $url")
                                releaseVlcPlayer()
                                onFail()
                            }

                            override fun playing() {
                                onPlaySuccess()
                            }

                            override fun onBuffering(bufferPercent: Float) {
                                if (bufferPercent > 95f) {
                                    onPlaySuccess()
                                }
                            }

                            override fun onEndReached() = Unit
                            override fun onTimeChanged(time: Long) = Unit
                            override fun onPositionChanged(position: Float) = Unit
                        })
                        newPlayer.setDataSource(url)
                        vlcPlayer = newPlayer
                        vlcPlayer?.let { vlcScreenRecorder.setVlcPlayer(it) }
                        vlcPlayer?.play()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    releaseVlcPlayer()
                    onFail()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        // 取消所有播放任务
        currentPlayJob?.cancel()

        // 隐藏loading对话框
        LoadingDialogUtils.hideLoading()

        releaseExoPlayer()
        vlcPlayer?.let { player ->
            // 使用安全的释放方法
            Thread {
                try {
                    player.safeStop()
                    player.safeRelease()
                } catch (e: Exception) {
                    Log.e("VLCPlayer", "子线程释放播放器异常: ${e.message}")
                }
            }.start()
        }

        // 清理录屏资源
        vlcScreenRecorder.release()
        recordingMonitor.release()
    }


    // 移除复杂的 MediaProjection 权限请求，使用 VLC 原生录制

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

    /**
     * 开始录屏（使用 VLC 原生录制）
     */
    private fun startScreenRecording() {
        vlcScreenRecorder.startRecording(channelIndex + 1, textureView)
    }

    /**
     * 停止录屏（使用 VLC 原生录制）
     */
    private fun stopScreenRecording() {
        vlcScreenRecorder.stopRecording()
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
            Log.d("RecordingProgress", progressText)
        }
    }
}
