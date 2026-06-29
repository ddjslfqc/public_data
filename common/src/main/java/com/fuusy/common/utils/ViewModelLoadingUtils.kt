package com.fuusy.common.utils

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel专用的Loading工具类
 * 方便在ViewModel中使用统一的loading
 */
object ViewModelLoadingUtils {
    
    /**
     * 在ViewModel中执行带loading的网络请求
     * @param context 上下文
     * @param viewModel ViewModel实例
     * @param request 网络请求函数
     * @param onSuccess 成功回调
     * @param onError 错误回调
     */
    fun <T> requestWithLoading(
        context: Context,
        viewModel: ViewModel,
        request: suspend () -> T,
        onSuccess: (T) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModel.viewModelScope.launch {
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
    }
    
    /**
     * 在ViewModel中执行带loading的网络请求（简化版本）
     * @param context 上下文
     * @param viewModel ViewModel实例
     * @param request 网络请求函数
     * @param onComplete 完成回调（包含结果和错误信息）
     */
    fun <T> requestWithLoading(
        context: Context,
        viewModel: ViewModel,
        request: suspend () -> T,
        onComplete: (result: T?, error: String?) -> Unit
    ) {
        viewModel.viewModelScope.launch {
            try {
                // 显示loading
                LoadingUtils.showLoading(context)
                
                // 执行网络请求
                val result = withContext(Dispatchers.IO) {
                    request()
                }
                
                // 隐藏loading
                LoadingUtils.hideLoading()
                
                // 完成回调
                onComplete(result, null)
                
            } catch (e: Exception) {
                // 隐藏loading
                LoadingUtils.hideLoading()
                
                // 完成回调
                onComplete(null, e.message ?: "网络请求失败")
            }
        }
    }
} 