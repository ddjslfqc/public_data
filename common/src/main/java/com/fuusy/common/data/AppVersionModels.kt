package com.fuusy.common.data

import com.google.gson.annotations.SerializedName

data class AppVersionCheckResult(
    val hasUpdate: Boolean = false,
    val versionCode: Int? = null,
    val versionName: String? = null,
    val fileUrl: String? = null,
    val fileSize: Long? = null,
    val updateLog: String? = null,
    val releaseTime: String? = null,
    val message: String? = null,
    @SerializedName("isForceUpdate")
    private val forceUpdateValue: Any? = null
) {
    val isForceUpdate: Boolean
        get() = when (forceUpdateValue) {
            is Boolean -> forceUpdateValue
            is Number -> forceUpdateValue.toInt() == 1
            is String -> forceUpdateValue == "1" ||
                forceUpdateValue.equals("true", ignoreCase = true)
            else -> false
        }

    fun fileSizeLabel(): String? {
        val size = fileSize ?: return null
        return when {
            size >= 1024 * 1024 -> String.format("%.2fMB", size / 1024.0 / 1024.0)
            size >= 1024 -> String.format("%.1fKB", size / 1024.0)
            else -> "${size}B"
        }
    }
}
