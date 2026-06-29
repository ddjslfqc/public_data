package com.fuusy.project.ui

import android.content.Context
import android.util.Log
import android.view.TextureView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

/**
 * 使用 ExoPlayer 播放 HLS 直播。
 */
class ExoLivePlayer(
    private val context: Context,
    private val textureView: TextureView
) {
    private var player: ExoPlayer? = null
    private var callback: Callback? = null

    interface Callback {
        fun onPlaying()
        fun onError(message: String?)
    }

    fun setCallback(cb: Callback?) {
        callback = cb
    }

    fun play(url: String) {
        releaseInternal()
        val exo = ExoPlayer.Builder(context).build()
        player = exo
        exo.setVideoTextureView(textureView)
        exo.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    Log.d(TAG, "ExoPlayer 开始播放: $url")
                    callback?.onPlaying()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                val detail = formatPlaybackError(error, url)
                Log.e(TAG, detail, error)
                callback?.onError(detail)
            }
        })

        val dataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)
            .setUserAgent("Mozilla/5.0 (Linux; Android) ExoPlayer")

        if (url.contains(".m3u8", ignoreCase = true)) {
            val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
            exo.setMediaSource(mediaSource)
            Log.d(TAG, "播放 HLS: $url")
        } else {
            exo.setMediaItem(MediaItem.fromUri(url))
            Log.d(TAG, "播放 HTTP: $url")
        }
        exo.prepare()
        exo.playWhenReady = true
    }

    fun release() {
        releaseInternal()
    }

    private fun releaseInternal() {
        player?.release()
        player = null
    }

    companion object {
        private const val TAG = "ExoLivePlayer"

        fun formatPlaybackError(error: PlaybackException, url: String): String {
            val codeName = PlaybackException.getErrorCodeName(error.errorCode)
            val causes = buildString {
                var cause: Throwable? = error.cause
                while (cause != null) {
                    append("\n  cause: ")
                    append(cause.javaClass.simpleName)
                    append(": ")
                    append(cause.message)
                    cause = cause.cause
                }
            }
            return "Source error [$codeName] url=$url msg=${error.message}$causes"
        }
    }
}
