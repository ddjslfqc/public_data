package com.fuusy.project.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.*
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.fuusy.project.R

class ProjectInfoDialogFragment : DialogFragment() {

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
        dialog?.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog?.window?.requestFeature(Window.FEATURE_NO_TITLE)

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
        dialog?.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }
} 