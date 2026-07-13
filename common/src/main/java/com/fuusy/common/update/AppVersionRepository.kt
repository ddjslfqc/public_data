package com.fuusy.common.update

import android.content.Context
import android.content.pm.PackageManager
import com.fuusy.common.data.AppVersionCheckResult
import com.fuusy.common.network.AppVersionApi
import com.fuusy.common.network.RetrofitManager
import com.fuusy.common.network.ServerConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AppVersionRepository {

    private fun api(): AppVersionApi =
        Retrofit.Builder()
            .baseUrl(ServerConfig.getWorkOrderBaseUrl())
            .client(RetrofitManager.client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(AppVersionApi::class.java)

    suspend fun checkUpdate(context: Context): Result<AppVersionCheckResult> = try {
        val versionCode = currentVersionCode(context)
        val resp = api().checkUpdate(versionCode = versionCode)
        if (resp.isSuccess && resp.data != null) {
            Result.success(resp.data!!)
        } else {
            Result.failure(IllegalStateException(resp.errorMsg ?: "检查更新失败(${resp.errorCode})"))
        }
    } catch (e: retrofit2.HttpException) {
        val hint = if (e.code() == 404) {
            "检查更新接口未找到(404)，请确认后端已部署 /mobile/app-version/check"
        } else {
            "HTTP ${e.code()}"
        }
        Result.failure(IllegalStateException(hint, e))
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun currentVersionCode(context: Context): Int = readPackageInfo(context)?.let { info ->
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            info.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            info.versionCode
        }
    }?.let { code ->
        alignWithBaseline(code, currentVersionName(context))
    } ?: AppVersionBaseline.VERSION_CODE

    fun currentVersionName(context: Context): String =
        readPackageInfo(context)?.versionName.orEmpty().ifBlank { AppVersionBaseline.VERSION_NAME }

    /** 若显示版本已是基线 1.0.1，则 versionCode 至少按 101 参与比较（避免 code 未同步误弹窗） */
    private fun alignWithBaseline(packageCode: Int, packageName: String): Int {
        val normalized = packageName.trim().removePrefix("v").removePrefix("V").trim()
        return if (normalized == AppVersionBaseline.VERSION_NAME) {
            maxOf(packageCode, AppVersionBaseline.VERSION_CODE)
        } else {
            packageCode
        }
    }

    private fun readPackageInfo(context: Context) = try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.PackageInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0)
        }
    } catch (_: Exception) {
        null
    }
}
