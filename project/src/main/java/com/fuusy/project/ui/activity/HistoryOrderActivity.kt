package com.fuusy.project.ui.activity

import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.project.R
import com.fuusy.project.databinding.ActivityHistoryOrderBinding

@Route(path = "/project/HistoryOrderActivity")
class HistoryOrderActivity : BaseVmActivity<ActivityHistoryOrderBinding>() {

    override fun getLayoutId(): Int = R.layout.activity_history_order

    override fun initData() {
        mBinding.btnBack.setOnClickListener { finish() }
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            val showActivePending = intent.getBooleanExtra(EXTRA_SHOW_ACTIVE_PENDING, false)
            val showCompleted = intent.getBooleanExtra(EXTRA_SHOW_COMPLETED, false)
            if (showCompleted) {
                mBinding.tvTitle.text = "完成任务"
            }
            supportFragmentManager.beginTransaction()
                .replace(
                    R.id.fragment_container,
                    WorkOrderListFragment.newInstance(
                        showActivePending = showActivePending,
                        showCompletedOnly = showCompleted
                    )
                )
                .commit()
        }
    }

    companion object {
        const val EXTRA_SHOW_ACTIVE_PENDING = "show_active_pending"
        const val EXTRA_SHOW_COMPLETED = "show_completed"
    }
}
