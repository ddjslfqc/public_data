package com.fuusy.common.utils

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 网络请求工具类示例
 * 展示如何在网络请求中使用统一的loading
 */
object NetworkUtils {
    
    /**
     * 带loading的网络请求示例
     * @param context 上下文
     * @param request 网络请求函数
     * @param onSuccess 成功回调
     * @param onError 错误回调
     */
    suspend fun <T> requestWithLoading(
        context: Context,
        request: suspend () -> T,
        onSuccess: (T) -> Unit,
        onError: (String) -> Unit
    ) {
        try {
            // 显示loading
            LoadingUtils.showLoading(context)
            
            // 执行网络请求
            val result = withContext(Dispatchers.IO) {
                request()
            }
            
            // 隐藏loading
            LoadingUtils.hideLoading()
            
            // 成功回调
            onSuccess(result)
            
        } catch (e: Exception) {
            // 隐藏loading
            LoadingUtils.hideLoading()
            
            // 错误回调
            onError(e.message ?: "网络请求失败")
        }
    }
    
    /**
     * 异步网络请求示例（使用协程）
     * @param context 上下文
     * @param request 网络请求函数
     * @param onSuccess 成功回调
     * @param onError 错误回调
     */
    fun <T> requestAsyncWithLoading(
        context: Context,
        scope: CoroutineScope,
        request: suspend () -> T,
        onSuccess: (T) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            requestWithLoading(context, request, onSuccess, onError)
        }
    }
} 