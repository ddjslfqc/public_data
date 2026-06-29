package com.fuusy.project.ui.activity

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Environment
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fuusy.project.R
import android.widget.Toast
import android.content.ContentValues
import android.provider.MediaStore
import com.fuusy.common.utils.ToastUtil
import java.io.FileInputStream

class ScreenRecordService : Service() {
    companion object {
        const val CHANNEL_ID = "screen_record_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "ACTION_STOP_SCREEN_RECORD"
    }

    private var mediaProjection: MediaProjection? = null
    private var mediaRecorder: MediaRecorder? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var outputPath: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 1. 立即进入前台，避免超时崩溃
        startForegroundServiceWithNotification()

        // 2. 再处理业务逻辑
        if (intent?.action == ACTION_STOP) {
            stopScreenRecording()
            stopSelf()
            return START_NOT_STICKY
        }
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")
        if (data != null) {
            startScreenRecording(resultCode, data)
        }
        // 不要提前 stopSelf()，即使参数不对也让服务活着
        return START_NOT_STICKY
    }

    private fun startForegroundServiceWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "屏幕录制", NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("正在录制屏幕")
            .setContentText("屏幕录制进行中...")
            .setSmallIcon(R.drawable.ic_record)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun startScreenRecording(resultCode: Int, data: Intent) {
        val mediaProjectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val dpi = metrics.densityDpi
        outputPath =
            getExternalFilesDir(Environment.DIRECTORY_MOVIES)?.absolutePath + "/screen_${System.currentTimeMillis()}.mp4"
        mediaRecorder = MediaRecorder().apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoSize(width, height)
            setVideoFrameRate(30)
            setVideoEncodingBitRate(5 * 1024 * 1024)
            setOutputFile(outputPath)
            prepare()
        }
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ScreenRecorder",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mediaRecorder?.surface, null, null
        )
        mediaRecorder?.start()
    }

    private fun stopScreenRecording() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            virtualDisplay?.release()
            mediaProjection?.stop()
            android.util.Log.i("ScreenRecordService", "stopScreenRecording outputPath=$outputPath")
            ToastUtil.showCustomToast(this, "录屏已保存")
            outputPath?.let { path ->
                saveVideoToCustomAlbum(this, path)
            }
        } catch (_: Exception) {
        }
    }

    private fun saveVideoToCustomAlbum(
        context: Context,
        videoFilePath: String,
        fileName: String = "video_${System.currentTimeMillis()}.mp4"
    ) {
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/MyAPP")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, contentValues)
        if (uri != null) {
            resolver.openOutputStream(uri).use { out ->
                FileInputStream(videoFilePath).use { input ->
                    input.copyTo(out!!)
                }
            }
            contentValues.clear()
            contentValues.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
    }

    override fun onDestroy() {
        stopScreenRecording()
        super.onDestroy()
    }
}