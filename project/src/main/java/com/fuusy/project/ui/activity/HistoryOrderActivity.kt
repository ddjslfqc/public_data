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
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, WorkOrderListFragment.newInstance())
                .commit()
        }
    }
}
