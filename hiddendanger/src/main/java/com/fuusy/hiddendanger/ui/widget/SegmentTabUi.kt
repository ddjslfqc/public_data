package com.fuusy.hiddendanger.ui.widget

import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.fuusy.hiddendanger.R

object SegmentTabUi {

    fun apply(tabs: List<TextView>, selectedIndex: Int) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            tab.setBackgroundResource(
                when {
                    !selected -> android.R.color.transparent
                    tabs.size == 1 -> R.drawable.bg_ai_segment_selected
                    index == 0 -> R.drawable.bg_ai_segment_selected_left
                    index == tabs.lastIndex -> R.drawable.bg_ai_segment_selected_right
                    else -> R.drawable.bg_ai_segment_selected_center
                }
            )
            tab.setTextColor(
                if (selected) Color.parseColor("#1465EB") else Color.parseColor("#686D79")
            )
            tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    /** 二级 Tab：文字 + 底部短指示条，避免和主分段控件叠两层「胶囊」 */
    fun applyUnderline(tabs: List<TextView>, selectedIndex: Int) {
        tabs.forEachIndexed { index, tab ->
            val selected = index == selectedIndex
            tab.setBackgroundResource(
                if (selected) R.drawable.bg_ai_subtab_selected else android.R.color.transparent
            )
            tab.setTextColor(
                if (selected) Color.parseColor("#1465EB") else Color.parseColor("#8B93A7")
            )
            tab.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            tab.alpha = if (selected) 1f else 0.92f
        }
    }

    fun ensureTrackClip(track: View) {
        (track as? ViewGroup)?.let { group ->
            group.clipToPadding = false
            group.clipChildren = false
        }
        (track.parent as? ViewGroup)?.let { parent ->
            parent.clipChildren = false
            parent.clipToPadding = false
        }
    }
}
