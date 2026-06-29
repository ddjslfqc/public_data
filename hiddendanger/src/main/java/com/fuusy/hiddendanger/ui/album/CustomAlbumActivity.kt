package com.fuusy.hiddendanger.ui.album

import android.app.Activity
import androidx.activity.viewModels
import androidx.recyclerview.widget.GridLayoutManager
import com.fuusy.hiddendanger.R
import com.fuusy.hiddendanger.databinding.ActivityCustomAlbumBinding
import com.fuusy.hiddendanger.ui.album.util.GridSpacingItemDecoration
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Build
import android.util.Log
import android.widget.TextView
import com.fuusy.common.base.BaseVmActivity
import com.fuusy.common.utils.ToastUtil
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay


class CustomAlbumActivity : BaseVmActivity<ActivityCustomAlbumBinding>() {
    private val viewModel by viewModels<CustomAlbumViewModel>()
    private lateinit var customAlbumAdapter: CustomAlbumAdapter
    private lateinit var btnList: Array<TextView>
    private var mode: String = "select"
    
    // 定时刷新相关
    private var refreshJob: kotlinx.coroutines.Job? = null
    private val refreshInterval = 5000L // 5秒刷新一次

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 101
        private val REQUIRED_PERMISSIONS = mutableListOf<String>().apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.READ_MEDIA_IMAGES)
                add(Manifest.permission.READ_MEDIA_VIDEO)
            } else {
                add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }.toTypedArray()
    }

    override fun getLayoutId() = R.layout.activity_custom_album

    override fun initData() {
        // 读取模式
        mode = intent.getStringExtra("mode") ?: "select"
        // 恢复已选id
        val selectedIds = intent.getStringArrayExtra("selected_ids")?.toMutableSet() ?: mutableSetOf()
        val pathToIdMapping = intent.getSerializableExtra("path_to_id_mapping") as? HashMap<String, String> ?: hashMapOf()
        
        android.util.Log.d("CustomAlbumActivity", "=== CustomAlbumActivity接收到的数据 ===")
        android.util.Log.d("CustomAlbumActivity", "接收到的selectedIds: ${selectedIds.joinToString()}")
        android.util.Log.d("CustomAlbumActivity", "selectedIds数量: ${selectedIds.size}")
        android.util.Log.d("CustomAlbumActivity", "接收到的路径映射: $pathToIdMapping")
        android.util.Log.d("CustomAlbumActivity", "=== 接收数据结束 ===")
        viewModel.selected.value = selectedIds
        // 1. 适配器
        customAlbumAdapter =
            CustomAlbumAdapter(
                getSelected = { viewModel.selected.value ?: emptySet() },
                mode = mode,
                onItemClick = { item ->
                if (mode == "view") {
                    // 如果是视频且为 avi 或 ts，用VLC在内嵌Fragment播放，保持样式一致
                    if (item.type == AlbumMediaItem.MediaType.VIDEO && (item.path.endsWith(".avi", true) || item.path.endsWith(".ts", true))) {
                        openVideoPlayerFragment(item.path, useVlc = true)
                    } else {
                        // 原预览逻辑
                        val allItems = viewModel.showItems.value ?: emptyList()
                        val clicked = item
                        val singleList = arrayListOf(
                            com.luck.picture.lib.entity.LocalMedia().apply {
                                path = clicked.path
                                mimeType = if (clicked.type == com.fuusy.hiddendanger.ui.album.AlbumMediaItem.MediaType.VIDEO) "video/mp4" else "image/jpeg"
                                duration = clicked.duration
                            }
                        )
                        com.luck.picture.lib.basic.PictureSelector.create(this)
                            .openPreview()
                            .setImageEngine(com.fuusy.hiddendanger.util.GlideEngine())
                            .startActivityPreview(
                                0,
                                false,
                                singleList
                            )
                    }
                } else {
                    viewModel.toggleSelect(item, this)
                    viewModel.setTabUserOperated(viewModel.tab.value ?: "ALL")
                }
                },
                pathToIdMapping = pathToIdMapping
            )
        mBinding.recyclerView.layoutManager = GridLayoutManager(this, 4)
        val spacingInPixels = resources.getDimensionPixelSize(R.dimen.grid_spacing)
        mBinding.recyclerView.addItemDecoration(GridSpacingItemDecoration(4, spacingInPixels, true))
        mBinding.recyclerView.adapter = customAlbumAdapter
        (mBinding.recyclerView.itemAnimator as? androidx.recyclerview.widget.SimpleItemAnimator)?.apply {
            supportsChangeAnimations = false
        }

        // 1. 首次加载时显示 loading
        if (allPermissionsGranted()) {
            showLoading()
            viewModel.loadMedia(application)
        } else {
            requestPermissions()
        }
        // 2. 监听数据变化，决定展示内容还是 loading
        viewModel.showItems.observe(this) { items ->
            if (viewModel.allItems.value.isNullOrEmpty()) {
                showLoading()
            } else {
                dismissLoading()
                customAlbumAdapter.submitList(items)
                // 数据更新后，主动同步选中状态到适配器
                val currentSelected = viewModel.selected.value ?: emptySet()
                android.util.Log.d("CustomAlbumActivity", "=== 数据更新后选中状态同步 ===")
                android.util.Log.d("CustomAlbumActivity", "当前选中IDs: ${currentSelected.joinToString()}")
                android.util.Log.d("CustomAlbumActivity", "当前显示项目数量: ${items.size}")
                android.util.Log.d("CustomAlbumActivity", "显示项目IDs: ${items.take(5).map { it.id }.joinToString()}")
                android.util.Log.d("CustomAlbumActivity", "=== 选中状态同步结束 ===")
                customAlbumAdapter.updateSelected(currentSelected)
                val tab = viewModel.tab.value ?: "ALL"
                // 只有用户操作过才恢复上次位置，否则强制滚到顶部
                val position = if (viewModel.isTabUserOperated(tab)) {
                    viewModel.getTabScrollPosition(tab)
                } else {
                    0
                }
                mBinding.recyclerView.post {
                    (mBinding.recyclerView.layoutManager as? GridLayoutManager)?.scrollToPosition(
                        position
                    )
                }
            }
        }
        btnList = arrayOf(
            mBinding.btnWhole, mBinding.btnAI, mBinding.btnAlbum
        )
        btnList = arrayOf(
            mBinding.btnWhole, mBinding.btnAI, mBinding.btnAlbum
        )
        updateChannelUI(0)
        viewModel.selected.observe(this) {
            updateSubmitButtonText()
            customAlbumAdapter.updateSelected(it?.toSet() ?: emptySet())
        }
        mBinding.btnWhole.setOnClickListener {
            if (viewModel.allItems.value.isNullOrEmpty()) {
                showLoading()
            } else {
                dismissLoading()
            }
            val alreadyOnAll = (viewModel.tab.value == "ALL")
            viewModel.tab.value = "ALL"
            viewModel.setTabUserOperated("ALL")
            viewModel.saveTabScrollPosition("ALL", 0)
            // 若已在"全部"，延迟0.5秒后重新加载，确保刷新且避免频繁刷新
            if (alreadyOnAll) {
                GlobalScope.launch(Dispatchers.Main) {
                    delay(500) // 0.5秒延迟
                    
                    // 强制刷新MediaStore，确保新拍摄的视频被扫描
                    try {
                        Log.d("CustomAlbumActivity", "全部按钮：开始强制刷新MediaStore")
                        val intent = android.content.Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE")
                        intent.data = android.net.Uri.parse("file:///storage/emulated/0/Movies/Camera/")
                        sendBroadcast(intent)
                        Log.d("CustomAlbumActivity", "全部按钮：发送MediaStore扫描广播")
                        
                        // 再等待0.5秒，确保MediaStore扫描完成
                        delay(500)
                    } catch (e: Exception) {
                        Log.e("CustomAlbumActivity", "全部按钮：MediaStore扫描失败", e)
                    }
                    
                    viewModel.loadMedia(application, useCache = false)
                }
            } else {
                viewModel.updateShowItems("ALL")
            }
            updateChannelUI(0)
            // 点击后滚到顶部，避免误以为未刷新
            mBinding.recyclerView.post {
                (mBinding.recyclerView.layoutManager as? GridLayoutManager)?.scrollToPosition(0)
            }
        }
        mBinding.btnAI.setOnClickListener {
            if (viewModel.allItems.value.isNullOrEmpty()) {
                showLoading()
            } else {
                dismissLoading()
            }
            viewModel.tab.value = "AI"
            viewModel.updateShowItems("AI")
            updateChannelUI(1)
        }
        mBinding.btnAlbum.setOnClickListener {
            if (viewModel.allItems.value.isNullOrEmpty()) {
                showLoading()
            } else {
                dismissLoading()
            }
            viewModel.tab.value = "ALBUM"
            viewModel.updateShowItems("ALBUM")
            updateChannelUI(2)
        }

        // 4. 权限
        // 5. 提交按钮
        mBinding.btnSubmit.setOnClickListener {
            val result = intent
            val allItems = viewModel.allItems.value ?: emptyList()
            val selected = viewModel.selected.value ?: emptySet()
            // 新增：构建完整选中状态map
            val selectStateMap = HashMap<String, Boolean>()
            allItems.forEach { item ->
                selectStateMap[item.id] = selected.contains(item.id)
            }
            result.putExtra("select_state_map", selectStateMap)
            // 兼容老逻辑，依然传递选中的item数组
            result.putExtra("selected", allItems.filter { selected.contains(it.id) }.toTypedArray())
            setResult(Activity.RESULT_OK, result)
            finish()
        }
        mBinding.btnBack.setOnClickListener { finish() }
        
        // 添加刷新按钮（可选）
        // mBinding.btnRefresh.setOnClickListener {
        //     viewModel.loadMedia(application, useCache = false)
        // }

        // 隐藏提交按钮和多选UI（view模式）
        if (mode == "view") {
            mBinding.btnSubmit.visibility = android.view.View.GONE
        } else {
            mBinding.btnSubmit.visibility = android.view.View.VISIBLE
        }

        // RecyclerView滑动监听，只有用户滑动后才保存位置
        mBinding.recyclerView.addOnScrollListener(object :
            androidx.recyclerview.widget.RecyclerView.OnScrollListener() {
            override fun onScrolled(
                recyclerView: androidx.recyclerview.widget.RecyclerView, dx: Int, dy: Int
            ) {
                val layoutManager = recyclerView.layoutManager as? GridLayoutManager ?: return
                val firstVisible = layoutManager.findFirstVisibleItemPosition()
                // 只有用户滑动后才保存
                if (dy != 0) {
                    val tab = viewModel.tab.value ?: "ALL"
                    viewModel.saveTabScrollPosition(tab, firstVisible)
                    viewModel.setTabUserOperated(tab)
                }
            }
        })

        // 监听刷新相册事件
        // LiveEventBus.get("refresh_album", Boolean::class.java).observe(this) { needRefresh ->
        //     if (needRefresh == true) {
        //         Log.d("CustomAlbumActivity", "收到刷新相册事件，重新加载数据")
        //         viewModel.loadMedia(application, useCache = false)
        //     }
        // }
    }

    private fun openVideoPlayerFragment(path: String, useVlc: Boolean) {
        val containerId = com.fuusy.hiddendanger.R.id.videoPlayerContainer
        
        // 设置容器点击事件拦截，防止事件穿透
        mBinding.videoPlayerContainer.setOnClickListener {
            // 空实现，仅用于拦截点击事件
        }
        
        // 显示容器
        mBinding.videoPlayerContainer.visibility = android.view.View.VISIBLE
        
        // 置入Fragment
        val frag = VideoPlayerFragment.newInstance(path, useVlc)
        supportFragmentManager
            .beginTransaction()
            .setReorderingAllowed(true)
            .replace(containerId, frag)
            .addToBackStack("video_player")
            .commit()
            
        // 监听回退以隐藏容器
        supportFragmentManager.addOnBackStackChangedListener {
            val visible = supportFragmentManager.findFragmentById(containerId) != null
            if (!visible) {
                mBinding.videoPlayerContainer.visibility = android.view.View.GONE
                // 清除点击事件监听器
                mBinding.videoPlayerContainer.setOnClickListener(null)
            }
        }
    }

    override fun onBackPressed() {
        val fm = supportFragmentManager
        if (fm.backStackEntryCount > 0) {
            fm.popBackStack()
            return
        }
        super.onBackPressed()
    }

    override fun onStop() {
        super.onStop()
        // 防御性：清空可能遗留的叠层
        try {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        } catch (_: Exception) {}
        mBinding.videoPlayerContainer.visibility = android.view.View.GONE
        (mBinding.videoPlayerContainer as? android.view.ViewGroup)?.removeAllViews()
    }

    override fun onResume() {
        super.onResume()
        // 页面可见时延迟0.5秒后自动刷新相册数据，避免频繁刷新
        if (allPermissionsGranted()) {
            Log.d("CustomAlbumActivity", "onResume: 延迟0.5秒后自动刷新相册数据")
            GlobalScope.launch(Dispatchers.Main) {
                delay(500) // 0.5秒延迟，等待MediaStore扫描完成
                
                // 强制刷新MediaStore，确保新拍摄的视频被扫描
                try {
                    Log.d("CustomAlbumActivity", "开始强制刷新MediaStore")
                    val intent = android.content.Intent("android.intent.action.MEDIA_SCANNER_SCAN_FILE")
                    intent.data = android.net.Uri.parse("file:///storage/emulated/0/Movies/Camera/")
                    sendBroadcast(intent)
                    Log.d("CustomAlbumActivity", "发送MediaStore扫描广播")
                    
                    // 再等待0.5秒，确保MediaStore扫描完成
                    delay(500)
                } catch (e: Exception) {
                    Log.e("CustomAlbumActivity", "MediaStore扫描失败", e)
                }
                
                // 然后加载媒体数据
                viewModel.loadMedia(application, useCache = false)
            }
            // 启动定时刷新（已取消，避免视频抖动）
            // startPeriodicRefresh()
        }
    }

    override fun onPause() {
        super.onPause()
        // 页面不可见时停止定时刷新
        stopPeriodicRefresh()
    }

    /**
     * 启动定时刷新
     */
    private fun startPeriodicRefresh() {
        // 已禁用：避免视频播放/预览抖动
        stopPeriodicRefresh()
        // 不再启动任何循环任务
        // refreshJob = GlobalScope.launch(Dispatchers.Main) { ... }
    }

    /**
     * 停止定时刷新
     */
    private fun stopPeriodicRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    override fun onDestroy() {
        // 停止定时刷新
        stopPeriodicRefresh()
        
        // 再次确保清理
        try {
            supportFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        } catch (_: Exception) {}
        mBinding.videoPlayerContainer.visibility = android.view.View.GONE
        (mBinding.videoPlayerContainer as? android.view.ViewGroup)?.removeAllViews()
        super.onDestroy()
    }



    private fun updateChannelUI(selected: Int) {
        for (i in btnList.indices) {
            btnList[i].isSelected = (i == selected)
        }
    }

    private fun updateSubmitButtonText() {
        val count = viewModel.selected.value?.size ?: 0
//        mBinding.btnSubmit.text = "提交 ($count/${viewModel.MAX_SELECTION_COUNT})"
        mBinding.btnSubmit.text = "提交"
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        val granted =
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
        Log.d("CustomAlbumActivity", "Permission $it: ${if (granted) "GRANTED" else "DENIED"}")
        granted
    }

    private fun requestPermissions() {
        Log.d(
            "CustomAlbumActivity", "Requesting permissions: ${REQUIRED_PERMISSIONS.joinToString()}"
        )
        ActivityCompat.requestPermissions(
            this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            Log.d("CustomAlbumActivity", "Permission result received")
            permissions.forEachIndexed { index, permission ->
                val result =
                    if (grantResults[index] == PackageManager.PERMISSION_GRANTED) "GRANTED" else "DENIED"
                Log.d("CustomAlbumActivity", "Permission $permission: $result")
            }
            if (allPermissionsGranted()) {
                Log.d("CustomAlbumActivity", "All permissions granted, loading media")
                viewModel.loadMedia(application) // 授权后立即刷新数据
            } else {
                Log.d("CustomAlbumActivity", "Some permissions denied, showing toast and finishing")
                ToastUtil.showCustomToast(this, "需要媒体权限才能查看相册")
                finish()
            }
        }
    }

} 