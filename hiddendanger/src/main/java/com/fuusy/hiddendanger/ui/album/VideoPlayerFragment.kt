package com.fuusy.hiddendanger.ui.album

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.graphics.SurfaceTexture
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.fuusy.hiddendanger.R
import com.fuusy.project.ui.VLCPlayer

class VideoPlayerFragment : Fragment() {

    private var videoPath: String? = null
    private var useVlc: Boolean = false

    private var textureView: TextureView? = null
    private var btnClose: ImageView? = null
    private var btnCenterPlay: ImageView? = null
    private var tvTitle: TextView? = null

    private var vlcPlayer: VLCPlayer? = null
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        videoPath = arguments?.getString(ARG_VIDEO_PATH)
        useVlc = arguments?.getBoolean(ARG_USE_VLC, false) ?: false
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_video_player, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textureView = view.findViewById(R.id.textureView)
        btnClose = view.findViewById(R.id.btnClose)
        btnCenterPlay = view.findViewById(R.id.btnCenterPlay)
        tvTitle = view.findViewById(R.id.tvTitle)

        tvTitle?.text = videoPath?.substringAfterLast('/') ?: ""

        btnClose?.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnCenterPlay?.setOnClickListener {
            if (isPlaying) pause() else play()
        }

        ensureVlc()
        val tv = textureView
        if (tv != null) {
            if (tv.isAvailable) {
                bindSurfaceAndPrepare()
                // 初始显示居中播放按钮，等待用户手动开始
                showCenterPlay(true)
            } else {
                tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                        bindSurfaceAndPrepare()
                        showCenterPlay(true)
                    }
                    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
                    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean { return true }
                    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
                }
            }
        }
    }

    private fun buildPlayableUriString(path: String): String {
        return if (path.startsWith("content://") || path.startsWith("file://")) {
            path
        } else {
            Uri.fromFile(java.io.File(path)).toString()
        }
    }

    private fun ensureVlc() {
        if (vlcPlayer == null) {
            vlcPlayer = VLCPlayer(requireContext())
        }
        val p = videoPath ?: return
        vlcPlayer?.setDataSource(buildPlayableUriString(p))
    }

    private fun bindSurfaceAndPrepare() {
        textureView?.let { vlcPlayer?.setVideoSurface(it) }
    }

    private fun showCenterPlay(show: Boolean) {
        btnCenterPlay?.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun play() {
        vlcPlayer?.play()
        isPlaying = true
        showCenterPlay(false)
    }

    private fun pause() {
        vlcPlayer?.pause()
        isPlaying = false
        showCenterPlay(true)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vlcPlayer?.safeRelease()
        textureView = null
        btnClose = null
        btnCenterPlay = null
        tvTitle = null
    }

    companion object {
        private const val ARG_VIDEO_PATH = "arg_video_path"
        private const val ARG_USE_VLC = "arg_use_vlc"

        fun newInstance(path: String, useVlc: Boolean): VideoPlayerFragment {
            val f = VideoPlayerFragment()
            f.arguments = Bundle().apply {
                putString(ARG_VIDEO_PATH, path)
                putBoolean(ARG_USE_VLC, useVlc)
            }
            return f
        }
    }
} 