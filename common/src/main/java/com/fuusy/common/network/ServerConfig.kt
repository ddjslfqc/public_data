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
     * 当前环境配置（远程默认）
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
        Environment.LOCAL to "http://127.0.0.1:9220/",
        Environment.REMOTE to "http://8.130.120.35:9220/",
        Environment.WORK_REMOTE to "https://ios.yceil.com/",
        Environment.YUN_REMOTE to "http://8.130.120.35:9220"
    )

    /**
     * 获取 OKR 服务器地址（与业务服务器同源，含评论、进度更新记录等完整 OKR 接口）
     */
    fun getOkrBaseUrl(): String = getWorkOrderBaseUrl()

    /**
     * 千文智能助手服务地址（独立于业务 9220）
     * 与 Web order-background 默认一致；可通过 Sp 键 qianwen_base_url 覆盖
     */
    fun getQianwenBaseUrl(): String {
        val custom = IpConfigUtils.getQianwenBaseUrl()
        if (custom.isNotBlank()) return ensureTrailingSlash(custom)
        return "http://42.228.15.242:24632/"
    }

    /** FileBrowser 知识库（与 Web order-background 默认一致） */
    fun getFileBrowserBaseUrl(): String {
        val custom = IpConfigUtils.getFileBrowserBaseUrl()
        if (custom.isNotBlank()) return ensureTrailingSlash(custom)
        return "http://42.228.15.242:24634/"
    }

    /** 匿名公共分享 hash（与 Web index.html 默认一致） */
    fun getFileBrowserShareHash(): String =
        IpConfigUtils.getFileBrowserShareHash().ifBlank { "dCRyVzvSrnmXZdfuTZArkw" }

    /**
     * FileBrowser 长期 API Token（可选）。
     * 仅 Sp 配置了有效 token 时才走 /api/resources + Authorization；
     * 默认与 Web 一致：公共分享 hash，不强制 JWT。
     */
    fun getFileBrowserApiToken(): String = IpConfigUtils.getFileBrowserApiToken()


    /** FileBrowser 来源名（文档默认：本地文件） */
    fun getFileBrowserSource(): String = "本地文件"

    private fun ensureTrailingSlash(url: String): String =
        if (url.endsWith("/")) url else "$url/"

    /**
     * 获取当前环境的服务器地址
     */
    fun getBaseUrl(): String {
        if (IpConfigUtils.isUseLocalServer()) {
            return IpConfigUtils.getLocalServerUrl()
        }
        return if (IpConfigUtils.hasCustomConfig()) {
            IpConfigUtils.getMainServerUrl()
        } else {
            serverUrls[currentEnvironment] ?: serverUrls[Environment.REMOTE]!!
        }
    }

    /**
     * 获取工单服务器地址
     */
    fun getWorkOrderBaseUrl(): String {
        if (IpConfigUtils.isUseLocalServer()) {
            return IpConfigUtils.getLocalServerUrl()
        }
        return if (IpConfigUtils.hasCustomConfig()) {
            IpConfigUtils.getWorkOrderServerUrl()
        } else {
            serverUrls[currentWorkOrderEnvironment] ?: serverUrls[Environment.WORK_REMOTE]!!
        }
    }

    /**
     * 获取云台服务器地址
     */
    fun getYunBaseUrl(): String {
        if (IpConfigUtils.isUseLocalServer()) {
            return IpConfigUtils.getLocalServerUrl()
        }
        return if (IpConfigUtils.hasCustomConfig()) {
            IpConfigUtils.getYunServerUrl()
        } else {
            serverUrls[currentYunEnvironment] ?: serverUrls[Environment.YUN_REMOTE]!!
        }
    }

    /**
     * 获取当前环境类型
     */
    fun getCurrentEnvironment(): Environment {
        return if (IpConfigUtils.isUseLocalServer()) Environment.LOCAL else currentEnvironment
    }

    /**
     * 判断是否为本地环境
     */
    fun isLocalEnvironment(): Boolean = IpConfigUtils.isUseLocalServer()

    /**
     * 当前生效的服务器描述（调试用）
     */
    fun getActiveServerLabel(): String {
        return if (IpConfigUtils.isUseLocalServer()) {
            "本地 ${IpConfigUtils.getLocalServerIp()}:${IpConfigUtils.getLocalServerPort()}"
        } else if (IpConfigUtils.hasCustomConfig()) {
            "自定义远程服"
        } else {
            "远程测试服"
        }
    }
}
