package com.fuusy.project.ui.activity

import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.GravityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.room.Room
import cn.onestravel.one.navigation.androidx.OnItemSelectedListener
import com.alibaba.android.arouter.facade.annotation.Route
import com.alibaba.android.arouter.launcher.ARouter
import com.fuusy.common.base.BaseActivity
import com.fuusy.common.support.Constants
import com.fuusy.project.R
import com.fuusy.project.adapter.ProjectItemAdapter
import com.fuusy.project.repo.VideoInfo
import com.fuusy.project.bean.AppDatabase
import com.fuusy.project.bean.ProjectItem
import com.fuusy.project.databinding.ActivityProjectDetailContainerBinding
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Route(path = "/project/ProjectDetailActivity")
class ProjectDetailActivity : BaseActivity<ActivityProjectDetailContainerBinding>() {

    private lateinit var homeFragment: HomeFragment
    private var videoFragment: VideoListFragment? = null
    private var workOrderFragment: Fragment? = null
    private var personalFragment: Fragment? = null
    private var currentFragment: Fragment? = null

    private lateinit var drawerProjectAdapter: ProjectItemAdapter
    private lateinit var drawerRecyclerView: androidx.recyclerview.widget.RecyclerView
    private var selectedProjects: List<ProjectItem> = emptyList()

    private val TAG_HOME = "home_fragment"
    private val TAG_VIDEO = "video_fragment"
    private val TAG_WORK_ORDER = "work_order_fragment"
    private val TAG_PERSONAL = "personal_fragment"

    override fun initData(savedInstanceState: Bundle?) {
        getSelectedProjectsFromIntent()
        initFragments(savedInstanceState)
        initBottomNavigation()
        initClickListeners()
        initDrawer()
        switchToFragment(homeFragment)
    }

    private fun getSelectedProjectsFromIntent() {
        val projectsJson = intent.getStringExtra("selected_projects")
        if (!projectsJson.isNullOrEmpty()) {
            try {
                val gson = Gson()
                val type = object : TypeToken<List<ProjectItem>>() {}.type
                selectedProjects = gson.fromJson(projectsJson, type)
            } catch (e: Exception) {
                e.printStackTrace()
                selectedProjects = emptyList()
            }
        }
    }

    private fun initDrawer() {
        setupDrawerRecyclerView()
    }

    private fun handleProjectSwitch(selectedItems: List<ProjectItem>) {
        if (selectedItems.isNotEmpty()) {
            val selectedProject = selectedItems.first()
            Log.d("ProjectSwitch", "处理项目切换: ${selectedProject.item} (${selectedProject.itemName})")
            lifecycleScope.launch {
                withContext(Dispatchers.IO) {
                    val db = Room.databaseBuilder(
                        applicationContext,
                        AppDatabase::class.java, "app_database"
                    ).build()
                    val allProjects = db.projectItemDao().getAll()
                    val updatedList =
                        allProjects.map { it.copy(isSelected = (it.item == selectedProject.item && it.device == selectedProject.device)) }
                    db.projectItemDao().insertAll(updatedList)
                    withContext(Dispatchers.Main) {
                        drawerProjectAdapter.notifyDataSetChanged()
                        switchToProject(selectedProject)
                        mBinding.drawerLayout.closeDrawer(GravityCompat.START)
                    }
                }
            }
        } else {
            Log.w("ProjectSwitch", "没有选中的项目")
        }
    }

    private fun switchToProject(project: ProjectItem) {
        val projectIdentifier = "${project.item}_${project.device}"
        Log.d("ProjectSwitch", "切换到项目: $projectIdentifier")
        // 通知首页 Fragment 项目已切换
        homeFragment.onProjectSwitched(projectIdentifier)
    }

    override fun getLayoutId(): Int = R.layout.activity_project_detail_container

    private fun initFragments(savedInstanceState: Bundle?) {
        val fm = supportFragmentManager
        val existHome = fm.findFragmentByTag(TAG_HOME) as? HomeFragment

        homeFragment = existHome ?: run {
            val projectId = intent.getStringExtra(Constants.KEY_PROJECT_ID) ?: ""
            if (projectId.isNotEmpty()) HomeFragment.newInstance(projectId)
            else HomeFragment.newInstance()
        }

        // 如果是首次创建，预先添加首页
        if (existHome == null) {
            fm.beginTransaction()
                .add(R.id.fragment_container, homeFragment, TAG_HOME)
                .commit()
            currentFragment = homeFragment
        } else {
            currentFragment = homeFragment
        }
    }

    private fun initBottomNavigation() {
        try {
            mBinding.BottomLayout.setFragmentManager(
                supportFragmentManager,
                mBinding.bottomNavStubContainer
            )
        } catch (_: Throwable) {
        }

        mBinding.BottomLayout.setOnItemSelectedListener(object : OnItemSelectedListener {
            override fun invoke(item: cn.onestravel.one.navigation.androidx.OneBottomNavigationBar.Item, position: Int) {
                when (item.id) {
                    R.id.navigation_home -> {
                        switchToFragment(homeFragment)
                        true
                    }
                    R.id.navigation_video -> {
                        showVideoFragment()
                        true
                    }
                    R.id.navigation_work_order -> {
                        showWorkOrderFragment()
                        true
                    }
                    R.id.navigation_profile -> {
                        showPersonalFragment()
                        true
                    }
                    else -> false
                }
            }
        })
        mBinding.BottomLayout.setSelected(0)
    }

    fun switchToVideoTab() {
        mBinding.BottomLayout.setSelected(1)
    }

    fun switchToWorkOrderTab() {
        mBinding.BottomLayout.setSelected(2)
    }

    private fun showVideoFragment() {
        val fm = supportFragmentManager
        if (videoFragment == null) {
            videoFragment = VideoListFragment.newInstance()
            fm.beginTransaction()
                .add(R.id.fragment_container, videoFragment!!, TAG_VIDEO)
                .commit()
        }
        switchToFragment(videoFragment!!)
    }

    private fun showWorkOrderFragment() {
        val fm = supportFragmentManager
        if (workOrderFragment == null) {
            workOrderFragment = WorkOrderListFragment.newInstance()
            fm.beginTransaction()
                .add(R.id.fragment_container, workOrderFragment!!, TAG_WORK_ORDER)
                .commit()
        }
        switchToFragment(workOrderFragment!!)
    }

    private fun showPersonalFragment() {
        val fm = supportFragmentManager
        if (personalFragment == null) {
            try {
                personalFragment = ARouter.getInstance()
                    .build("/hiddendanger/PersonalFragment")
                    .navigation() as? Fragment
            } catch (_: Throwable) {
            }
        }
        personalFragment?.let { switchToFragment(it) }
    }

    private fun initClickListeners() {
        mBinding.tvSwitchProject.setOnClickListener { switchProject() }
    }

    fun switchProject() {
        if (mBinding.drawerLayout.isDrawerOpen(GravityCompat.START) == true) {
            mBinding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            mBinding.drawerLayout.openDrawer(GravityCompat.START)
        }
    }

    private fun setupDrawerRecyclerView() {
        drawerRecyclerView = mBinding.navView.findViewById(R.id.rv_projects)
        drawerProjectAdapter = ProjectItemAdapter()

        drawerRecyclerView.apply {
            layoutManager = LinearLayoutManager(this@ProjectDetailActivity)
            adapter = drawerProjectAdapter
        }

        lifecycleScope.launch {
            val db = Room.databaseBuilder(
                applicationContext,
                AppDatabase::class.java, "app_database"
            ).build()
            val allProjects = db.projectItemDao().getAll()
            drawerProjectAdapter.setItems(allProjects)
            val selected = allProjects.filter { it.isSelected }
            if (selected.isNotEmpty()) {
                drawerProjectAdapter.setOnSelectionChangedListener { selectedItems ->
                    handleProjectSwitch(selectedItems)
                }
                handleProjectSwitch(selected)
            } else {
                drawerProjectAdapter.setOnSelectionChangedListener { selectedItems ->
                    handleProjectSwitch(selectedItems)
                }
            }
        }
    }

    private fun switchToFragment(fragment: Fragment) {
        if (currentFragment === fragment) return
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction().setReorderingAllowed(true)

        currentFragment?.let { transaction.hide(it) }

        val tag = when (fragment) {
            homeFragment -> TAG_HOME
            videoFragment -> TAG_VIDEO
            workOrderFragment -> TAG_WORK_ORDER
            personalFragment -> TAG_PERSONAL
            else -> fragment::class.java.simpleName
        }
        val exist = fm.findFragmentByTag(tag)
        if (exist == null && !fragment.isAdded) {
            transaction.add(R.id.fragment_container, fragment, tag)
        } else {
            transaction.show(exist ?: fragment)
        }
        transaction.commit()
        currentFragment = fragment

        val topBar = findViewById<ConstraintLayout>(R.id.topBar)
        when (fragment) {
            homeFragment, personalFragment -> topBar?.visibility = View.GONE
            else -> topBar?.visibility = View.VISIBLE
        }
    }

    override fun onBackPressed() {
        if (mBinding.drawerLayout.isDrawerOpen(GravityCompat.START) == true) {
            mBinding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    companion object {
        fun newInstance(videoInfo: VideoInfo): android.content.Intent {
            return android.content.Intent().apply {
                putExtra(Constants.KEY_VIDEO_INFO, videoInfo)
            }
        }
    }
}
