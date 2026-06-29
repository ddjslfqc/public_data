package com.fuusy.common.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.databinding.ViewDataBinding
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.fuusy.common.utils.ToastUtil

abstract class BaseVmFragment<VDB : ViewDataBinding, VM : ViewModel> : Fragment() {

    protected lateinit var mDataBinding: VDB
    protected lateinit var mViewModel: VM

    abstract fun initContentView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): Int

    abstract fun initVariableId(): Int
    abstract fun initViewModel(): VM
    abstract fun initViewObservable()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View? {
        val layoutResID = initContentView(inflater, container, savedInstanceState)
        mDataBinding =
            androidx.databinding.DataBindingUtil.inflate(inflater, layoutResID, container, false)
        return mDataBinding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 初始化ViewModel
        mViewModel = initViewModel()

        // 将ViewModel绑定到DataBinding
        val variableId = initVariableId()
        mDataBinding.setVariable(variableId, mViewModel)
        mDataBinding.lifecycleOwner = this

        // 初始化视图观察者
        initViewObservable()
    }

    fun showToast(msg: String) {
        val lower = msg.lowercase()
        val blockKeywords = listOf("失败", "错误", "异常", "超时", "拒绝", "未获取", "找不到", "无权限")
        if (blockKeywords.any { lower.contains(it) }) return
        ToastUtil.showCustomToast(requireActivity(), msg)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mDataBinding.unbind()
    }
}