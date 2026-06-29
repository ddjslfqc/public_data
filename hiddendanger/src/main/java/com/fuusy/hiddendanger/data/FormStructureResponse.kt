package com.fuusy.hiddendanger.data

import com.fuusy.hiddendanger.ui.adapter.DynamicFormAdapter

// 表单结构接口响应数据类

data class FormStructureResponse(
    val code: Int,
    val data: FormData?
)

data class FormData(
    val formItems: List<DynamicFormAdapter.FormItem>?
) 