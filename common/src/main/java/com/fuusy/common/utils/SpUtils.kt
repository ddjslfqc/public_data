package com.fuusy.common.utils

import android.content.Context
import com.tencent.mmkv.MMKV

/*
 *
 * 自定义的key-value 轻量数据存储管理类，便于替换
 */
object SpUtils {

    private var kv: MMKV? = null

    /**
     * 初始化 MMKV
     */
    fun init(context: Context) {
        if (kv == null) {
            // 使用应用内部文件目录
            val rootDir = context.filesDir.absolutePath
            MMKV.initialize(rootDir)
            kv = MMKV.defaultMMKV()
        }
    }

    /**
     * 获取 MMKV 实例
     */
    private fun getKv(): MMKV {
        if (kv == null) {
            throw IllegalStateException("SpUtils not initialized. Call SpUtils.init(context) first.")
        }
        return kv!!
    }

    fun put(key: String, value: Any?) {
        when (value) {
            is Boolean -> getKv().putBoolean(key, value)
            is ByteArray -> getKv().putBytes(key, value)
            is Float -> getKv().putFloat(key, value)
            is Int -> getKv().putInt(key, value)
            is Long -> getKv().putLong(key, value)
            is String -> getKv().putString(key, value)
            else -> error("${value?.javaClass?.simpleName} Not Supported By SpUtils")
        }
    }

    fun getBoolean(key: String, defValue: Boolean = false) = getKv().getBoolean(key, defValue)

    fun getBytes(key: String, defValue: ByteArray? = null) = getKv().getBytes(key, defValue)

    fun getFloat(key: String, defValue: Float = 0f) = getKv().getFloat(key, defValue)

    fun getInt(key: String, defValue: Int = 0) = getKv().getInt(key, defValue)

    fun getLong(key: String, defValue: Long = 0L) = getKv().getLong(key, defValue)

    fun getString(key: String, defValue: String? = null) = getKv().getString(key, defValue)

    fun remove(key: String) = getKv().remove(key)

    fun removeValue(key: String) = getKv().removeValueForKey(key)

    /**
     * 清除所有数据
     */
    fun clearAll() = getKv().clearAll()


    /**
     * 检查是否包含某个键
     */
    fun containsKey(key: String): Boolean = getKv().containsKey(key)

}