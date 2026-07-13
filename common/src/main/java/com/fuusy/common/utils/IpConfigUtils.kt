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
        buildBaseUrl(getLocalServerIp(), getLocalServerPort())

    fun saveLocalServer(ip: String, port: String) {
        val host = normalizeHostInput(ip)
        val portTrimmed = port.trim()
        val normalizedPort = when {
            portTrimmed.isNotEmpty() -> portTrimmed
            isDomainHost(host) -> ""
            else -> "9220"
        }
        SpUtils.put(KEY_LOCAL_IP, host)
        SpUtils.put(KEY_LOCAL_PORT, normalizedPort)
        setUseLocalServer(true)
    }

    fun clearLocalServer() {
        SpUtils.removeValue(KEY_LOCAL_IP)
        SpUtils.removeValue(KEY_LOCAL_PORT)
        setUseLocalServer(false)
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
    fun getMainServerUrl(): String =
        buildBaseUrl(getMainServerIp(), getMainServerPort())

    /**
     * 获取工单服务器IP
     */
    fun getWorkOrderServerIp(): String {
        return SpUtils.getString("custom_ip_2") ?: "ios.yceil.com"
    }

    /**
     * 获取工单服务器端口（域名 HTTPS 默认留空，走 443）
     */
    fun getWorkOrderServerPort(): String {
        return SpUtils.getString("custom_port_2") ?: ""
    }

    /**
     * 获取工单服务器完整地址
     */
    fun getWorkOrderServerUrl(): String =
        buildBaseUrl(getWorkOrderServerIp(), getWorkOrderServerPort())

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
    fun getYunServerUrl(): String =
        buildBaseUrl(getYunServerIp(), getYunServerPort())

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

    /** 去掉协议、路径、端口，只保留主机名或 IP */
    fun normalizeHostInput(raw: String): String {
        var host = fixSchemeTypos(raw.trim())
        host = host.removePrefix("https://").removePrefix("http://")
        host = host.split('/', '?', '#').first()
        if (host.contains(':') && !host.startsWith('[')) {
            host = host.substringBefore(':')
        }
        return host
    }

    private fun fixSchemeTypos(value: String): String = when {
        value.startsWith("https//") -> "https://" + value.removePrefix("https//")
        value.startsWith("http//") -> "http://" + value.removePrefix("http//")
        else -> value
    }

    private fun isDomainHost(host: String): Boolean {
        val normalized = normalizeHostInput(host)
        return normalized.contains('.') &&
            !normalized.matches(Regex("""\d{1,3}(\.\d{1,3}){3}"""))
    }

    private fun buildBaseUrl(hostRaw: String, portRaw: String): String {
        var host = fixSchemeTypos(hostRaw.trim())
        val port = portRaw.trim()

        if (host.startsWith("http://") || host.startsWith("https://")) {
            return if (host.endsWith("/")) host else "$host/"
        }

        host = normalizeHostInput(host)
        if (host.isEmpty()) return "http://127.0.0.1:9220/"

        if (isDomainHost(host)) {
            return when (port) {
                "", "443", "80" -> "https://$host/"
                else -> "https://$host:$port/"
            }
        }

        return if (port.isEmpty()) "http://$host/" else "http://$host:$port/"
    }
}
