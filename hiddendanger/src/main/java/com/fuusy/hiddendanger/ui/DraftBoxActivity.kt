package com.fuusy.hiddendanger.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.ActivityDraftBoxBinding
import com.fuusy.hiddendanger.ui.adapter.DraftBoxAdapter

@Route(path = "/hiddendanger/DraftBoxActivity")
class DraftBoxActivity : BaseVmActivity<ActivityDraftBoxBinding>() {
    private val viewModel: DraftBoxViewModel by viewModels()
    private val adapter = DraftBoxAdapter(onEdit = { draft ->
        // 跳转创建工单页面并回填数据
        val intent = Intent(this, CreateWorkOrderActivity::class.java)
        intent.putExtra("draft_data", draft)
        startActivity(intent)
        finish()
    }, onDelete = { draft ->
        viewModel.deleteDraft(draft)
    })

    override fun getLayoutId() = com.fuusy.hiddendanger.R.layout.activity_draft_box

    override fun initData() {
        mBinding.recyclerView.layoutManager = LinearLayoutManager(this)
        mBinding.recyclerView.adapter = adapter
        mBinding.swipeRefresh.setOnRefreshListener {
            viewModel.loadDrafts()
        }
        viewModel.draftList.observe(this) {
            adapter.submitList(it)
            val isEmpty = it.isNullOrEmpty()
            mBinding.emptyView.root.visibility =
                if (isEmpty) android.view.View.VISIBLE else android.view.View.GONE
            val emptyView = mBinding.emptyView.root.findViewById(R.id.emptyView) as View
            emptyView.findViewById<TextView>(com.fuusy.common.R.id.tv_empty).text = "暂无草稿"
            mBinding.recyclerView.visibility =
                if (isEmpty) android.view.View.GONE else android.view.View.VISIBLE
            mBinding.swipeRefresh.isRefreshing = false
        }
        viewModel.loadDrafts()
        mBinding.btnBack.setOnClickListener {
            finish()
        }
    }
} 