package com.fuusy.common.network

import com.fuusy.common.utils.IpConfigUtils

/**
 * 服务器配置管理类
 * 用于全局管理不同环境的服务器地址
 */
object ServerConfig {

    /**
     * 环境类型枚举
     */
    enum class Environment {
        LOCAL,      // 本地开发环境
        REMOTE,      // 远程服务器环境
        WORK_REMOTE,     // 工单服务器环境
        YUN_REMOTE      // 云台服务器环境
    }

    /**
     * 当前环境配置
     * 修改这里可以一键切换环境
     */
    private val currentEnvironment = Environment.REMOTE

    private val currentWorkOrderEnvironment = Environment.WORK_REMOTE

    private val currentYunEnvironment = Environment.YUN_REMOTE

    /**
     * 是否使用mock数据
     */
    val isMockData = false

    fun resolveStreamUrl(url: String?): String? =
        url?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * 服务器地址配置
     */
    private val serverUrls = mapOf(
        Environment.LOCAL to "http://127.0.0.1:8085/",
        Environment.REMOTE to "http://8.130.120.35:9220/",
        Environment.WORK_REMOTE to "http://8.130.120.35:9220/",
        Environment.YUN_REMOTE to "http://8.130.120.35:9220"
    )

    /**
     * 获取当前环境的服务器地址
     * 优先使用自定义配置的IP地址
     */
    fun getBaseUrl(): String {
        return if (IpConfigUtils.hasCustomConfig()) {
            IpConfigUtils.getMainServerUrl()
        } else {
            serverUrls[currentEnvironment] ?: serverUrls[Environment.LOCAL]!!
        }
    }

    /**
     * 获取工单服务器地址
     * 优先使用自定义配置的IP地址
     */
    fun getWorkOrderBaseUrl(): String {
        return if (IpConfigUtils.hasCustomConfig()) {
            IpConfigUtils.getWorkOrderServerUrl()
        } else {
            serverUrls[currentWorkOrderEnvironment] ?: serverUrls[Environment.LOCAL]!!
        }
    }

    /**
     * 获取云台服务器地址
     * 优先使用自定义配置的IP地址
     */
    fun getYunBaseUrl(): String {
        return if (IpConfigUtils.hasCustomConfig()) {
            IpConfigUtils.getYunServerUrl()
        } else {
            serverUrls[currentYunEnvironment] ?: serverUrls[Environment.LOCAL]!!
        }
    }

    /**
     * 获取当前环境类型
     */
    fun getCurrentEnvironment(): Environment {
        return currentEnvironment
    }

    /**
     * 判断是否为本地环境
     */
    fun isLocalEnvironment(): Boolean {
        return currentEnvironment == Environment.LOCAL
    }

    /**
     * 清除自定义配置，恢复到默认配置
     */
    fun clearCustomConfig() {
        // 这里可以添加清除自定义配置的逻辑
        // 目前通过IpConfigUtils.hasCustomConfig()来判断是否使用自定义配置
    }
}