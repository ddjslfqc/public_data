package com.fuusy.common.network

import com.fuusy.common.support.Constants
import com.fuusy.common.utils.SpUtils

/** 供工单、OKR 等接口注入 X-User-Id 请求头 */
object UserIdProvider {

    @Volatile
    var userId: Long? = null

    fun update(id: Long?) {
        userId = id?.takeIf { it > 0 }
        if (userId != null) {
            SpUtils.put(Constants.SP_KEY_USER_ID, userId!!)
        }
    }

    /** 从 SharedPreferences 同步恢复，可在 Application.onCreate 主线程调用 */
    fun restoreFromCache() {
        val cached = SpUtils.getLong(Constants.SP_KEY_USER_ID, 0L)
        if (cached > 0) {
            userId = cached
        }
    }

    /** 优先内存，其次 SpUtils 缓存 */
    fun current(): Long? {
        userId?.takeIf { it > 0 }?.let { return it }
        val cached = SpUtils.getLong(Constants.SP_KEY_USER_ID, 0L)
        if (cached > 0) {
            userId = cached
            return cached
        }
        return null
    }
}
