package com.fuusy.project.ui.activity

import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.project.R
import com.fuusy.project.databinding.ActivityHistoryOrderBinding

@Route(path = "/project/HistoryOrderActivity")
class HistoryOrderActivity : BaseVmActivity<ActivityHistoryOrderBinding>() {

    override fun getLayoutId(): Int = R.layout.activity_history_order

    override fun initData() {
        applyStatusBarPadding()
        mBinding.btnBack.setOnClickListener { finish() }
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) != null) return

        val listMode = intent.getStringExtra(EXTRA_LIST_MODE).orEmpty().ifBlank {
            when {
                intent.getBooleanExtra(EXTRA_SHOW_COMPLETED, false) -> MODE_COMPLETED
                intent.getBooleanExtra(EXTRA_SHOW_ACTIVE_PENDING, false) -> MODE_RELATED
                else -> MODE_RELATED
            }
        }

        mBinding.tvTitle.text = when (listMode) {
            MODE_COMPLETED -> "完成任务"
            else -> "我相关的任务"
        }

        supportFragmentManager.beginTransaction()
            .replace(
                R.id.fragment_container,
                WorkOrderListFragment.newInstance(listMode = listMode)
            )
            .commit()
    }

    private fun applyStatusBarPadding() {
        ViewCompat.setOnApplyWindowInsetsListener(mBinding.topBar) { view, insets ->
            val statusBarTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.updatePadding(top = statusBarTop)
            insets
        }
        ViewCompat.requestApplyInsets(mBinding.topBar)
    }

    companion object {
        const val EXTRA_LIST_MODE = "list_mode"
        const val EXTRA_SHOW_ACTIVE_PENDING = "show_active_pending"
        const val EXTRA_SHOW_COMPLETED = "show_completed"

        const val MODE_RELATED = "related"
        const val MODE_COMPLETED = "completed"
    }
}
