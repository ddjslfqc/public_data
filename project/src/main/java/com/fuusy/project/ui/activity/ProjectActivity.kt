package com.fuusy.project.ui.activity

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.base.BaseActivity
import com.fuusy.common.network.net.IStateObserver
import com.fuusy.project.R
import com.fuusy.project.adapter.ProjectItemAdapter
import com.fuusy.project.bean.AppDatabase
import com.fuusy.project.bean.ProjectItem
import com.fuusy.project.databinding.ActivityProjectBinding
import com.fuusy.project.viewmodel.ProjectViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import kotlinx.coroutines.launch
import android.util.Log
import com.fuusy.common.network.BaseResp
import com.fuusy.common.utils.SpUtils
import com.google.gson.Gson

@Route(path = "/project/ProjectActivity")
class ProjectActivity : BaseActivity<ActivityProjectBinding>() {

    private val mViewModel: ProjectViewModel by viewModel()
    private lateinit var adapter: ProjectItemAdapter
    private var selectedItems: List<ProjectItem> = emptyList()

    override fun getLayoutId(): Int = R.layout.activity_project

    override fun initData(savedInstanceState: Bundle?) {
        setupViews()
        setupRecyclerView()
        observeViewModel()
        mBinding.swipeRefresh.setOnRefreshListener { loadData() }
        loadData()
    }

    private fun setupViews() {
        mBinding?.apply {
            toolbar.title = "项目列表"
            btnConfirm.setOnClickListener {
                handleConfirmClick()
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = ProjectItemAdapter()
        adapter.setOnSelectionChangedListener { selectedItems ->
            this.selectedItems = selectedItems
            updateConfirmButton()
        }

        mBinding?.recyclerView?.apply {
            layoutManager = LinearLayoutManager(this@ProjectActivity)
            adapter = this@ProjectActivity.adapter
        }
    }

    private fun observeViewModel() {
        // 监听loading状态
        mViewModel.loadingStatus.observe(this) { status ->
            when (status) {
                is com.fuusy.common.utils.LoadingStatus.Idle -> {
                    com.fuusy.common.utils.LoadingUtils.hideLoading()
                }
                is com.fuusy.common.utils.LoadingStatus.Loading -> {
                    com.fuusy.common.utils.LoadingUtils.showLoading(this)
                }
                is com.fuusy.common.utils.LoadingStatus.Success -> {
                    com.fuusy.common.utils.LoadingUtils.hideLoading()
                }
                is com.fuusy.common.utils.LoadingStatus.Error -> {
                    com.fuusy.common.utils.LoadingUtils.hideLoading()
                    showToast("加载失败：${status.message}")
                }
            }
        }
        
        mViewModel.mItemListLiveData.observe(
            this, object : IStateObserver<List<ProjectItem>>(null) {
                override fun onDataChange(data: List<ProjectItem>?) {
                    data?.let {
                        adapter.setItems(it)
                    }
                    mBinding.swipeRefresh.isRefreshing = false
                }

                override fun onReload(p0: View?) {
                    loadData()
                }
            })
    }

    private fun loadData() {
        mViewModel.getItemList()
    }

    private fun handleConfirmClick() {
        if (selectedItems.isNotEmpty()) {
            handleSelectedItems(selectedItems)
        } else {
            showToast("请先选择项目")
        }
    }

    private fun updateConfirmButton() {
        mBinding.btnConfirm.let {
            it.isEnabled = selectedItems.isNotEmpty()
            it.text = "进入项目"
            // 根据选中状态改变按钮背景颜色
            it.setBackgroundResource(R.drawable.common_bg_select)
        }
    }

    private fun isSelectItem(): Boolean {
        selectedItems.forEach { item ->
            if (item.isSelected) {
                return true
            }
        }
        return false
    }

    private fun handleSelectedItems(items: List<ProjectItem>) {
        val allProjects = mViewModel.mItemListLiveData.value
        Log.d("ProjectDebug", "mItemListLiveData.value = $allProjects, type = ${allProjects?.javaClass}")
        val projectList = when (allProjects) {
            is List<*> -> allProjects.filterIsInstance<ProjectItem>()
            is BaseResp<*> -> (allProjects.data as? List<*>)?.filterIsInstance<ProjectItem>() ?: emptyList()
            else -> emptyList()
        }
        Log.d("ProjectDebug", "projectList.size = ${projectList.size}")
        // 单选逻辑：只允许一个被选中
        val selectedItem = items.firstOrNull()
        
        // 保存选中的项目到 SharedPreferences
        selectedItem?.let { project ->
            val gson = Gson()
            val projectJson = gson.toJson(project)
            SpUtils.put("selected_project", projectJson)
            Log.d("ProjectDebug", "保存项目到SP: $projectJson")
        }
        
        val allWithSelection = projectList.map { project ->
            project.copy(isSelected = (selectedItem != null && 
                project.item == selectedItem.item && 
                project.device == selectedItem.device))
        }
        Log.d("ProjectDebug", "保存项目数量: ${allWithSelection.size}")
        lifecycleScope.launch {
            val db = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java, "app_database"
            ).build()
            db.projectItemDao().deleteAll()
            db.projectItemDao().insertAll(allWithSelection)
            ARouter.getInstance()
                .build("/project/ProjectDetailActivity")
                .navigation()
            finish()
        }
    }
}
