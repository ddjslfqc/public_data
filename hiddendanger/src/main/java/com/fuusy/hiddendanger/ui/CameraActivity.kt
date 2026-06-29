package com.fuusy.hiddendanger.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.fuusy.common.base.BaseActivity
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.ActivityCameraBinding
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import android.os.CountDownTimer
import android.provider.MediaStore
import android.view.MotionEvent
import android.view.View
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.video.QualitySelector
import androidx.camera.video.Quality
import android.os.Handler
import android.os.Looper
import android.view.ScaleGestureDetector
import com.bumptech.glide.Glide
import android.database.Cursor
import android.net.Uri
import android.util.Log
import java.io.File
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.annotation.RequiresPermission
import androidx.camera.video.FileOutputOptions
import com.fuusy.common.utils.ToastUtil
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSource

class CameraActivity : BaseActivity<ActivityCameraBinding>() {

    private lateinit var cameraViewModel: CameraViewModel
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private val capturedImages = mutableListOf<String>()

    // 录像状态标志
    private var isRecording = false // 标识是否正在录制视频 (用于UI和onTouch判断)
    private var recordTimer: CountDownTimer? = null
    private var activeRecording: Recording? = null // CameraX的录制对象，直到Finalize才置空

    private var videoCapture: VideoCapture<Recorder>? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var isFlashOn = false
    private var lastCapturedUri: android.net.Uri? = null
    private var lastIsVideo: Boolean = false

    // 微信风格长按/短按识别相关变量
    private var isLongPressConfirmed = false // 确认是长按，用于区分短按和长按
    private var longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null

    private var currentZoomRatio = 1f
    private var maxZoomRatio = 1f
    private var minZoomRatio = 1f
    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private var exoPlayer: ExoPlayer? = null

    private var tempPhotoFile: File? = null
    private var tempVideoFile: File? = null

    private var lastSavedVideoUri: Uri? = null // 新增：缓存已保存到相册的视频uri

    override fun getLayoutId(): Int = R.layout.activity_camera

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 设置全屏模式
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )

        // 使用 post 确保在窗口完全初始化后设置 insets
        window.decorView.post {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                window.insetsController?.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
            }
        }

        cameraViewModel = ViewModelProvider(this)[CameraViewModel::class.java]
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupClickListeners()

        // Initialize ScaleGestureDetector for zoom
        scaleGestureDetector = ScaleGestureDetector(
            this,
            object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                override fun onScale(detector: ScaleGestureDetector): Boolean {
                    val scale =
                        camera?.cameraInfo?.zoomState?.value?.zoomRatio?.times(detector.scaleFactor)
                            ?: 1f
                    camera?.cameraControl?.setZoomRatio(scale.coerceIn(minZoomRatio, maxZoomRatio))
                    return true
                }
            })

        mBinding?.previewView?.setOnTouchListener { _, event ->
            if (event.pointerCount > 1) {
                scaleGestureDetector.onTouchEvent(event)
            }
            return@setOnTouchListener false
        }
    }

    override fun initData(savedInstanceState: Bundle?) {
        val videoUriToPlay = intent.getStringExtra("video_uri_to_play")
        val isVideoPreviewOnly = intent.getBooleanExtra("is_video_preview_only", false)

        if (isVideoPreviewOnly && !videoUriToPlay.isNullOrEmpty()) {
            // 如果是视频预览模式
            mBinding?.previewContainer?.visibility = View.VISIBLE
            mBinding?.previewVideo?.visibility = View.VISIBLE
            mBinding?.previewImage?.visibility = View.GONE

            mBinding?.mainCameraControls?.visibility = View.GONE // 隐藏相机控件
            mBinding?.previewView?.visibility = View.GONE // 隐藏相机预览
            mBinding?.tvCaptureHint?.visibility = View.GONE // 隐藏提示文字

            mBinding?.btnBackPreview?.visibility = View.VISIBLE // 显示返回按钮
            mBinding?.btnUse?.visibility = View.GONE // 隐藏使用按钮，因为这里只是预览

            // 初始化并播放视频
            exoPlayer?.release()
            exoPlayer = ExoPlayer.Builder(this).build()
            mBinding?.previewVideo?.player = exoPlayer
            val mediaItem = MediaItem.fromUri(videoUriToPlay)
            exoPlayer?.setMediaItem(mediaItem)
            exoPlayer?.repeatMode = ExoPlayer.REPEAT_MODE_ONE
            exoPlayer?.prepare()
            exoPlayer?.playWhenReady = true
        } else {
            // 正常相机模式的初始化
            // 显式设置初始可见性
            mBinding?.btnBackPreview?.visibility = View.GONE
            mBinding?.tvCaptureHint?.visibility = View.VISIBLE
            mBinding?.mainCameraControls?.visibility = View.VISIBLE

            if (allPermissionsGranted()) {
                startCamera()
            } else {
                requestPermissions()
            }

            mBinding?.btnUse?.setOnClickListener {
                if (lastIsVideo) {
                    // 直接返回已保存到相册的content uri
                    lastSavedVideoUri?.let { uri ->
                        val resultIntent = Intent().apply {
                            putStringArrayListExtra("captured_images", arrayListOf(uri.toString()))
                            // 添加标记，表示这是新拍摄的视频
                            putExtra("is_new_captured", true)
                            putExtra("captured_uri", uri.toString())
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    } ?: run {
                        ToastUtil.showCustomToast(this, "未找到已保存的视频")
                    }
                } else {
                    tempPhotoFile?.let { file ->
                        val uri = saveImageToGallery(file)
                        val resultIntent = Intent().apply {
                            putStringArrayListExtra("captured_images", arrayListOf(uri.toString()))
                            // 添加标记，表示这是新拍摄的照片
                            putExtra("is_new_captured", true)
                            putExtra("captured_uri", uri.toString())
                        }
                        setResult(RESULT_OK, resultIntent)
                        finish()
                    }
                }
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupClickListeners() {
        // 预览界面的返回按钮
        mBinding?.btnBackPreview?.setOnClickListener {
            hidePreview()
        }

//        // 主相机界面的返回按钮
//        mBinding?.btnBack?.setOnClickListener {
//            hidePreview()
//        }

        mBinding?.btnSwitchCamera?.setOnClickListener {
            lensFacing =
                if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
            startCamera()
        }

        mBinding?.btnFlash?.setOnClickListener {
            isFlashOn = !isFlashOn
            updateFlashState()
        }

        mBinding?.btnCapture?.setOnTouchListener { view, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // 开始长按检测
                    isLongPressConfirmed = false
                    // 创建一个局部的非空 Runnable 实例
                    val tempRunnable = Runnable {
                        isLongPressConfirmed = true
                        try {
                            startVideoRecording()
                        } catch (e: SecurityException) {
                            ToastUtil.showCustomToast(this@CameraActivity, "录音权限被拒绝，无法录制视频")
                        } catch (e: Exception) {
                            ToastUtil.showCustomToast(this@CameraActivity, "录制视频失败: ${e.message}")
                        }
                    }
                    // 将非空 Runnable 传递给 postDelayed，并明确转换为 Runnable 类型
                    longPressHandler.postDelayed(tempRunnable as Runnable, 500) // 500ms 长按阈值
                    // 然后将该实例赋值给可空的成员变量，以便后续取消
                    longPressRunnable = tempRunnable
                    true
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // 取消长按检测
                    longPressRunnable?.let { longPressHandler.removeCallbacks(it) }
                    if (isLongPressConfirmed) {
                        // 如果是长按，停止录像
                        stopVideoRecording()
                    } else {
                        // 如果是短按，拍照
                        takePhoto()
                    }
                    true
                }

                else -> false
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(mBinding?.previewView?.surfaceProvider)
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build()

                val recorder =
                    Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.FHD)).build()
                videoCapture = VideoCapture.withOutput(recorder)

                val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

                try {
                    cameraProvider?.unbindAll()
                    camera = cameraProvider?.bindToLifecycle(
                        this, cameraSelector, preview, imageCapture, videoCapture
                    )
                    updateFlashState()
                } catch (exc: Exception) {
                    ToastUtil.showCustomToast(this@CameraActivity, "相机启动失败: ${exc.message}")
                }
            } catch (exc: Exception) {
                ToastUtil.showCustomToast(this@CameraActivity, "相机初始化失败: ${exc.message}")
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        val imageCapture = imageCapture ?: return
        val name = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss-SSS", Locale.US
        ).format(System.currentTimeMillis())
        // 1. 创建临时文件
        val photoFile = File(getExternalCacheDir(), "$name.jpg")
        tempPhotoFile = photoFile
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    ToastUtil.showCustomToast(this@CameraActivity, "拍照失败: ${exception.message}")
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    // 2. 用 FileProvider 获取 Uri 进行预览
                    val uri = androidx.core.content.FileProvider.getUriForFile(
                        this@CameraActivity, "${packageName}.fileprovider", photoFile
                    )
                    showPreview(uri, false)
                }
            })
    }

    private fun updateThumbnails() {
        // This method seems unused now that we have a preview screen,
        // but keeping it in case it's used elsewhere.
        cameraViewModel.updateCapturedImages(capturedImages)
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
        )
    }

    private fun returnCapturedImages() {
        // This method should now be called only from the "使用" button on the preview screen
        val resultIntent = Intent().apply {
            putStringArrayListExtra("captured_images", ArrayList(capturedImages))
        }
        setResult(RESULT_OK, resultIntent)
        finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                ToastUtil.showCustomToast(this@CameraActivity, "需要相机和存储权限才能使用此功能")
                finish()
            }
        }
    }

    override fun onDestroy() {
        exoPlayer?.release()
        exoPlayer = null
        super.onDestroy()
        cameraExecutor.shutdown()
        longPressRunnable?.let {
            longPressHandler.removeCallbacks(it) // Clean up any pending callbacks
        }
    }

    private fun startRecordTimer() {
        // 重置进度条
        mBinding?.circleProgress?.progress = 0f
        mBinding?.circleProgress?.isFull = false
        mBinding?.circleProgress?.visibility = View.VISIBLE

        // 15秒倒计时
        recordTimer = object : CountDownTimer(15000, 50) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = (15000 - millisUntilFinished) / 15000f
                mBinding?.circleProgress?.progress = progress
                mBinding?.circleProgress?.isFull = progress >= 1f
            }

            override fun onFinish() {
                mBinding?.circleProgress?.progress = 1f
                mBinding?.circleProgress?.isFull = true
                stopVideoRecording() // 倒计时结束，自动停止录制
            }
        }.start()

        // TODO:  
        // 隐藏主拍摄区控件
//        mBinding?.mainCameraControls?.visibility = View.GONE
    }

    private fun stopVideoRecording() {
        // 停止倒计时
        recordTimer?.cancel()
        recordTimer = null

        // 重置进度条
        mBinding?.circleProgress?.visibility = View.GONE
        mBinding?.circleProgress?.progress = 0f
        mBinding?.circleProgress?.isFull = false

        // 显示主拍摄区控件
        mBinding?.mainCameraControls?.visibility = View.VISIBLE

        // 停止录制
        activeRecording?.stop()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startVideoRecording() {
        val videoCapture = videoCapture ?: return

        // 防止重复开始录制
        if (activeRecording != null) {
            ToastUtil.showCustomToast(
                this@CameraActivity,
                "视频录制正在进行中，请等待或停止当前录制。"
            )
            return
        }

        // 防止状态不同步
        if (isRecording) {
            ToastUtil.showCustomToast(this@CameraActivity, "录制状态异常，请稍后重试。")
            return
        }

        val name = SimpleDateFormat(
            "yyyy-MM-dd-HH-mm-ss-SSS", Locale.US
        ).format(System.currentTimeMillis())
        // 1. 创建临时文件
        val videoFile = File(getExternalCacheDir(), "$name.mp4")
        tempVideoFile = videoFile
        val fileOutputOptions = FileOutputOptions.Builder(videoFile).build()

        activeRecording =
            videoCapture.output.prepareRecording(this, fileOutputOptions).withAudioEnabled()
                .start(ContextCompat.getMainExecutor(this)) { event ->
                    when (event) {
                        is VideoRecordEvent.Start -> {
                            isRecording = true
                            startRecordTimer()
                        }

                        is VideoRecordEvent.Finalize -> {
                            Log.d(
                                "CameraActivity",
                                "Video finalize, file=${videoFile.absolutePath}, error=${event.error}"
                            )
                            if (event.hasError()) {
                                ToastUtil.showCustomToast(
                                    this@CameraActivity,
                                    "录像失败: ${event.error}"
                                )
                            } else {
                                Log.d(
                                    "CameraActivity",
                                    "视频文件存在: ${videoFile.exists()}, 大小: ${videoFile.length()}"
                                )
                                // 录制完成后，先保存到相册，拿到content uri，再预览
                                val contentUri = saveVideoToGallery(videoFile)
                                lastSavedVideoUri = contentUri // 缓存content uri
                                Log.d("CameraActivity", "Content uri: $contentUri")
                                // 新增：延迟500ms再预览，确保媒体库已刷新
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    Log.d("CameraActivity", "showPreview after delay, uri=$contentUri")
                                    showPreview(contentUri, true)
                                }, 500)
                            }
                            isRecording = false
                            activeRecording = null
                        }
                    }
                }
    }

    private fun showPreview(uri: Uri, isVideo: Boolean) {
        Log.d("CameraActivity", "showPreview, uri=$uri, isVideo=$isVideo")
        mBinding?.previewContainer?.visibility = View.VISIBLE
        mBinding?.mainCameraControls?.visibility = View.GONE // 隐藏主拍摄区
        mBinding?.btnBackPreview?.visibility = View.VISIBLE // 显示预览模式下的返回按钮
//        mBinding?.btnBack?.visibility = View.GONE // 隐藏主相机模式下的返回按钮
        mBinding?.tvCaptureHint?.visibility = View.GONE // 隐藏"短按拍照，长按录制"

        // 确保预览容器及其内部的按钮和文本在最顶层
        mBinding?.previewContainer?.bringToFront()
        mBinding?.btnBackPreview?.bringToFront()
        mBinding?.btnUse?.bringToFront()

        lastCapturedUri = uri
        lastIsVideo = isVideo

        if (isVideo) {
            mBinding?.previewImage?.visibility = View.GONE
            mBinding?.previewVideo?.visibility = View.VISIBLE
            mBinding?.btnUse?.text = "使用视频"

            exoPlayer?.release()
            exoPlayer = ExoPlayer.Builder(this).build()
            mBinding?.previewVideo?.player = exoPlayer

            try {
                val mediaItem = MediaItem.fromUri(uri)
                val dataSourceFactory = DefaultDataSource.Factory(this)
                val mediaSource =
                    ProgressiveMediaSource.Factory(dataSourceFactory).createMediaSource(mediaItem)
                exoPlayer?.setMediaSource(mediaSource)
                exoPlayer?.prepare()
                exoPlayer?.playWhenReady = true
                Log.d("CameraActivity", "ExoPlayer setMediaSource success, uri=$uri")
            } catch (e: Exception) {
                Log.e("CameraActivity", "ExoPlayer setMediaSource error: ${e.message}", e)
            }
        } else {
            mBinding?.previewVideo?.visibility = View.GONE
            mBinding?.previewImage?.visibility = View.VISIBLE
            mBinding?.btnUse?.text = "使用照片"

            Glide.with(this).load(uri).fitCenter().into(mBinding?.previewImage!!)
        }
    }

    // 获取真实文件路径
    private fun getRealPathFromUri(context: Context, uri: Uri): String? {
        var realPath: String? = null
        val proj = arrayOf(MediaStore.Video.Media.DATA)
        val cursor: Cursor? = context.contentResolver.query(uri, proj, null, null, null)
        if (cursor != null && cursor.moveToFirst()) {
            val columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA)
            realPath = cursor.getString(columnIndex)
            cursor.close()
        }
        return realPath
    }

    private fun updateFlashState() {
        // 只支持后置摄像头
        if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            camera?.cameraControl?.enableTorch(isFlashOn)
            mBinding?.btnFlash?.setImageResource(if (isFlashOn) R.drawable.ic_flash_on else R.drawable.ic_flash_off)
        } else {
            // 前置摄像头不支持闪光灯
            camera?.cameraControl?.enableTorch(false)
            mBinding?.btnFlash?.setImageResource(R.drawable.ic_flash_off)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (mBinding?.previewContainer?.visibility == View.VISIBLE) {
            // If preview is active, don't pass touch events to the camera for zooming
            return super.onTouchEvent(event)
        }
        scaleGestureDetector.onTouchEvent(event)
        return true
    }

    private fun hidePreview() {
        mBinding?.previewContainer?.visibility = View.GONE
        mBinding?.previewVideo?.visibility = View.GONE
        mBinding?.previewImage?.visibility = View.GONE
        mBinding?.btnBackPreview?.visibility = View.GONE // 隐藏预览模式下的返回按钮
        mBinding?.mainCameraControls?.visibility = View.VISIBLE // 显示主拍摄区
//        mBinding?.btnBack?.visibility = View.VISIBLE // 显示主相机模式下的返回按钮
        mBinding?.tvCaptureHint?.visibility = View.VISIBLE // 恢复"短按拍照，长按录制"
        lastCapturedUri = null
        lastIsVideo = false

        // 停止并释放 ExoPlayer
        if (exoPlayer != null) {
            exoPlayer?.stop()
            exoPlayer?.release()
            exoPlayer = null
        }
    }

    // 新增保存图片到相册的方法
    private fun saveImageToGallery(file: File): Uri {
        val name = file.name
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Camera")
            }
        }
        val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)!!
        contentResolver.openOutputStream(uri).use { output ->
            file.inputStream().use { input ->
                input.copyTo(output!!)
            }
        }
        // 保存到相册后，删除临时文件
        file.delete()
        
        // 强制刷新媒体库
        val realPath = getRealPathFromUri(this, uri)
        if (realPath != null) {
            // 使用 MediaScannerConnection 刷新
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(realPath),
                arrayOf("image/jpeg"),
                object : android.media.MediaScannerConnection.OnScanCompletedListener {
                    override fun onScanCompleted(path: String?, uri: Uri?) {
                        Log.d("CameraActivity", "MediaScanner scan completed: path=$path, uri=$uri")
                    }
                }
            )
            
            // 额外发送广播通知系统有新文件
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(File(realPath))
            sendBroadcast(intent)
            
            Log.d("CameraActivity", "MediaScannerConnection.scanFile and broadcast for uri=$uri, path=$realPath")
        }
        
        return uri
    }

    // 新增保存视频到相册的方法
    private fun saveVideoToGallery(file: File): Uri {
        val name = file.name
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Camera")
            }
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)!!
        contentResolver.openOutputStream(uri).use { output ->
            file.inputStream().use { input ->
                input.copyTo(output!!)
            }
        }
        // 保存到相册后，删除临时文件
        file.delete()
        
        // 强制刷新媒体库
        val realPath = getRealPathFromUri(this, uri)
        if (realPath != null) {
            // 使用 MediaScannerConnection 刷新
            android.media.MediaScannerConnection.scanFile(
                this,
                arrayOf(realPath),
                arrayOf("video/mp4"),
                object : android.media.MediaScannerConnection.OnScanCompletedListener {
                    override fun onScanCompleted(path: String?, uri: Uri?) {
                        Log.d("CameraActivity", "MediaScanner scan completed: path=$path, uri=$uri")
                    }
                }
            )
            
            // 额外发送广播通知系统有新文件
            val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
            intent.data = Uri.fromFile(File(realPath))
            sendBroadcast(intent)
            
            Log.d("CameraActivity", "MediaScannerConnection.scanFile and broadcast for uri=$uri, path=$realPath")
        }
        
        return uri
    }

    companion object {
        private const val TAG = "CameraActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO
        )
    }
}