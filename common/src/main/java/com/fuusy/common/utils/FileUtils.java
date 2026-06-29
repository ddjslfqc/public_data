package com.fuusy.common.utils;

import android.content.Context;
import android.os.Build;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class FileUtils {

    public static String getPath() {
        String imageRootPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES) + File.separator + "picture";
        File dir = new File(imageRootPath);
        if (!dir.exists() && !dir.mkdirs()) {
            return null;
        }
        return imageRootPath;
    }

    /**
     * 获取应用存储路径（用于录屏文件）
     * Android 10+ 使用应用专属目录，Android 9 及以下使用外部存储
     */
    public static String getAppPath() {
        Context context = AppHelper.mContext;
        if (context == null) {
            Log.e("FileUtils", "AppHelper.mContext 为 null，无法获取应用路径");
            return null;
        }
        
        String appDir;
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 使用应用专属目录，无需存储权限
            File externalFilesDir = context.getExternalFilesDir(Environment.DIRECTORY_MOVIES);
            if (externalFilesDir == null) {
                Log.e("FileUtils", "context.getExternalFilesDir() 返回 null");
                return null;
            }
            appDir = externalFilesDir.getAbsolutePath() + File.separator + "ComponentJetpackMVVM";
            Log.d("FileUtils", "Android 10+ 使用应用专属目录: " + appDir);
        } else {
            // Android 9 及以下使用外部存储
            String externalStorageDir = Environment.getExternalStorageDirectory().getAbsolutePath();
            appDir = externalStorageDir + File.separator + "ComponentJetpackMVVM";
            Log.d("FileUtils", "Android 9 及以下使用外部存储: " + appDir);
        }
        
        File dir = new File(appDir);
        Log.d("FileUtils", "检查目录是否存在: " + dir.getAbsolutePath() + ", exists: " + dir.exists());
        
        if (!dir.exists()) {
            Log.d("FileUtils", "目录不存在，尝试创建: " + dir.getAbsolutePath());
            boolean created = dir.mkdirs();
            Log.d("FileUtils", "目录创建结果: " + created);
            
            if (!created) {
                Log.e("FileUtils", "无法创建应用目录: " + appDir);
                // 检查父目录权限
                File parentDir = dir.getParentFile();
                if (parentDir != null) {
                    Log.e("FileUtils", "父目录: " + parentDir.getAbsolutePath() + ", exists: " + parentDir.exists() + ", canWrite: " + parentDir.canWrite());
                }
                return null;
            }
        }
        
        Log.d("FileUtils", "应用目录创建成功: " + appDir);
        return appDir;
    }

    /**
     * 获取录屏文件存储路径
     */
    public static String getScreenRecordPath() {
        String basePath = getAppPath();
        if (basePath == null) {
            Log.e("FileUtils", "getAppPath() 返回 null，无法创建录屏目录");
            return null;
        }
        
        String recordDir = basePath + File.separator + "ScreenRecords";
        File dir = new File(recordDir);
        Log.d("FileUtils", "检查录屏目录是否存在: " + dir.getAbsolutePath() + ", exists: " + dir.exists());
        
        if (!dir.exists()) {
            Log.d("FileUtils", "录屏目录不存在，尝试创建: " + dir.getAbsolutePath());
            boolean created = dir.mkdirs();
            Log.d("FileUtils", "录屏目录创建结果: " + created);
            
            if (!created) {
                Log.e("FileUtils", "无法创建录屏目录: " + recordDir);
                // 检查父目录权限
                File parentDir = dir.getParentFile();
                if (parentDir != null) {
                    Log.e("FileUtils", "录屏父目录: " + parentDir.getAbsolutePath() + ", exists: " + parentDir.exists() + ", canWrite: " + parentDir.canWrite());
                }
                return null;
            }
        }
        
        Log.d("FileUtils", "录屏目录创建成功: " + recordDir);
        return recordDir;
    }

    /**
     * 生成录屏文件名
     */
    public static String generateScreenRecordFileName(int channelNum) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        String currentTime = dateFormat.format(new Date());
        return "channel" + channelNum + "_" + currentTime + ".mp4";
    }

    /**
     * 检查文件是否存在且大小大于0
     */
    public static boolean isFileValid(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        File file = new File(filePath);
        return file.exists() && file.length() > 0;
    }

    /**
     * 获取文件大小（字节）
     */
    public static long getFileSize(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return 0;
        }
        File file = new File(filePath);
        return file.exists() ? file.length() : 0;
    }

    /**
     * 格式化文件大小
     */
    public static String formatFileSize(long sizeInBytes) {
        String[] units = {"B", "KB", "MB", "GB"};
        double size = sizeInBytes;
        int unitIndex = 0;
        
        while (size >= 1024 && unitIndex < units.length - 1) {
            size /= 1024;
            unitIndex++;
        }
        
        return String.format("%.2f %s", size, units[unitIndex]);
    }

    /**
     * 删除文件
     */
    public static boolean deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }
        File file = new File(filePath);
        return file.exists() && file.delete();
    }

    /**
     * 重命名文件
     */
    public static boolean renameFile(String oldPath, String newPath) {
        if (oldPath == null || newPath == null || oldPath.isEmpty() || newPath.isEmpty()) {
            return false;
        }
        
        File oldFile = new File(oldPath);
        File newFile = new File(newPath);
        
        // 确保目标目录存在
        File parentDir = newFile.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            Log.e("FileUtils", "无法创建目标目录: " + parentDir.getAbsolutePath());
            return false;
        }
        
        return oldFile.exists() && oldFile.renameTo(newFile);
    }

    /**
     * 获取目录下的所有文件
     */
    public static File[] getFilesInDirectory(String dirPath) {
        if (dirPath == null || dirPath.isEmpty()) {
            return new File[0];
        }
        
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            return dir.listFiles();
        }
        return new File[0];
    }

    /**
     * 清理临时文件
     */
    public static void cleanupTempFiles(String dirPath, String prefix) {
        if (dirPath == null || dirPath.isEmpty()) {
            return;
        }
        
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().startsWith(prefix)) {
                        if (file.delete()) {
                            Log.d("FileUtils", "删除临时文件: " + file.getAbsolutePath());
                        } else {
                            Log.e("FileUtils", "删除临时文件失败: " + file.getAbsolutePath());
                        }
                    }
                }
            }
        }
    }

    public static String getVideoPath(String tongDao, String currentTime) {
        String path = getPath();
        String videoPath = path + File.separator + tongDao + currentTime + "_1.mp4";
        Log.e("视频保存文件路径", videoPath);
        return videoPath;
    }

    public static String getVideoPathAvi(String tongDao, String currentTime) {
        String path = getPath();
        String videoPath = path + File.separator + tongDao + currentTime + "_1.avi";
        Log.e("视频保存文件路径", videoPath);
        return videoPath;
    }

//    public static String downLoadPdfPath(String pathName, String fileName) {
//        String rootDirectory = .getApplication().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
//                + File.separator + "pdf" + File.separator + pathName;
//
//        File dir = new File(rootDirectory);
//        if (!dir.exists() && !dir.mkdirs()) {
//            return null;
//        }
//        for (File file : dir.listFiles()) {
//
//        }
//        return rootDirectory + File.separator + fileName + ".pdf";
//    }

//    public static String getPdfPath() {
//        String rootDirectory = App.getApplication().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
//                + File.separator + "pdf";
//        File dir = new File(rootDirectory);
//        if (!dir.exists() && !dir.mkdirs()) {
//            return null;
//        }
//        return rootDirectory;
//    }
//
//    public static String getPdfPathName(String path) {
//        String rootDirectory = App.getApplication().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
//                + File.separator + "pdf" + File.separator + path;
//        File dir = new File(rootDirectory);
//        if (!dir.exists() && !dir.mkdirs()) {
//            return null;
//        }
//        return rootDirectory;
//    }

}
