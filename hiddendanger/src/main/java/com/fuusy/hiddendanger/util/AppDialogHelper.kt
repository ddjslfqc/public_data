package com.fuusy.hiddendanger.util

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.fuusy.common.utils.ToastUtil
import com.fuusy.hiddendanger.R

object AppDialogHelper {

    fun showConfirm(
        context: Context,
        title: String,
        message: String? = null,
        cancelText: String = "取消",
        confirmText: String = "确认",
        onConfirm: () -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_confirm, null)
        val dialog = AlertDialog.Builder(context, R.style.CustomDialog)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.tvTitle).text = title
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        if (message.isNullOrBlank()) {
            tvMessage.visibility = View.GONE
        } else {
            tvMessage.text = message
            tvMessage.visibility = View.VISIBLE
        }
        view.findViewById<FrameLayout>(R.id.contentContainer).visibility = View.GONE

        view.findViewById<TextView>(R.id.btnCancel).apply {
            text = cancelText
            setOnClickListener { dialog.dismiss() }
        }
        view.findViewById<TextView>(R.id.btnConfirm).apply {
            text = confirmText
            setOnClickListener {
                dialog.dismiss()
                onConfirm()
            }
        }
        dialog.show()
    }

    fun showInput(
        context: Context,
        title: String,
        label: String,
        hint: String = "",
        message: String? = null,
        required: Boolean = false,
        cancelText: String = "取消",
        confirmText: String = "确认",
        onConfirm: (String) -> Unit
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_app_confirm, null)
        val dialog = AlertDialog.Builder(context, R.style.CustomDialog)
            .setView(view)
            .create()

        view.findViewById<TextView>(R.id.tvTitle).text = title
        val tvMessage = view.findViewById<TextView>(R.id.tvMessage)
        if (message.isNullOrBlank()) {
            tvMessage.visibility = View.GONE
        } else {
            tvMessage.text = message
            tvMessage.visibility = View.VISIBLE
        }

        val container = view.findViewById<FrameLayout>(R.id.contentContainer)
        container.visibility = View.VISIBLE
        val inputRoot = LayoutInflater.from(context)
            .inflate(R.layout.dialog_reject_reason, container, false)
        container.addView(inputRoot)

        inputRoot.findViewById<TextView>(R.id.tvLabel).text = label
        val editText = inputRoot.findViewById<EditText>(R.id.et_reject_reason).apply {
            this.hint = hint.ifBlank { label }
        }

        view.findViewById<TextView>(R.id.btnCancel).apply {
            text = cancelText
            setOnClickListener { dialog.dismiss() }
        }
        view.findViewById<TextView>(R.id.btnConfirm).apply {
            text = confirmText
            setOnClickListener {
                val text = editText.text?.toString()?.trim().orEmpty()
                if (required && text.isBlank()) {
                    ToastUtil.showCustomToast(context, "请填写内容")
                    return@setOnClickListener
                }
                dialog.dismiss()
                onConfirm(text)
            }
        }
        dialog.show()
    }
}
