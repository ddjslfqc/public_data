package com.fuusy.project.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.core.content.ContextCompat
import com.fuusy.project.R

/**
 * 自定义底部 Tab 栏，替代 Material BottomNavigationView，避免 ripple / 指示器 / inset 异常。
 */
class BottomTabBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    companion object {
        /** 底部「视频」Tab 开关：false 隐藏，true 显示（布局与逻辑保留） */
        const val SHOW_VIDEO_TAB = false
    }

    private data class TabItem(
        @IdRes val id: Int,
        val icon: ImageView,
        val label: TextView,
    )

    private val tabs: List<TabItem>
    private var selectedTabId: Int = R.id.navigation_home
    private var onTabSelectedListener: ((Int) -> Unit)? = null

    private val colorSelected = ContextCompat.getColor(context, R.color.home_blue)
    private val colorNormal = ContextCompat.getColor(context, R.color.home_text_normal)

    init {
        orientation = HORIZONTAL
        LayoutInflater.from(context).inflate(R.layout.layout_bottom_tab_bar, this, true)

        tabs = listOf(
            TabItem(R.id.navigation_home, findViewById(R.id.tab_home_icon), findViewById(R.id.tab_home_label)),
            TabItem(R.id.navigation_video, findViewById(R.id.tab_video_icon), findViewById(R.id.tab_video_label)),
            TabItem(R.id.navigation_work_order, findViewById(R.id.tab_work_order_icon), findViewById(R.id.tab_work_order_label)),
            TabItem(R.id.navigation_profile, findViewById(R.id.tab_profile_icon), findViewById(R.id.tab_profile_label)),
        )

        findViewById<LinearLayout>(R.id.tab_home).setOnClickListener { onTabClicked(R.id.navigation_home) }
        findViewById<LinearLayout>(R.id.tab_video).setOnClickListener { onTabClicked(R.id.navigation_video) }
        findViewById<LinearLayout>(R.id.tab_work_order).setOnClickListener { onTabClicked(R.id.navigation_work_order) }
        findViewById<LinearLayout>(R.id.tab_profile).setOnClickListener { onTabClicked(R.id.navigation_profile) }

        // 视频 Tab 暂隐藏，保留布局与切换逻辑便于恢复
        findViewById<View>(R.id.tab_video).visibility =
            if (SHOW_VIDEO_TAB) View.VISIBLE else View.GONE

        refreshAppearance()
    }

    fun setOnTabSelectedListener(listener: ((Int) -> Unit)?) {
        onTabSelectedListener = listener
    }

    fun selectTab(@IdRes tabId: Int) {
        if (!SHOW_VIDEO_TAB && tabId == R.id.navigation_video) return
        if (selectedTabId == tabId) return
        selectedTabId = tabId
        refreshAppearance()
    }

    private fun onTabClicked(@IdRes tabId: Int) {
        if (!SHOW_VIDEO_TAB && tabId == R.id.navigation_video) return
        if (selectedTabId == tabId) return
        selectedTabId = tabId
        refreshAppearance()
        onTabSelectedListener?.invoke(tabId)
    }

    private fun refreshAppearance() {
        tabs.forEach { tab ->
            val color = if (tab.id == selectedTabId) colorSelected else colorNormal
            tab.icon.setColorFilter(color)
            tab.label.setTextColor(color)
        }
    }
}
