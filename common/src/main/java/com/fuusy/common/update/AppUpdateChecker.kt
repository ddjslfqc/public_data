package com.fuusy.common.update

import android.util.Log
import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import com.azhon.appupdate.listener.OnButtonClickListener
import com.azhon.appupdate.manager.DownloadManager
import com.fuusy.common.data.AppVersionCheckResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object AppUpdateChecker {

    private const val TAG = "AppUpdateChecker"
    private val repository = AppVersionRepository()

    /** 是否应提示升级：以本地 versionCode / versionName 为准，不信任 hasUpdate 单独为 true */
    private fun shouldPromptUpdate(
        localCode: Int,
        localName: String,
        info: AppVersionCheckResult
    ): Boolean {
        val remoteCode = info.versionCode ?: return false
        if (info.fileUrl.isNullOrBlank()) return false

        val remoteName = normalizeVersionName(info.versionName)
        val localNorm = normalizeVersionName(localName)

        // 版本名相同（如都是 1.0.1）→ 不弹窗
        if (remoteName.isNotBlank() && remoteName == localNorm) {
            Log.d(TAG, "skip update: same versionName=$remoteName")
            return false
        }
        // 服务端 versionCode 未高于本地 → 不弹窗（忽略 hasUpdate）
        if (remoteCode <= localCode) {
            Log.d(TAG, "skip update: remoteCode=$remoteCode <= localCode=$localCode")
            return false
        }
        Log.d(TAG, "prompt update: remote=$remoteCode/$remoteName local=$localCode/$localNorm")
        return true
    }

    private fun normalizeVersionName(name: String?): String =
        name?.trim()?.removePrefix("v")?.removePrefix("V")?.trim().orEmpty()

    /**
     * 启动时检查更新。
     * @param onFinished 非强制更新时，用户取消或无需更新后回调
     */
    fun checkOnLaunch(
        activity: FragmentActivity,
        @DrawableRes smallIcon: Int,
        onFinished: () -> Unit
    ) {
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.checkUpdate(activity)
            }
            result.fold(
                onSuccess = { info ->
                    val localCode = repository.currentVersionCode(activity)
                    val localName = repository.currentVersionName(activity)
                    if (!shouldPromptUpdate(localCode, localName, info)) {
                        onFinished()
                        return@fold
                    }
                    showUpdateDialog(activity, info, smallIcon, onFinished)
                },
                onFailure = {
                    Log.w(TAG, "check update failed: ${it.message}")
                    onFinished()
                }
            )
        }
    }

    /** 手动检查更新（设置页等） */
    fun checkManually(
        activity: FragmentActivity,
        @DrawableRes smallIcon: Int
    ) {
        activity.lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                repository.checkUpdate(activity)
            }
            result.fold(
                onSuccess = { info ->
                    val localCode = repository.currentVersionCode(activity)
                    val localName = repository.currentVersionName(activity)
                    if (!shouldPromptUpdate(localCode, localName, info)) {
                        Toast.makeText(activity, "当前已是最新版本", Toast.LENGTH_SHORT).show()
                        return@fold
                    }
                    showUpdateDialog(activity, info, smallIcon) { }
                },
                onFailure = {
                    Toast.makeText(
                        activity,
                        it.message ?: "检查更新失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            )
        }
    }

    private fun showUpdateDialog(
        activity: FragmentActivity,
        info: AppVersionCheckResult,
        @DrawableRes smallIcon: Int,
        onFinished: () -> Unit
    ) {
        if (activity.isFinishing || activity.isDestroyed) return

        val apkName = "app-${info.versionName ?: info.versionCode}.apk"
        val manager = DownloadManager.Builder(activity).run {
            apkUrl(info.fileUrl!!)
            apkName(apkName)
            smallIcon(smallIcon)
            apkVersionCode(info.versionCode!!)
            apkVersionName(info.versionName ?: "v${info.versionCode}")
            info.fileSizeLabel()?.let { apkSize(it) }
            apkDescription(info.updateLog ?: info.message ?: "发现新版本，建议立即更新")
            showNotification(true)
            forcedUpgrade(info.isForceUpdate)
            onButtonClickListener(object : OnButtonClickListener {
                override fun onButtonClick(id: Int) {
                    if (id == OnButtonClickListener.CANCEL && !info.isForceUpdate) {
                        onFinished()
                    }
                }
            })
            build()
        }
        manager?.download()
    }
}
