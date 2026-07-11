package com.fuusy.common.utils

/**
 * IP配置工具类
 * 用于获取保存的IP配置
 */
object IpConfigUtils {

    private const val KEY_USE_LOCAL = "use_local_server"
    private const val KEY_LOCAL_IP = "local_server_ip"
    private const val KEY_LOCAL_PORT = "local_server_port"

    /** 是否使用本地开发服务器 */
    fun isUseLocalServer(): Boolean = SpUtils.getBoolean(KEY_USE_LOCAL, false)

    fun setUseLocalServer(useLocal: Boolean) {
        SpUtils.put(KEY_USE_LOCAL, useLocal)
    }

    fun getLocalServerIp(): String = SpUtils.getString(KEY_LOCAL_IP).orEmpty()

    fun getLocalServerPort(): String = SpUtils.getString(KEY_LOCAL_PORT) ?: "9220"

    fun getLocalServerUrl(): String =
        "http://${getLocalServerIp()}:${getLocalServerPort()}/"

    fun saveLocalServer(ip: String, port: String) {
        SpUtils.put(KEY_LOCAL_IP, ip)
        SpUtils.put(KEY_LOCAL_PORT, port)
        setUseLocalServer(true)
    }

    /**
     * 获取主服务器IP
     */
    fun getMainServerIp(): String {
        return SpUtils.getString("custom_ip_1") ?: "10.237.25.119"
    }

    /**
     * 获取主服务器端口
     */
    fun getMainServerPort(): String {
        return SpUtils.getString("custom_port_1") ?: "9250"
    }

    /**
     * 获取主服务器完整地址
     */
    fun getMainServerUrl(): String {
        return "http://${getMainServerIp()}:${getMainServerPort()}/"
    }

    /**
     * 获取工单服务器IP
     */
    fun getWorkOrderServerIp(): String {
        return SpUtils.getString("custom_ip_2") ?: "47.110.156.186"
    }

    /**
     * 获取工单服务器端口
     */
    fun getWorkOrderServerPort(): String {
        return SpUtils.getString("custom_port_2") ?: "9220"
    }

    /**
     * 获取工单服务器完整地址
     */
    fun getWorkOrderServerUrl(): String {
        return "http://${getWorkOrderServerIp()}:${getWorkOrderServerPort()}/"
    }

    /**
     * 获取云服务器IP
     */
    fun getYunServerIp(): String {
        return SpUtils.getString("custom_ip_3") ?: "10.237.25.119"
    }

    /**
     * 获取云服务器端口
     */
    fun getYunServerPort(): String {
        return SpUtils.getString("custom_port_3") ?: "8055"
    }

    /**
     * 获取云服务器完整地址
     */
    fun getYunServerUrl(): String {
        return "http://${getYunServerIp()}:${getYunServerPort()}/"
    }

    /**
     * 检查是否使用了自定义远程配置（三组 IP 都填了）
     */
    fun hasCustomConfig(): Boolean {
        if (isUseLocalServer()) return false
        val ip1 = SpUtils.getString("custom_ip_1")
        val port1 = SpUtils.getString("custom_port_1")
        val ip2 = SpUtils.getString("custom_ip_2")
        val port2 = SpUtils.getString("custom_port_2")
        val ip3 = SpUtils.getString("custom_ip_3")
        val port3 = SpUtils.getString("custom_port_3")

        return !ip1.isNullOrEmpty() && !port1.isNullOrEmpty() &&
                !ip2.isNullOrEmpty() && !port2.isNullOrEmpty() &&
                !ip3.isNullOrEmpty() && !port3.isNullOrEmpty()
    }
}
