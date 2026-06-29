package com.fuusy.project;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.TextureView;
import com.fuusy.common.utils.FileUtils;
import com.fuusy.project.ui.VLCPlayer;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import android.os.Environment;

/**
 * VLC 原生录屏工具类
 * 参考 CSDN 文章：https://blog.csdn.net/u010735007/article/details/108517985/
 * 使用 VLC 原生录制功能，无需 MediaProjection 权限
 */
public class VLCScreenRecorder {
    
    private Context context;
    private VLCPlayer vlcPlayer;
    private boolean isRecording = false;
    private String currentRecordPath = null;
    private Handler handler;
    private int recordingTime = 0;
    private Runnable timerRunnable;
    
    // 回调接口
    public interface VLCRecorderCallback {
        void onRecordingStarted();
        void onRecordingStopped(String videoPath, boolean success);
        void onTimeUpdate(String timeString);
        void onError(String error);
    }
    
    private VLCRecorderCallback callback;
    
    public VLCScreenRecorder(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
    }
    
    public void setVlcPlayer(VLCPlayer vlcPlayer) {
        this.vlcPlayer = vlcPlayer;
    }
    
    public void setCallback(VLCRecorderCallback callback) {
        this.callback = callback;
    }
    
    /**
     * 开始录制
     * @param channelNum 通道号
     * @param textureView TextureView（保持接口一致，实际不使用）
     */
    public void startRecording(int channelNum, TextureView textureView) {
        if (isRecording) {
            if (callback != null) callback.onError("录屏正在进行中");
            return;
        }
        
        if (vlcPlayer == null) {
            if (callback != null) callback.onError("VLC 播放器未设置");
            return;
        }
        
        try {
            // 1. 录制到 Movies 目录
            String recordDirectory = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
            
            Log.d("VLCRecorder", "开始录制，目录: " + recordDirectory);
            
            // 2. 开始 VLC 录制（传入目录路径）
            boolean success = vlcPlayer.setRecord(recordDirectory);
            
            if (success) {
                // 3. 更新状态
                isRecording = true;
                currentRecordPath = recordDirectory;
                
                // 4. 开始计时
                startTimer();
                
                // 5. 回调
                if (callback != null) callback.onRecordingStarted();
                
                Log.d("VLCRecorder", "VLC 录制开始成功");
            } else {
                Log.e("VLCRecorder", "VLC 录制开始失败");
                if (callback != null) callback.onError("VLC 录制开始失败");
            }
            
        } catch (Exception e) {
            Log.e("VLCRecorder", "开始录制失败: " + e.getMessage());
            if (callback != null) callback.onError("开始录制失败: " + e.getMessage());
        }
    }
    
    /**
     * 停止录制
     */
    public void stopRecording() {
        if (!isRecording) {
            if (callback != null) callback.onError("录屏未在进行中");
            return;
        }
        
        try {
            Log.d("VLCRecorder", "停止录制");
            
            // 1. 停止 VLC 录制 - 放到子线程避免主线程阻塞
            if (vlcPlayer != null) {
                Thread stopThread = new Thread(() -> {
                    try {
                        boolean success = vlcPlayer.stopRecord();
                        if (success) {
                            Log.d("VLCRecorder", "VLC 录制停止成功");
                            // 在主线程处理后续逻辑
                            new Handler(Looper.getMainLooper()).post(() -> {
                                // 2. 停止计时
                                stopTimer();
                                
                                // 3. 延迟检查文件（VLC可能需要时间生成文件）
                                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                    checkRecordFile();
                                    
                                    // 4. 重置状态
                                    isRecording = false;
                                    currentRecordPath = null;
                                }, 1000); // 延迟1秒检查
                            });
                        } else {
                            Log.e("VLCRecorder", "VLC 录制停止失败");
                            new Handler(Looper.getMainLooper()).post(() -> {
                                if (callback != null) callback.onError("VLC 录制停止失败");
                            });
                        }
                    } catch (Exception e) {
                        Log.e("VLCRecorder", "子线程停止录制失败: " + e.getMessage());
                        new Handler(Looper.getMainLooper()).post(() -> {
                            if (callback != null) callback.onError("停止录制失败: " + e.getMessage());
                        });
                    }
                });
                stopThread.start();
            } else {
                Log.e("VLCRecorder", "vlcPlayer 为 null，无法停止录制");
                if (callback != null) callback.onError("播放器未初始化，无法停止录制");
            }
            
        } catch (Exception e) {
            Log.e("VLCRecorder", "停止录制失败: " + e.getMessage());
            if (callback != null) callback.onError("停止录制失败: " + e.getMessage());
            isRecording = false;
        }
    }
    
    /**
     * 检查录制文件
     */
    private void checkRecordFile() {
        // 直接查找 Movies 目录
        String moviesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES).getAbsolutePath();
        Log.d("VLCRecorder", "检查录制文件，目录: " + moviesDir);
        File recordDirectory = new File(moviesDir);
        if (recordDirectory.exists() && recordDirectory.isDirectory()) {
            File[] files = recordDirectory.listFiles((dir, name) -> {
                String lowerName = name.toLowerCase();
                return lowerName.endsWith(".mp4") || lowerName.endsWith(".ts") || 
                       lowerName.endsWith(".avi") || lowerName.endsWith(".mkv") ||
                       lowerName.endsWith(".mov") || lowerName.endsWith(".wmv");
            });
            Log.d("VLCRecorder", "找到 " + (files != null ? files.length : 0) + " 个录制文件");
            if (files != null && files.length > 0) {
                File latestFile = files[0];
                for (File file : files) {
                    Log.d("VLCRecorder", "录制文件: " + file.getName() + ", 大小: " + file.length() + " bytes, 修改时间: " + file.lastModified());
                    if (file.lastModified() > latestFile.lastModified()) {
                        latestFile = file;
                    }
                }
                if (latestFile.exists() && latestFile.length() > 0) {
                    Log.d("VLCRecorder", "录制文件生成成功: " + latestFile.getAbsolutePath() + ", 大小: " + latestFile.length() + " 字节");
                    if (callback != null) callback.onRecordingStopped(latestFile.getAbsolutePath(), true);
                } else {
                    Log.e("VLCRecorder", "录制文件大小为0: " + latestFile.getAbsolutePath());
                    if (callback != null) callback.onRecordingStopped(null, false);
                }
            } else {
                Log.e("VLCRecorder", "未找到录制文件，目录内容:");
                File[] allFiles = recordDirectory.listFiles();
                if (allFiles != null) {
                    for (File file : allFiles) {
                        Log.e("VLCRecorder", "  - " + file.getName() + " (" + file.length() + " bytes)");
                    }
                } else {
                    Log.e("VLCRecorder", "  目录为空或无法访问");
                }
                if (callback != null) callback.onRecordingStopped(null, false);
            }
        } else {
            Log.e("VLCRecorder", "录制目录不存在: " + moviesDir);
            if (callback != null) callback.onRecordingStopped(null, false);
        }
    }
    
    /**
     * 开始计时
     */
    private void startTimer() {
        recordingTime = 0;
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                recordingTime++;
                String timeString = formatTime(recordingTime);
                if (callback != null) callback.onTimeUpdate(timeString);
                handler.postDelayed(this, 1000);
            }
        };
        handler.post(timerRunnable);
    }
    
    /**
     * 停止计时
     */
    private void stopTimer() {
        if (timerRunnable != null) {
            handler.removeCallbacks(timerRunnable);
            timerRunnable = null;
        }
    }
    
    /**
     * 格式化时间
     */
    private String formatTime(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, secs);
    }
    
    /**
     * 是否正在录制
     */
    public boolean isRecording() {
        return isRecording;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        if (isRecording) {
            stopRecording();
        }
        stopTimer();
        handler.removeCallbacksAndMessages(null);
    }
} 