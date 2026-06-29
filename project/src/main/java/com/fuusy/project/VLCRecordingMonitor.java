package com.fuusy.project;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import java.io.File;

/**
 * VLC 录制状态监控器
 * 用于实时检测录制是否成功进行
 */
public class VLCRecordingMonitor {
    
    private static final String TAG = "VLCRecordingMonitor";
    private static final int CHECK_INTERVAL = 2000; // 每2秒检查一次
    
    private Context context;
    private Handler handler;
    private Runnable monitorRunnable;
    private String recordDirectory;
    private File lastRecordFile;
    private long lastFileSize;
    private boolean isMonitoring = false;
    
    // 监控回调
    public interface RecordingMonitorCallback {
        void onRecordingStarted(File recordFile);
        void onRecordingProgress(File recordFile, long currentSize, long duration);
        void onRecordingStopped(File recordFile, long finalSize);
        void onRecordingError(String error);
    }
    
    private RecordingMonitorCallback callback;
    
    public VLCRecordingMonitor(Context context) {
        this.context = context;
        this.handler = new Handler(Looper.getMainLooper());
        // 使用正确的Movies目录作为默认路径
        this.recordDirectory = context.getExternalFilesDir(android.os.Environment.DIRECTORY_MOVIES).getAbsolutePath();
    }
    
    public void setCallback(RecordingMonitorCallback callback) {
        this.callback = callback;
    }
    
    public void setRecordDirectory(String directory) {
        this.recordDirectory = directory;
    }
    
    /**
     * 开始监控录制状态
     */
    public void startMonitoring() {
        if (isMonitoring) {
            Log.w(TAG, "监控已在运行中");
            return;
        }
        
        isMonitoring = true;
        lastRecordFile = null;
        lastFileSize = 0;
        
        monitorRunnable = new Runnable() {
            @Override
            public void run() {
                if (isMonitoring) {
                    checkRecordingStatus();
                    handler.postDelayed(this, CHECK_INTERVAL);
                }
            }
        };
        
        handler.post(monitorRunnable);
        Log.d(TAG, "开始监控录制状态");
    }
    
    /**
     * 停止监控
     */
    public void stopMonitoring() {
        isMonitoring = false;
        if (monitorRunnable != null) {
            handler.removeCallbacks(monitorRunnable);
            monitorRunnable = null;
        }
        
        // 如果正在录制，通知录制停止
        if (lastRecordFile != null && lastRecordFile.exists()) {
            if (callback != null) {
                callback.onRecordingStopped(lastRecordFile, lastRecordFile.length());
            }
        }
        
        Log.d(TAG, "停止监控录制状态");
    }
    
    /**
     * 检查录制状态
     */
    private void checkRecordingStatus() {
        try {
            File directory = new File(recordDirectory);
            if (!directory.exists() || !directory.isDirectory()) {
                return;
            }
            
            // 查找最新的录制文件
            File[] files = directory.listFiles((dir, name) -> 
                name.toLowerCase().endsWith(".mp4") || name.toLowerCase().endsWith(".ts"));
            
            if (files == null || files.length == 0) {
                return;
            }
            
            // 找到最新的文件
            File latestFile = files[0];
            for (File file : files) {
                if (file.lastModified() > latestFile.lastModified()) {
                    latestFile = file;
                }
            }
            
            long currentSize = latestFile.length();
            
            // 如果是新文件或文件大小在增长
            if (lastRecordFile == null || !lastRecordFile.equals(latestFile)) {
                // 新录制文件
                lastRecordFile = latestFile;
                lastFileSize = currentSize;
                
                if (callback != null) {
                    callback.onRecordingStarted(latestFile);
                }
                
                Log.d(TAG, "检测到新的录制文件: " + latestFile.getName() + ", 大小: " + currentSize);
                
            } else if (currentSize > lastFileSize) {
                // 文件在增长，录制进行中
                long duration = System.currentTimeMillis() - latestFile.lastModified();
                
                if (callback != null) {
                    callback.onRecordingProgress(latestFile, currentSize, duration);
                }
                
                lastFileSize = currentSize;
                Log.d(TAG, "录制进行中: " + latestFile.getName() + ", 大小: " + currentSize + ", 时长: " + duration + "ms");
                
            } else if (currentSize == lastFileSize && currentSize > 0) {
                // 文件大小未变化，可能录制已停止
                Log.d(TAG, "录制可能已停止: " + latestFile.getName() + ", 最终大小: " + currentSize);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "检查录制状态失败: " + e.getMessage());
            if (callback != null) {
                callback.onRecordingError("检查录制状态失败: " + e.getMessage());
            }
        }
    }
    
    /**
     * 获取当前录制文件
     */
    public File getCurrentRecordFile() {
        return lastRecordFile;
    }
    
    /**
     * 获取当前录制文件大小
     */
    public long getCurrentFileSize() {
        return lastFileSize;
    }
    
    /**
     * 是否正在监控
     */
    public boolean isMonitoring() {
        return isMonitoring;
    }
    
    /**
     * 释放资源
     */
    public void release() {
        if (isMonitoring) {
            stopMonitoring();
        }
        // 将handler清理放到子线程
        Thread cleanupThread = new Thread(() -> {
            try {
                handler.removeCallbacksAndMessages(null);
            } catch (Exception e) {
                Log.e("VLCRecordingMonitor", "子线程清理handler失败: " + e.getMessage());
            }
        });
        cleanupThread.start();
    }
} 