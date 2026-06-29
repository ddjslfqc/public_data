package com.fuusy.hiddendanger.util

import android.content.Context
import android.widget.ImageView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.luck.picture.lib.engine.ImageEngine
import android.util.Log

class GlideEngine : ImageEngine {
    override fun loadImage(context: Context, url: String, imageView: ImageView) {
        Log.d("GlideEngine", "loadImage: $url")
        Glide.with(context)
            .load(url)
            .apply(RequestOptions()
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(com.fuusy.common.R.mipmap.img_placeholder))
            .into(imageView)
    }

    override fun loadImage(
        context: Context,
        imageView: ImageView,
        url: String,
        width: Int,
        height: Int
    ) {
        Log.d("GlideEngine", "loadImage with size: $url, ${width}x${height}")
        Glide.with(context)
            .load(url)
            .apply(RequestOptions()
                .override(width, height)
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(com.fuusy.common.R.mipmap.img_placeholder))
            .into(imageView)
    }

    override fun loadAlbumCover(context: Context, url: String, imageView: ImageView) {
        Log.d("GlideEngine", "loadAlbumCover: $url")
        Glide.with(context)
            .load(url)
            .apply(RequestOptions()
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(com.fuusy.common.R.mipmap.img_placeholder))
            .into(imageView)
    }

    override fun loadGridImage(context: Context, url: String, imageView: ImageView) {
        Log.d("GlideEngine", "loadGridImage: $url")
        Glide.with(context)
            .load(url)
            .apply(RequestOptions()
                .centerCrop()
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .error(com.fuusy.common.R.mipmap.img_placeholder))
            .into(imageView)
    }

    override fun pauseRequests(context: Context?) {
        if (context != null) {
            Glide.with(context).pauseRequests()
        }
    }

    override fun resumeRequests(context: Context?) {
        if (context != null) {
            Glide.with(context).resumeRequests()
        }
    }

    companion object {
        fun createGlideEngine(): GlideEngine = GlideEngine()
    }
} 