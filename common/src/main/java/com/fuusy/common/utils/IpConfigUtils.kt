package com.fuusy.common.utils

/**
 * IP配置工具类
 * 用于获取保存的IP配置
 */
object IpConfigUtils {
    
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
        return SpUtils.getString("custom_ip_2") ?: "10.237.25.119"
    }
    
    /**
     * 获取工单服务器端口
     */
    fun getWorkOrderServerPort(): String {
        return SpUtils.getString("custom_port_2") ?: "8088"
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
     * 检查是否使用了自定义配置
     */
    fun hasCustomConfig(): Boolean {
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