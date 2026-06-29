package com.yourpackage.vlc

import android.content.Context
import android.net.Uri
import android.util.Log
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

class VlcPlayerHelper(
    private val context: Context,
    private val videoLayout: VLCVideoLayout
) {
    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var onPlayingListener: (() -> Unit)? = null

    fun play(url: String) {
        Log.d("VLC", "[play] called with url: $url")
        release()
        val options = arrayListOf(
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--rtsp-tcp",
            "--network-caching=1000"
        )
        libVLC = LibVLC(context, options)
        Log.d("VLC", "[play] LibVLC created: $libVLC")
        mediaPlayer = MediaPlayer(libVLC).apply {
            Log.d("VLC", "[play] MediaPlayer created: $this")
            attachViews(videoLayout, null, false, false)
            Log.d("VLC", "[play] attachViews called with videoLayout: $videoLayout")
            val media = Media(libVLC, Uri.parse(url))
            media.setHWDecoderEnabled(true, false)
            media.addOption(":network-caching=1000")
            this.media = media
            Log.d("VLC", "[play] setMedia called with url: $url")
            media.release()
            setEventListener { event ->
                Log.d("VLC", "[event] type=${event.type}, event=$event")
                when (event.type) {
                    MediaPlayer.Event.EncounteredError -> Log.e(
                        "VLC",
                        "[event] 播放失败: EncounteredError"
                    )

                    MediaPlayer.Event.Opening -> Log.d("VLC", "[event] 正在打开流...")
                    MediaPlayer.Event.Buffering -> Log.d(
                        "VLC",
                        "[event] 缓冲中... ${event.buffering}"
                    )

                    MediaPlayer.Event.Playing -> {
                        Log.d("VLC", "[event] 开始播放")
                        onPlayingListener?.invoke()
                    }

                    MediaPlayer.Event.Stopped -> Log.d("VLC", "[event] 播放停止")
                    MediaPlayer.Event.EndReached -> Log.d("VLC", "[event] 播放结束")
                    else -> Log.d("VLC", "[event] 其他事件 type=${event.type}")
                }
            }
            try {
                play()
                Log.d("VLC", "[play] play() called")
            } catch (e: Exception) {
                Log.e("VLC", "[play] play failed: ${e.message}")
            }
        }
    }

    fun stop() {
        Log.d("VLC", "[stop] called")
        mediaPlayer?.stop()
        // 将detachViews放到子线程，避免主线程阻塞
        mediaPlayer?.let { player ->
            Thread {
                try {
                    player.detachViews()
                } catch (e: Exception) {
                    Log.e("VLC", "[stop] detachViews error: ${e.message}")
                }
            }.start()
        }
    }

    fun release() {
        Log.d("VLC", "[release] called")
        mediaPlayer?.stop()
        
        // 将资源释放放到子线程，避免主线程阻塞
        val player = mediaPlayer
        val lib = libVLC
        
        mediaPlayer = null
        libVLC = null
        
        Thread {
            try {
                player?.detachViews()
                player?.release()
                lib?.release()
            } catch (e: Exception) {
                Log.e("VLC", "[release] 子线程释放资源失败: ${e.message}")
            }
        }.start()
    }

    fun setOnPlayingListener(listener: () -> Unit) {
        onPlayingListener = listener
    }

    fun mute() {
        mediaPlayer?.volume = 0
    }

    fun unmute() {
        mediaPlayer?.volume = 100
    }

    fun setAspectRatio(aspect: String) {
        Log.d("VLC", "setAspectRatio: $aspect")
        mediaPlayer?.setAspectRatio(aspect)
    }
} 