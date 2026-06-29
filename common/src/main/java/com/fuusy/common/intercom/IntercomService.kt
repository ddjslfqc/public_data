package com.fuusy.common.intercom

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.fuusy.common.R
import com.fuusy.common.utils.WebRTCPusher

class IntercomService : Service() {
	companion object {
		private const val CHANNEL_ID = "intercom_channel"
		private const val NOTI_ID = 10086
		const val ACTION_START = "com.fuusy.common.intercom.action.START"
		const val ACTION_STOP = "com.fuusy.common.intercom.action.STOP"
		const val EXTRA_SIGNALING_URL = "extra_signaling_url"
	}

	@Volatile private var pusher: WebRTCPusher? = null

	override fun onBind(intent: Intent?): IBinder? = null

	override fun onCreate() {
		super.onCreate()
		createChannel()
		startForeground(NOTI_ID, buildNotification("对讲准备中…"))
	}

	override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
		when (intent?.action) {
			ACTION_START -> {
				val url = intent.getStringExtra(EXTRA_SIGNALING_URL) ?: return START_STICKY
				startIntercom(url)
			}
			ACTION_STOP -> stopIntercom()
		}
		return START_STICKY
	}

	private fun startIntercom(signalingUrl: String) {
		stopIntercom()
		pusher = WebRTCPusher(
			context = this,
			signalingUrl = signalingUrl,
			onLog = { /* could post to notification if needed */ }
		)
		pusher?.init()
		pusher?.connectAndPush()
		updateNotification("对讲中…")
	}

	private fun stopIntercom() {
		try { pusher?.close() } catch (_: Exception) {}
		pusher = null
		updateNotification("对讲已停止")
	}

	override fun onDestroy() {
		stopIntercom()
		super.onDestroy()
	}

	private fun createChannel() {
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
			val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
			if (nm.getNotificationChannel(CHANNEL_ID) == null) {
				val ch = NotificationChannel(CHANNEL_ID, "对讲", NotificationManager.IMPORTANCE_LOW)
				nm.createNotificationChannel(ch)
			}
		}
	}

	private fun buildNotification(content: String): Notification {
		val pi = PendingIntent.getActivity(
			this, 0, packageManager.getLaunchIntentForPackage(packageName),
			PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
		)
		return NotificationCompat.Builder(this, CHANNEL_ID)
			.setContentTitle("语音对讲")
			.setContentText(content)
			.setSmallIcon(R.mipmap.logo_new)
			.setContentIntent(pi)
			.setOngoing(true)
			.build()
	}

	private fun updateNotification(content: String) {
		val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
		nm.notify(NOTI_ID, buildNotification(content))
	}
} 