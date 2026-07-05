package com.fuusy.project.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.media.Image
import android.media.ImageReader
import android.net.Uri
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.LruCache
import android.widget.ImageView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume

/**
 * HTTP-FLV / RTSP 列表封面：VLC 抓取首帧 + 内存/磁盘二级缓存。
 * LibVLC 全局单例，避免每个缩略图重复初始化导致内存暴涨与 ANR。
 */
object FlvThumbnailLoader {

    private const val TAG = "FlvThumbnail"
    private const val THUMB_WIDTH = 480
    private const val THUMB_HEIGHT = 360
    private const val CAPTURE_TIMEOUT_MS = 4_000L
    private const val MAX_CONCURRENT = 1
    private const val JPEG_QUALITY = 82

    private val memoryCache = object : LruCache<String, Bitmap>(8) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val vlcThread = HandlerThread("FlvThumbnailVlc").apply { start() }
    private val vlcHandler = Handler(vlcThread.looper)
    private val semaphore = Semaphore(MAX_CONCURRENT)
    private val paused = AtomicBoolean(true)
    private val viewJobs = ConcurrentHashMap<Int, Job>()
    private val pendingLoads = ConcurrentHashMap.newKeySet<PendingLoad>()

    @Volatile
    private var applicationContext: Context? = null

    @Volatile
    private var libVLCInstance: LibVLC? = null

    private data class PendingLoad(
        val cacheKey: String,
        val streamUrl: String,
        val imageRef: WeakReference<ImageView>
    )

    fun init(context: Context) {
        val appContext = context.applicationContext
        if (applicationContext != null) return
        applicationContext = appContext
        vlcHandler.post {
            if (libVLCInstance != null) return@post
            try {
                val options = arrayListOf(
                    "--network-caching=300",
                    "--live-caching=150",
                    "--no-audio",
                    "--http-reconnect"
                )
                libVLCInstance = LibVLC(appContext, options)
                Log.d(TAG, "LibVLC initialized")
            } catch (e: Exception) {
                Log.e(TAG, "LibVLC init failed", e)
            }
        }
    }

    fun release() {
        cancelAll()
        vlcHandler.post {
            try {
                libVLCInstance?.release()
            } catch (_: Exception) {
            }
            libVLCInstance = null
            Log.d(TAG, "LibVLC released")
        }
        applicationContext = null
    }

    fun setPaused(value: Boolean) {
        val wasPaused = paused.getAndSet(value)
        if (value) {
            cancelAll()
        } else if (wasPaused) {
            drainPending()
        }
    }

    fun bindCover(
        context: Context,
        imageView: ImageView,
        cacheKey: String,
        streamUrl: String?,
        scope: CoroutineScope
    ) {
        val appContext = context.applicationContext
        if (applicationContext == null) {
            init(appContext)
        }
        val requestId = System.nanoTime().toInt()
        imageView.setTag(com.fuusy.project.R.id.tag_thumbnail_request, requestId)
        imageView.setImageDrawable(null)

        if (streamUrl.isNullOrBlank()) {
            cancel(imageView)
            return
        }

        memoryCache.get(cacheKey)?.let { cached ->
            if (isRequestActive(imageView, requestId)) {
                imageView.setImageBitmap(cached)
            }
            return
        }

        cancelJobForView(imageView)
        val viewId = System.identityHashCode(imageView)
        val job = scope.launch(Dispatchers.IO) {
            try {
                val diskBitmap = loadDisk(appContext, cacheKey)
                if (diskBitmap != null) {
                    memoryCache.put(cacheKey, diskBitmap)
                    withContext(Dispatchers.Main) {
                        if (isRequestActive(imageView, requestId)) {
                            imageView.setImageBitmap(diskBitmap)
                        }
                    }
                    return@launch
                }
                if (paused.get()) {
                    pendingLoads.add(PendingLoad(cacheKey, streamUrl, WeakReference(imageView)))
                    return@launch
                }
                loadFromStream(appContext, imageView, requestId, cacheKey, streamUrl)
            } finally {
                viewJobs.remove(viewId)
            }
        }
        viewJobs[viewId] = job
    }

    fun cancel(imageView: ImageView) {
        cancelJobForView(imageView)
        pendingLoads.removeIf { it.imageRef.get() == imageView }
        imageView.setTag(com.fuusy.project.R.id.tag_thumbnail_request, null)
    }

    fun cancelAll() {
        viewJobs.values.forEach { it.cancel() }
        viewJobs.clear()
        pendingLoads.clear()
        vlcHandler.removeCallbacksAndMessages(null)
    }

    private fun drainPending() {
        if (paused.get()) return
        val copies = pendingLoads.toList()
        pendingLoads.clear()
        copies.forEach { pending ->
            val imageView = pending.imageRef.get() ?: return@forEach
            val requestId = imageView.getTag(com.fuusy.project.R.id.tag_thumbnail_request) as? Int
                ?: System.nanoTime().toInt()
            val viewId = System.identityHashCode(imageView)
            cancelJobForView(imageView)
            val job = scope.launch(Dispatchers.IO) {
                try {
                    loadDisk(imageView.context.applicationContext, pending.cacheKey)?.let { diskBitmap ->
                        memoryCache.put(pending.cacheKey, diskBitmap)
                        withContext(Dispatchers.Main) {
                            if (isRequestActive(imageView, requestId)) {
                                imageView.setImageBitmap(diskBitmap)
                            }
                        }
                        return@launch
                    }
                    if (paused.get()) {
                        pendingLoads.add(pending)
                        return@launch
                    }
                    loadFromStream(
                        imageView.context.applicationContext,
                        imageView,
                        requestId,
                        pending.cacheKey,
                        pending.streamUrl
                    )
                } finally {
                    viewJobs.remove(viewId)
                }
            }
            viewJobs[viewId] = job
        }
    }

    private suspend fun loadFromStream(
        appContext: Context,
        imageView: ImageView,
        requestId: Int,
        cacheKey: String,
        streamUrl: String
    ) {
        if (paused.get()) {
            pendingLoads.add(PendingLoad(cacheKey, streamUrl, WeakReference(imageView)))
            return
        }
        semaphore.acquire()
        try {
            if (paused.get()) {
                pendingLoads.add(PendingLoad(cacheKey, streamUrl, WeakReference(imageView)))
                return
            }
            val bitmap = captureFirstFrame(appContext, streamUrl)
            if (bitmap != null) {
                memoryCache.put(cacheKey, bitmap)
                saveDisk(appContext, cacheKey, bitmap)
            }
            withContext(Dispatchers.Main) {
                if (bitmap != null && isRequestActive(imageView, requestId)) {
                    imageView.setImageBitmap(bitmap)
                }
            }
        } finally {
            semaphore.release()
        }
    }

    private fun cancelJobForView(imageView: ImageView) {
        viewJobs.remove(System.identityHashCode(imageView))?.cancel()
    }

    private fun isRequestActive(imageView: ImageView, requestId: Int): Boolean {
        return imageView.getTag(com.fuusy.project.R.id.tag_thumbnail_request) == requestId
    }

    private fun diskFile(context: Context, cacheKey: String): File {
        val dir = File(context.cacheDir, "video_thumbs")
        if (!dir.exists()) dir.mkdirs()
        val safe = cacheKey.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dir, "$safe.jpg")
    }

    private fun loadDisk(context: Context, cacheKey: String): Bitmap? {
        val file = diskFile(context, cacheKey)
        if (!file.exists() || file.length() == 0L) return null
        return try {
            val opts = android.graphics.BitmapFactory.Options().apply {
                inSampleSize = 2
            }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, opts)
        } catch (e: Exception) {
            Log.w(TAG, "读取磁盘封面失败: ${file.name}", e)
            null
        }
    }

    private fun saveDisk(context: Context, cacheKey: String, bitmap: Bitmap) {
        val file = diskFile(context, cacheKey)
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        } catch (e: Exception) {
            Log.w(TAG, "写入磁盘封面失败: ${file.name}", e)
        }
    }

    private suspend fun captureFirstFrame(context: Context, url: String): Bitmap? {
        return suspendCancellableCoroutine { cont ->
            var player: MediaPlayer? = null
            var imageReader: ImageReader? = null
            var timeoutRunnable: Runnable? = null
            var finished = false

            fun finish(bitmap: Bitmap?) {
                if (finished) return
                finished = true
                timeoutRunnable?.let { vlcHandler.removeCallbacks(it) }
                try {
                    player?.stop()
                    player?.vlcVout?.detachViews()
                } catch (_: Exception) {
                }
                try {
                    player?.release()
                } catch (_: Exception) {
                }
                try {
                    imageReader?.close()
                } catch (_: Exception) {
                }
                if (cont.isActive) cont.resume(bitmap)
            }

            cont.invokeOnCancellation {
                vlcHandler.post { finish(null) }
            }

            vlcHandler.post {
                timeoutRunnable = Runnable { finish(null) }
                vlcHandler.postDelayed(timeoutRunnable!!, CAPTURE_TIMEOUT_MS)
                try {
                    val currentLibVLC = libVLCInstance
                    if (currentLibVLC == null) {
                        Log.w(TAG, "LibVLC not ready, skip capture: $url")
                        finish(null)
                        return@post
                    }
                    player = MediaPlayer(currentLibVLC)
                    imageReader = ImageReader.newInstance(
                        THUMB_WIDTH,
                        THUMB_HEIGHT,
                        PixelFormat.RGBA_8888,
                        2
                    )
                    val surface = imageReader!!.surface
                    player!!.vlcVout.setVideoSurface(surface, null)
                    player!!.vlcVout.setWindowSize(THUMB_WIDTH, THUMB_HEIGHT)
                    player!!.vlcVout.attachViews()

                    imageReader!!.setOnImageAvailableListener({ reader ->
                        if (finished) return@setOnImageAvailableListener
                        val image = reader.acquireLatestImage() ?: return@setOnImageAvailableListener
                        try {
                            val bitmap = imageToBitmap(image)
                            if (bitmap != null) {
                                Log.d(TAG, "抓帧成功: $url")
                                finish(bitmap)
                            }
                        } finally {
                            image.close()
                        }
                    }, vlcHandler)

                    player!!.setEventListener { event ->
                        when (event.type) {
                            MediaPlayer.Event.EncounteredError -> {
                                Log.w(TAG, "抓帧失败(VLC错误): $url")
                                finish(null)
                            }
                        }
                    }

                    val media = Media(currentLibVLC, Uri.parse(url))
                    media.setHWDecoderEnabled(false, false)
                    media.addOption(":network-caching=300")
                    player!!.media = media
                    media.release()
                    player!!.play()
                    Log.d(TAG, "开始抓帧: $url")
                } catch (e: Exception) {
                    Log.e(TAG, "抓帧异常: $url", e)
                    finish(null)
                }
            }
        }
    }

    private fun imageToBitmap(image: Image): Bitmap? {
        val plane = image.planes.firstOrNull() ?: return null
        val buffer = plane.buffer
        buffer.rewind()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val width = image.width
        val height = image.height
        val paddedWidth = width + (rowStride - pixelStride * width) / pixelStride
        val bitmap = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
        bitmap.copyPixelsFromBuffer(buffer)
        return if (paddedWidth == width) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, width, height).also { bitmap.recycle() }
        }
    }
}
