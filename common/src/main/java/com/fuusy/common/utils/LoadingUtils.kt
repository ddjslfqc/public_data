package com.fuusy.common.utils

import android.app.Activity
import android.content.Context
import com.fuusy.common.widget.LoadingDialog

/**
 * 统一的Loading工具类
 * 提供全局的loading管理，确保loading不能被用户取消
 */
object LoadingUtils {
    
    private var loadingDialog: LoadingDialog? = null
    
    /**
     * 显示loading对话框
     * @param context 上下文
     */
    fun showLoading(context: Context) {
        hideLoading() // 先隐藏之前的loading
        
        if (context is Activity && context.isFinishing) {
            return
        }
        
        loadingDialog = LoadingDialog(context, true) // true表示不可取消
        loadingDialog?.show()
    }
    
    /**
     * 隐藏loading对话框
     */
    fun hideLoading() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }
    
    /**
     * 检查是否正在显示loading
     */
    fun isLoading(): Boolean {
        return loadingDialog?.isShowing == true
    }
} 