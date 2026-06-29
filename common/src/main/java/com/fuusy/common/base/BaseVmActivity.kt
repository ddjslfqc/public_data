package com.fuusy.common.base

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import com.alibaba.android.arouter.utils.TextUtils
import com.fuusy.common.support.StatusBar
import com.fuusy.common.utils.ToastUtil
import com.fuusy.common.utils.LoadingUtils

private const val TAG = "BaseVmActivity"

abstract class BaseVmActivity<T : ViewDataBinding> : AppCompatActivity {

    constructor() : super()



    lateinit var mBinding: T

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        StatusBar().fitSystemBar(this)
        mBinding = DataBindingUtil.setContentView(this, getLayoutId())

        initData()

    }


    fun showToast(msg: String) {
        // 全局不弹错误类吐司
        if (msg.isNotEmpty()) {
            val lower = msg.lowercase()
            val blockKeywords = listOf("失败", "错误", "异常", "超时", "拒绝", "未获取", "找不到", "无权限")
            if (blockKeywords.any { lower.contains(it) }) return
        }
        ToastUtil.showCustomToast(this, msg)
    }

    override fun onDestroy() {
        super.onDestroy()
        mBinding?.unbind()
    }

    abstract fun initData()

    abstract fun getLayoutId(): Int


    /**
     * show 加载中
     */
    fun showLoading() {
        LoadingUtils.showLoading(this)
    }

    /**
     * dismiss loading dialog
     */
    fun dismissLoading() {
        LoadingUtils.hideLoading()
    }

    /**
     * 设置toolbar名称
     */
    protected fun setToolbarTitle(view: TextView, title: String) {
        view.text = title
    }

    /**
     * 设置toolbar返回按键图片
     */
    protected fun setToolbarBackIcon(view: ImageView, id: Int) {
        view.setBackgroundResource(id)
    }

}