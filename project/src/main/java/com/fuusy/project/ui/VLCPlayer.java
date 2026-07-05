package com.fuusy.project.ui;


import android.content.Context;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.Media;
import org.videolan.libvlc.MediaPlayer;
import org.videolan.libvlc.interfaces.IMedia;

import java.io.File;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * VLC播放视频工具类
 */
public class VLCPlayer implements MediaPlayer.EventListener {

    /** 所有 native stop/release 串行执行，避免并发 nativeStop 崩溃 */
    private static final ExecutorService NATIVE_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "VLC-Native");
        t.setDaemon(true);
        return t;
    });

    private static LibVLC sLibVLC;
    private MediaPlayer mediaPlayer;
    private String playUrl;
    private TextureView currentTextureView;
    private View.OnLayoutChangeListener layoutChangeListener;
    private int videoWidth = 1280;
    private int videoHeight = 720;
    private Context context; // 添加Context引用
    private Media currentMedia; // 添加当前Media对象的引用
    private boolean isMediaPrepared = false; // 添加Media是否已准备的标志
    private boolean isPaused = false; // 添加暂停状态标志
    private int playAttempt = 0; // 0=MediaCodec, 1=软解
    private volatile boolean released = false;
    private volatile boolean decodeStarted = false;
    private final Object releaseLock = new Object();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private Runnable playTimeoutRunnable;

    public VLCPlayer(Context context) {
        this.context = context; // 保存Context引用
        ArrayList<String> options = new ArrayList<>();
        options.add("--network-caching=1500");
        options.add("--live-caching=300");
        options.add("--file-caching=1000");
        // RTSP/H.264 等场景走 MediaCodec
        options.add("--codec=mediacodec_ndk,mediacodec_jni,all");

        try {
            if (sLibVLC == null) {
                sLibVLC = new LibVLC(context, options);
            }
            mediaPlayer = new MediaPlayer(sLibVLC);
            mediaPlayer.setEventListener(this);
            Log.d("VLCPlayer", "VLC播放器初始化成功");
        } catch (Exception e) {
            Log.e("VLCPlayer", "VLC播放器初始化失败: " + e.getMessage());
            sLibVLC = null;
            mediaPlayer = null;
        }
    }


    public void VLCPlayer111(Context context) {
        ArrayList<String> options = new ArrayList<>();
        options.add("--no-drop-late-frames");
        options.add("--rtsp-tcp");
        options.add("--live-caching=0");
        options.add("--network-caching=100");
        options.add("--file-caching=1000");

        try {
            sLibVLC = new LibVLC(context, options);
        } catch (Exception e) {
            Log.e("VLCPlayer", "VLC播放器初始化失败: " + e.getMessage());
        }
    }

    /**
     * 设置播放视图
     *
     * @param textureView
     */
    public void setVideoSurface(TextureView textureView) {
        if (mediaPlayer == null) {
            Log.e("looklook", "setVideoSurface - mediaPlayer 为 null，无法设置视频表面");
            return;
        }
        if (textureView == null || textureView.getSurfaceTexture() == null) {
            Log.e("VLCPlayer", "setVideoSurface: textureView is null or surface is null");
            return;
        }
        try {
            videoWidth = textureView.getWidth();
            videoHeight = textureView.getHeight();
            mediaPlayer.getVLCVout().setVideoSurface(textureView.getSurfaceTexture());
            mediaPlayer.getVLCVout().setWindowSize(videoWidth, videoHeight);
            mediaPlayer.setAspectRatio(videoWidth + ":" + videoHeight);

            textureView.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                                           int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    Log.d("VLCPlayer", "left:" + left + " top:" + top + " right:" + right + " bottom:" + bottom);
                    // 你可以在这里动态调整窗口大小
                    // int newWidth = right - left;
                    // int newHeight = bottom - top;
                    // mediaPlayer.getVLCVout().setWindowSize(newWidth, newHeight);
                }
            });
            mediaPlayer.getVLCVout().attachViews();
            Log.d("VLCPlayer", "视频表面设置成功");
        } catch (Exception e) {
            Log.e("VLCPlayer", "设置视频表面失败: " + e.getMessage());
        }
    }

    public boolean setRecord(String outputPath) {
        if (mediaPlayer != null) {
            try {
                Log.d("VLCPlayer", "开始录制: " + outputPath);

                // 确保路径是绝对路径
                if (!outputPath.startsWith("/")) {
                    outputPath = "/storage/emulated/0/" + outputPath;
                }

                // 确保目录存在
                String directory = outputPath;
                File dir = new File(directory);
                if (!dir.exists()) {
                    boolean created = dir.mkdirs();
                    Log.d("VLCPlayer", "创建目录: " + directory + ", 结果: " + created);
                }

                // 使用 VLC 原生录制方法 - 传入完整目录路径和 true 开始录制
                // record(String directory, boolean enable)
                boolean success = mediaPlayer.record(directory, true);
                if (success) {
                    Log.d("VLCPlayer", "录制开始成功，目录: " + directory);
                } else {
                    Log.e("VLCPlayer", "录制开始失败，目录: " + directory);
                }
                return success;

            } catch (Exception e) {
                Log.e("VLCPlayer", "开始录制失败: " + e.getMessage());
                return false;
            }
        } else {
            Log.e("VLCPlayer", "mediaPlayer 为 null，无法录制");
            return false;
        }
    }

    public boolean stopRecord() {
        if (mediaPlayer != null) {
            try {
                Log.d("VLCPlayer", "停止录制");
                // 传入 null 和 false 停止录制
                boolean success = mediaPlayer.record(null, false);
                if (success) {
                    Log.d("VLCPlayer", "录制停止成功");
                } else {
                    Log.e("VLCPlayer", "录制停止失败");
                }
                return success;
            } catch (Exception e) {
                Log.e("VLCPlayer", "停止录制失败: " + e.getMessage());
                return false;
            }
        } else {
            Log.e("VLCPlayer", "mediaPlayer 为 null，无法停止录制");
            return false;
        }
    }

    /**
     * 检查是否正在录制
     * 通过检查录制目录中是否有正在增长的文件来判断
     */
    public boolean isRecording() {
        if (mediaPlayer != null) {
            try {
                // 检查录制目录中是否有正在增长的文件
                String recordDirectory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES).getAbsolutePath();
                File directory = new File(recordDirectory);
                if (directory.exists() && directory.isDirectory()) {
                    File[] files = directory.listFiles((dir, name) ->
                            name.toLowerCase().endsWith(".mp4") || name.toLowerCase().endsWith(".ts"));

                    if (files != null && files.length > 0) {
                        // 检查是否有文件正在增长（录制中）
                        for (File file : files) {
                            long currentSize = file.length();
                            // 等待一秒后再次检查文件大小
                            try {
                                Thread.sleep(1000);
                                long newSize = file.length();
                                if (newSize > currentSize) {
                                    Log.d("VLCPlayer", "检测到录制中的文件: " + file.getName());
                                    return true;
                                }
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                            }
                        }
                    }
                }
                return false;
            } catch (Exception e) {
                Log.e("VLCPlayer", "检查录制状态失败: " + e.getMessage());
                return false;
            }
        }
        return false;
    }


    public void setVolume(int volume) {
        if (mediaPlayer != null) {
            try {
                // VLC 音量范围通常是 0~100
                if (volume < 0) volume = 0;
                if (volume > 100) volume = 100;

                // 设置音量
                mediaPlayer.setVolume(volume);
                Log.d("VLCPlayer", "设置音量: " + volume + ", 当前音量: " + mediaPlayer.getVolume());
            } catch (Exception e) {
                Log.e("VLCPlayer", "设置音量时发生错误: " + e.getMessage());
            }
        } else {
            Log.e("VLCPlayer", "mediaPlayer 为 null，无法设置音量");
        }
    }

    /**
     * 设置播放地址
     *
     * @param url
     */
    public void setDataSource(String url) {
        try {
            // 如果URL发生变化，需要重置Media状态
            if (!url.equals(playUrl)) {
                resetMedia();
                playAttempt = 0;
                decodeStarted = false;
            }
            playUrl = url;
            Log.d("VLC", "setDataSource: url=" + url);
        } catch (Exception e) {
            Log.d("VLC", "setDataSource error: " + e.getMessage());
        }
    }

    /**
     * 播放
     */
    public void play() {
        Log.d("VLC", "[VLCPlayer.play] called, url=" + playUrl + ", mediaPlayer=" + (mediaPlayer != null));
        if (released) {
            Log.w("VLC", "[VLCPlayer.play] player already released");
            return;
        }
        if (mediaPlayer != null && playUrl != null && !playUrl.isEmpty()) {
            try {
                cancelPlayTimeout();
                // 本地文件不需要网络
                if (!isLocalUri(playUrl) && !isNetworkAvailable()) {
                    Log.e("VLC", "[VLCPlayer.play] 网络不可用，无法播放流");
                    if (callback != null) callback.onError();
                    return;
                }

                // 如果Media已经准备好且当前是暂停状态，直接恢复播放
                if (isMediaPrepared && isPaused) {
                    Log.d("VLC", "[VLCPlayer.play] 恢复暂停的播放");
                    mediaPlayer.play();
                    isPaused = false;
                    return;
                }

                // 如果Media已经准备好且正在播放，不需要重新创建
                if (isMediaPrepared && mediaPlayer.isPlaying()) {
                    Log.d("VLC", "[VLCPlayer.play] 已经在播放中，无需重新创建Media");
                    return;
                }

                // 只有在需要时才创建新的Media对象
                if (currentMedia == null) {
                    Log.d("VLC", "[VLCPlayer.play] 创建新的Media对象, attempt=" + playAttempt);
                    currentMedia = createMedia(playUrl, playAttempt);
                    mediaPlayer.setMedia(currentMedia);
                    isMediaPrepared = true;
                }

                // 开始播放
                mediaPlayer.play();
                isPaused = false;
                Log.d("VLC", "[VLCPlayer.play] play() called");

                playTimeoutRunnable = () -> {
                    if (released || mediaPlayer == null) return;
                    try {
                        if (!mediaPlayer.isPlaying() && !isPaused) {
                            Log.w("VLC", "[VLCPlayer.play] 播放超时，通知上层切换地址");
                            notifyError();
                        }
                    } catch (Exception e) {
                        Log.w("VLC", "[VLCPlayer.play] 播放超时检查异常: " + e.getMessage());
                    }
                };
                mainHandler.postDelayed(playTimeoutRunnable, 10000);
                
            } catch (Exception e) {
                Log.e("VLCPlayer", "播放时发生错误: " + e.getMessage());
                if (callback != null) callback.onError();
            }
        } else {
            Log.e("VLC", "[VLCPlayer.play] playUrl为空或mediaPlayer为null");
            if (callback != null) callback.onError();
        }
    }

    /** 根据 URL 与尝试次数创建 Media（attempt 0=MediaCodec，1=软解） */
    private Media createMedia(String url, int attempt) {
        Media media = new Media(sLibVLC, Uri.parse(url));
        boolean useHw = attempt == 0;
        media.setHWDecoderEnabled(useHw, false);
        applyStreamMediaOptions(media, url, useHw);
        return media;
    }

    private void applyStreamMediaOptions(Media media, String url, boolean useHw) {
        media.addOption(":network-caching=1500");
        media.addOption(":live-caching=300");
        media.addOption(":no-audio");
        if (url.startsWith("http://") || url.startsWith("https://")) {
            media.addOption(":http-reconnect");
        }
        if (url.startsWith("rtsp://")) {
            media.addOption(":rtsp-tcp");
        }
        if (useHw) {
            media.addOption(":avcodec-hw=mediacodec");
        } else {
            media.addOption(":avcodec-hw=none");
        }
    }

    /** 仅在已收到画面/缓冲后，才认为是解码失败并降级软解 */
    private void retryWithSoftwareDecodeAsync() {
        if (released || playAttempt > 0 || playUrl == null || mediaPlayer == null) {
            notifyError();
            return;
        }
        playAttempt = 1;
        decodeStarted = false;
        Log.w("VLC", "[VLCPlayer] 解码失败，后台降级软解重试");
        final MediaPlayer player = mediaPlayer;
        NATIVE_EXECUTOR.execute(() -> {
            try {
                if (!released && player.isPlaying()) {
                    player.stop();
                }
            } catch (Exception ignored) {
            }
            mainHandler.post(() -> {
                if (released || mediaPlayer == null) return;
                resetMedia();
                play();
            });
        });
    }

    private void cancelPlayTimeout() {
        if (playTimeoutRunnable != null) {
            mainHandler.removeCallbacks(playTimeoutRunnable);
            playTimeoutRunnable = null;
        }
    }

    private void notifyError() {
        cancelPlayTimeout();
        if (released || callback == null) return;
        VLCPlayerCallback cb = callback;
        callback = null;
        cb.onError();
    }

    /** 判断是否为本地URI */
    private boolean isLocalUri(String url) {
        try {
            if (url == null || url.isEmpty()) return false;
            Uri u = Uri.parse(url);
            String scheme = u.getScheme();
            if (scheme == null) {
                return url.startsWith("/");
            }
            return "file".equalsIgnoreCase(scheme) || "content".equalsIgnoreCase(scheme);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 检查网络是否可用
     */
    private boolean isNetworkAvailable() {
        try {
            android.net.ConnectivityManager cm = (android.net.ConnectivityManager)
                    context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
            android.net.NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
            return activeNetwork != null && activeNetwork.isConnected();
        } catch (Exception e) {
            Log.e("VLC", "检查网络状态失败: " + e.getMessage());
            return false;
        }
    }

    /**
     * 暂停
     */
    public void pause() {
        if (mediaPlayer == null) {
            Log.e("VLCPlayer", "mediaPlayer 为 null，无法暂停");
            return;
        }
        try {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.pause();
                isPaused = true;
                Log.d("VLCPlayer", "播放已暂停");
            }
        } catch (Exception e) {
            Log.e("VLCPlayer", "暂停失败: " + e.getMessage());
        }
    }

    /**
     * 检查是否暂停
     */
    public boolean isPaused() {
        return isPaused;
    }

    /**
     * 重置Media状态（当需要切换URL时调用）
     */
    public void resetMedia() {
        if (currentMedia != null) {
            try {
                currentMedia.release();
            } catch (Exception e) {
                Log.e("VLCPlayer", "释放Media失败: " + e.getMessage());
            }
            currentMedia = null;
        }
        isMediaPrepared = false;
        isPaused = false;
        Log.d("VLCPlayer", "Media状态已重置");
    }

    /**
     * 停止播放（不释放实例时少用；退出页面请用 {@link #safeRelease()}）
     */
    public void stop() {
        Log.d("VLC", "stop() called");
        if (released || mediaPlayer == null) return;
        final MediaPlayer player = mediaPlayer;
        NATIVE_EXECUTOR.execute(() -> {
            try {
                if (player.isPlaying()) {
                    player.stop();
                }
            } catch (Exception e) {
                Log.e("VLC", "stop error: " + e.getMessage());
            }
        });
    }

    /**
     * 兼容旧调用：不再单独异步 stop，避免与 safeRelease 并发 nativeStop。
     */
    public void safeStop() {
        Log.d("VLC", "safeStop() noop, use safeRelease() for teardown");
    }

    /**
     * 安全释放资源：主线程 detachViews，native stop/release 在同一条后台队列串行执行。
     */
    public void safeRelease() {
        synchronized (releaseLock) {
            if (released) {
                return;
            }
            released = true;
        }
        Log.d("VLCPlayer", "safeRelease() called");
        cancelPlayTimeout();
        callback = null;

        final MediaPlayer playerToRelease = mediaPlayer;
        final Media mediaToRelease = currentMedia;
        mediaPlayer = null;
        currentMedia = null;
        isMediaPrepared = false;
        isPaused = false;
        decodeStarted = false;

        if (playerToRelease == null) {
            return;
        }

        mainHandler.post(() -> {
            try {
                if (playerToRelease.getVLCVout().areViewsAttached()) {
                    playerToRelease.getVLCVout().detachViews();
                    Log.d("VLCPlayer", "safeRelease() - Views detached.");
                }
            } catch (Exception e) {
                Log.e("VLCPlayer", "safeRelease() - detachViews failed: " + e.getMessage(), e);
            }
            NATIVE_EXECUTOR.execute(() -> releaseNative(playerToRelease, mediaToRelease));
        });
    }

    private static void releaseNative(MediaPlayer player, Media media) {
        try {
            player.stop();
        } catch (Exception e) {
            Log.w("VLCPlayer", "releaseNative stop failed: " + e.getMessage());
        }
        if (media != null) {
            try {
                media.release();
            } catch (Exception e) {
                Log.w("VLCPlayer", "releaseNative media failed: " + e.getMessage());
            }
        }
        try {
            player.release();
            Log.d("VLCPlayer", "releaseNative player released.");
        } catch (Exception e) {
            Log.w("VLCPlayer", "releaseNative player failed: " + e.getMessage());
        }
    }

    public boolean isPlaying() {
        if (mediaPlayer != null) {
            try {
                return mediaPlayer.isPlaying();
            } catch (Exception e) {
                Log.e("VLCPlayer", "检查播放状态失败: " + e.getMessage());
                return false;
            }
        }
        return false;
    }


    /**
     * 释放资源
     */
    public void release() {
        // 重定向到新的安全释放方法
        safeRelease();
    }

    public void setAspectRatio(String aspect) {
        if (mediaPlayer != null) {
            try {
                Log.d("VLC", "setAspectRatio: " + aspect);
                mediaPlayer.setAspectRatio(aspect);
            } catch (Exception e) {
                Log.e("VLCPlayer", "设置宽高比失败: " + e.getMessage());
            }
        } else {
            Log.e("VLCPlayer", "mediaPlayer 为 null，无法设置宽高比");
        }
    }

    @Override
    public void onEvent(MediaPlayer.Event event) {
        if (released) return;
        Log.d("VLC", "onEvent: type=" + event.type + ", event=" + event);
        switch (event.type) {
            case MediaPlayer.Event.EncounteredError:
                Log.e("VLC", "[event] 播放失败: EncounteredError, decodeStarted=" + decodeStarted);
                if (playAttempt == 0 && decodeStarted) {
                    retryWithSoftwareDecodeAsync();
                } else {
                    notifyError();
                }
                break;
            case MediaPlayer.Event.Opening:
                Log.d("VLC", "[event] 正在打开流...");
                break;
            case MediaPlayer.Event.Buffering:
                Log.d("VLC", "[event] 缓冲中... " + event.getBuffering());
                if (event.getBuffering() > 0f) {
                    decodeStarted = true;
                }
                if (callback != null) {
                    callback.onBuffering(event.getBuffering());
                }
                break;
            case MediaPlayer.Event.Playing:
                Log.d("VLC", "[event] 开始播放");
                decodeStarted = true;
                cancelPlayTimeout();
                if (callback != null) callback.playing();
                break;
            case MediaPlayer.Event.Stopped:
                Log.d("VLC", "[event] 播放停止");
                break;
            case MediaPlayer.Event.EndReached:
                Log.d("VLC", "[event] 播放结束");
                if (callback != null) callback.onEndReached();
                break;
            case MediaPlayer.Event.TimeChanged:
                if (callback != null) callback.onTimeChanged(event.getTimeChanged());
                break;
            case MediaPlayer.Event.PositionChanged:
                if (callback != null) callback.onPositionChanged(event.getPositionChanged());
                break;
            case MediaPlayer.Event.Vout:
                Log.d("VLC", "[event] 视频输出事件(Vout)");
                //在视频开始播放之前，视频的宽度和高度可能还没有被确定，因此我们需要在MediaPlayer.Event.Vout事件发生后才能获取到正确的宽度和高度
                if (mediaPlayer != null) {
                    try {
                        IMedia.VideoTrack vtrack = (IMedia.VideoTrack) mediaPlayer.getSelectedTrack(Media.Track.Type.Video);
                        if (vtrack == null) {
                            Log.d("VLC", "[event] Vout事件 - 视频轨道为空");
                            return;
                        }
                        Log.d("VLC", "[event] Vout事件 - 视频轨道信息: width=" + vtrack.width + ", height=" + vtrack.height);
                        // videoWidth = vtrack.width;
                        // videoHeight = vtrack.height;
                        
                        // 当视频轨道信息可用时，认为视频开始播放
                        if (callback != null) {
                            Log.d("VLC", "[event] Vout事件触发playing回调");
                            callback.playing();
                        }
                    } catch (Exception e) {
                        Log.e("VLCPlayer", "获取视频轨道失败: " + e.getMessage());
                    }
                }
                break;
            default:
                // 检查是否是Vout事件（type=261）
                if (event.type == 261) {
                    Log.d("VLC", "[event] 视频输出事件(type=261)");
                    //在视频开始播放之前，视频的宽度和高度可能还没有被确定，因此我们需要在MediaPlayer.Event.Vout事件发生后才能获取到正确的宽度和高度
                    if (mediaPlayer != null) {
                        try {
                            IMedia.VideoTrack vtrack = (IMedia.VideoTrack) mediaPlayer.getSelectedTrack(Media.Track.Type.Video);
                            if (vtrack == null) {
                                Log.d("VLC", "[event] 视频轨道为空");
                                return;
                            }
                            Log.d("VLC", "[event] 视频轨道信息: width=" + vtrack.width + ", height=" + vtrack.height);
                            // videoWidth = vtrack.width;
                            // videoHeight = vtrack.height;
                        } catch (Exception e) {
                            Log.e("VLCPlayer", "VLC事件 - 获取视频轨道失败: " + e.getMessage());
                        }
                    }
                } else {
                    Log.d("VLC", "[event] 其他事件 type=" + event.type);
                }
        }
    }


    private VLCPlayerCallback callback;

    public void setCallback(VLCPlayerCallback callback) {
        this.callback = callback;
    }

    public interface VLCPlayerCallback {
        void onBuffering(float bufferPercent);

        void playing();
        void onEndReached();

        void onError();

        void onTimeChanged(long currentTime);

        void onPositionChanged(float position);
    }

    public void setWindowSize(int w, int h) {
        if (mediaPlayer != null) {
            try {
                Log.d("VLC", "setWindowSize: " + w + " x " + h);
                mediaPlayer.getVLCVout().setWindowSize(w, h);
            } catch (Exception e) {
                Log.e("VLCPlayer", "设置窗口大小失败: " + e.getMessage());
            }
        } else {
            Log.e("VLCPlayer", "mediaPlayer 为 null，无法设置窗口大小");
        }
    }

}