package com.fuusy.project.utils

import android.app.Dialog
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.fuusy.project.R

object LoadingDialogUtils {
    
    private var loadingDialog: Dialog? = null
    
    /**
     * 显示loading对话框
     */
    fun showLoading(context: Context, message: String = "处理中...") {
        hideLoading() // 先隐藏之前的loading
        
        loadingDialog = Dialog(context, R.style.TransparentDialog)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
        view.findViewById<TextView>(R.id.tv_loading_message).text = message
        
        loadingDialog?.apply {
            setContentView(view)
            setCancelable(false)
            setCanceledOnTouchOutside(false)
            show()
        }
    }
    
    /**
     * 隐藏loading对话框
     */
    fun hideLoading() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }
} 