package com.fuusy.common.utils

/**
 * 统一的Loading状态枚举
 * 供所有模块使用，避免重复声明
 */
sealed class LoadingStatus {
    object Idle : LoadingStatus()
    object Loading : LoadingStatus()
    object Success : LoadingStatus()
    data class Error(val message: String) : LoadingStatus()
} 