package com.fuusy.project.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fuusy.project.R
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import android.widget.TextView
import android.widget.ImageView

class ProjectInfoBottomSheetDialogFragment : BottomSheetDialogFragment() {

    var projectName: String? = null
    var projectCode: String? = null
    var projectCompany: String? = null
    var projectLeader: String? = null
    var projectLocation: String? = null
    var projectContent: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_project_info, container, false)
        view.findViewById<ImageView>(R.id.ivClose).setOnClickListener { dismiss() }

        view.findViewById<TextView>(R.id.tvProjectName).text = projectName
        view.findViewById<TextView>(R.id.tvProjectCode).text = projectCode
        view.findViewById<TextView>(R.id.tvProjectCompany).text = projectCompany
        view.findViewById<TextView>(R.id.tvProjectLeader).text = projectLeader
        view.findViewById<TextView>(R.id.tvProjectLocation).text = projectLocation
        view.findViewById<TextView>(R.id.tvProjectContent).text = projectContent

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.let { dlg ->
            val bottomSheet = dlg.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                it.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                it.background = null // 去掉系统自带的圆角背景
            }
        }
    }
} 